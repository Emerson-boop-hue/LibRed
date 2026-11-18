package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.IngresoVista;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.model.ReporteVista.Cabecera;
import com.libred.sistema.model.ReporteVista.Columna;
import com.libred.sistema.model.ReporteVista.Dato;
import com.libred.sistema.model.ReporteVista.Documento;
import com.libred.sistema.model.ReporteVista.Seccion;
import com.libred.sistema.model.ReporteVista.Tipo;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Módulo REPORTES del administrador. Arma 3 informes de SU librería para un periodo (desde - hasta): */
@Service
public class ReporteService {

    public static final String CIERRE = "cierre";
    public static final String PRESTAMOS = "prestamos";
    public static final String LECTORES = "lectores";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Columna TXT(String t) { return new Columna(t, Tipo.TEXTO); }
    private static final Columna NUM(String t) { return new Columna(t, Tipo.NUMERO); }
    private static final Columna DIN(String t) { return new Columna(t, Tipo.DINERO); }

    private final LibreriaRepository libreriaRepository;
    private final PrestamoRepository prestamoRepository;
    private final MultaRepository multaRepository;
    private final IngresoService ingresoService;
    private final LectorService lectorService;
    private final RecursoService recursoService;

    public ReporteService(LibreriaRepository libreriaRepository,
                          PrestamoRepository prestamoRepository,
                          MultaRepository multaRepository,
                          IngresoService ingresoService,
                          LectorService lectorService,
                          RecursoService recursoService) {
        this.libreriaRepository = libreriaRepository;
        this.prestamoRepository = prestamoRepository;
        this.multaRepository = multaRepository;
        this.ingresoService = ingresoService;
        this.lectorService = lectorService;
        this.recursoService = recursoService;
    }

    /** Logo de LibRed para el encabezado del PDF y del Excel (null si no se encuentra). */
    public byte[] logoPng() {
        try (InputStream in = new ClassPathResource("static/img/LogoSinFodo.png").getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Documento armar(Integer libreriaId, String tipo, String desdeTexto, String hastaTexto) {
        String tipoOk = (PRESTAMOS.equals(tipo) || LECTORES.equals(tipo)) ? tipo : CIERRE;

        LocalDate hoy = LocalDate.now();
        LocalDate hasta = fecha(hastaTexto, hoy);
        LocalDate desde = fecha(desdeTexto, hoy.withDayOfMonth(1));
        if (hasta.isAfter(hoy)) {
            hasta = hoy;
        }
        if (desde.isAfter(hasta)) {
            desde = hasta;
        }

        Libreria lib = libreriaRepository.findById(libreriaId).orElse(null);
        Cabecera cabecera = cabecera(lib);

        String titulo;
        List<Seccion> secciones;
        switch (tipoOk) {
            case PRESTAMOS -> {
                titulo = "Préstamos y artículos";
                secciones = seccionesPrestamos(libreriaId, desde, hasta, hoy);
            }
            case LECTORES -> {
                titulo = "Lectores";
                secciones = seccionesLectores(libreriaId, desde, hasta, hoy);
            }
            default -> {
                titulo = "Cierre de caja";
                secciones = seccionesCierre(libreriaId, desde, hasta, hoy);
            }
        }

        String periodo = desde.equals(hasta) ? "Día " + desde.format(FECHA)
                : "Del " + desde.format(FECHA) + " al " + hasta.format(FECHA);
        String archivo = "reporte-" + limpiar(titulo) + "_" + desde + "_" + hasta;

        return new Documento(tipoOk, titulo, desde.toString(), hasta.toString(), periodo,
                "Generado el " + LocalDateTime.now().format(FECHA_HORA), cabecera, secciones, archivo);
    }

    private List<Seccion> seccionesCierre(Integer libreriaId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        IngresoVista.Pantalla pantalla = ingresoService.armarPantalla(libreriaId);

        // Cobros del periodo, del más antiguo al más reciente (la pantalla los trae al revés)
        List<IngresoVista.Fila> cobros = new ArrayList<>();
        for (IngresoVista.Fila f : pantalla.filas()) {
            if (f.dia().compareTo(desde.toString()) >= 0 && f.dia().compareTo(hasta.toString()) <= 0) {
                cobros.add(f);
            }
        }
        Collections.reverse(cobros);

        BigDecimal servicio = BigDecimal.ZERO, mora = BigDecimal.ZERO, multas = BigDecimal.ZERO;
        Map<String, BigDecimal[]> porDia = new TreeMap<>();
        for (IngresoVista.Fila f : cobros) {
            BigDecimal[] dia = porDia.computeIfAbsent(f.dia(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            switch (f.concepto()) {
                case "SERVICIO" -> { servicio = servicio.add(f.monto()); dia[0] = dia[0].add(f.monto()); }
                case "MORA" -> { mora = mora.add(f.monto()); dia[1] = dia[1].add(f.monto()); }
                default -> { multas = multas.add(f.monto()); dia[2] = dia[2].add(f.monto()); }
            }
        }
        BigDecimal total = servicio.add(mora).add(multas);

        List<Seccion> secciones = new ArrayList<>();

        secciones.add(new Seccion("Resumen del periodo", List.of(
                new Dato("Total cobrado", d(total), Tipo.DINERO),
                new Dato("Servicio", d(servicio), Tipo.DINERO),
                new Dato("Mora", d(mora), Tipo.DINERO),
                new Dato("Multas", d(multas), Tipo.DINERO),
                new Dato("Cantidad de cobros", String.valueOf(cobros.size()), Tipo.NUMERO)
        ), null, null, false, null));

        List<List<String>> filasDia = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : porDia.entrySet()) {
            BigDecimal[] v = e.getValue();
            filasDia.add(List.of(LocalDate.parse(e.getKey()).format(FECHA), d(v[0]), d(v[1]), d(v[2]),
                    d(v[0].add(v[1]).add(v[2]))));
        }
        if (!filasDia.isEmpty()) {
            filasDia.add(List.of("Total", d(servicio), d(mora), d(multas), d(total)));
        }
        secciones.add(new Seccion("Cobrado por día",
                null, List.of(TXT("Fecha"), DIN("Servicio"), DIN("Mora"), DIN("Multas"), DIN("Total")),
                filasDia, true, "No hubo cobros en este periodo."));

        List<List<String>> filasDetalle = new ArrayList<>();
        for (IngresoVista.Fila f : cobros) {
            filasDetalle.add(List.of(f.fecha(), f.conceptoTexto(), f.lector(), f.titulo(), f.detalle(), d(f.monto())));
        }
        secciones.add(new Seccion("Detalle de cobros", null,
                List.of(TXT("Fecha"), TXT("Concepto"), TXT("Lector"), TXT("Artículo"), TXT("Detalle"), DIN("Monto")),
                filasDetalle, false, "No hubo cobros en este periodo."));

        // Lo que falta por cobrar (foto de hoy, no depende del periodo)
        List<Multa> pendientes = multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PENDIENTE");
        BigDecimal deudaMultas = BigDecimal.ZERO;
        for (Multa m : pendientes) {
            if (m.getMonto() != null) {
                deudaMultas = deudaMultas.add(m.getMonto());
            }
        }
        long vencidos = prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO").stream()
                .filter(p -> p.getFechaDevolucionLimite() != null && p.getFechaDevolucionLimite().isBefore(hoy))
                .count();
        secciones.add(new Seccion("Por cobrar (al " + hoy.format(FECHA) + ")", null,
                List.of(TXT("Concepto"), NUM("Cantidad"), DIN("Monto")),
                List.of(
                        List.of("Servicio de pedidos por entregar", String.valueOf(pantalla.pedidosPorCobrar()), d(pantalla.porCobrar())),
                        List.of("Multas pendientes de pago", String.valueOf(pendientes.size()), d(deudaMultas)),
                        List.of("Préstamos vencidos hoy, de cualquier fecha (la mora se calcula al devolver)", String.valueOf(vencidos), "—")
                ), false, null));
        return secciones;
    }

    private List<Seccion> seccionesPrestamos(Integer libreriaId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<Prestamo> periodo = new ArrayList<>();
        for (Prestamo p : prestamoRepository.findByLibreriaId(libreriaId)) {
            if (p.getFechaPrestamo() != null && enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)) {
                periodo.add(p);
            }
        }

        int aTiempo = 0, conRetraso = 0, activos = 0, vencidos = 0, porEntregar = 0, cancelados = 0;
        BigDecimal servicio = BigDecimal.ZERO;
        Map<String, int[]> porCategoria = new LinkedHashMap<>();           // [préstamos, devueltos]
        Map<String, BigDecimal> servicioCategoria = new HashMap<>();
        Map<Integer, int[]> porArticulo = new HashMap<>();
        Map<Integer, Recurso> articulos = new HashMap<>();

        for (Prestamo p : periodo) {
            String estado = p.getEstadoPrestamo() == null ? "" : p.getEstadoPrestamo();
            if ("CANCELADO".equals(estado)) {
                cancelados++;
                continue;
            }
            Recurso r = p.getRecurso();
            String categoria = r == null ? "Sin categoría" : CategoriaRecurso.desdeValor(r.getTipoRecurso()).getEtiqueta();
            int[] cat = porCategoria.computeIfAbsent(categoria, k -> new int[2]);
            cat[0]++;

            if ("POR_ENTREGAR".equals(estado)) {
                porEntregar++;
            } else if ("DEVUELTO".equals(estado)) {
                cat[1]++;
                if (tarde(p, hoy)) {
                    conRetraso++;
                } else {
                    aTiempo++;
                }
            } else if (tarde(p, hoy)) {
                vencidos++;
            } else {
                activos++;
            }

            if (p.isServicioCobrado() && p.getMontoServicio() != null) {
                servicio = servicio.add(p.getMontoServicio());
                servicioCategoria.merge(categoria, p.getMontoServicio(), BigDecimal::add);
            }
            if (r != null) {
                porArticulo.computeIfAbsent(r.getId(), k -> new int[1])[0]++;
                articulos.put(r.getId(), r);
            }
        }
        int total = aTiempo + conRetraso + activos + vencidos + porEntregar;
        int devueltos = aTiempo + conRetraso;
        String puntualidad = devueltos == 0 ? "—" : Math.round(aTiempo * 100.0 / devueltos) + " %";

        List<Seccion> secciones = new ArrayList<>();
        secciones.add(new Seccion("Resumen del periodo", List.of(
                new Dato("Préstamos del periodo", String.valueOf(total), Tipo.NUMERO),
                new Dato("Devueltos", String.valueOf(devueltos), Tipo.NUMERO),
                new Dato("Puntualidad en devoluciones", puntualidad, Tipo.TEXTO),
                new Dato("Servicio cobrado", d(servicio), Tipo.DINERO)
        ), null, null, false, null));

        secciones.add(new Seccion("Por estado", null, List.of(TXT("Estado"), NUM("Cantidad")), List.of(
                List.of("Devueltos a tiempo", String.valueOf(aTiempo)),
                List.of("Devueltos con retraso", String.valueOf(conRetraso)),
                List.of("Activos (en plazo)", String.valueOf(activos)),
                List.of("Vencidos (sin devolver)", String.valueOf(vencidos)),
                List.of("Por entregar", String.valueOf(porEntregar)),
                List.of("Cancelados", String.valueOf(cancelados))
        ), false, null));

        List<Map.Entry<String, int[]>> categorias = new ArrayList<>(porCategoria.entrySet());
        categorias.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        List<List<String>> filasCategoria = new ArrayList<>();
        for (Map.Entry<String, int[]> e : categorias) {
            filasCategoria.add(List.of(e.getKey(), String.valueOf(e.getValue()[0]), String.valueOf(e.getValue()[1]),
                    d(servicioCategoria.getOrDefault(e.getKey(), BigDecimal.ZERO))));
        }
        secciones.add(new Seccion("Por categoría", null,
                List.of(TXT("Categoría"), NUM("Préstamos"), NUM("Devueltos"), DIN("Servicio cobrado")),
                filasCategoria, false, "No hubo préstamos en este periodo."));

        List<Map.Entry<Integer, int[]>> ranking = new ArrayList<>(porArticulo.entrySet());
        ranking.sort((a, b) -> {
            int c = Integer.compare(b.getValue()[0], a.getValue()[0]);
            return c != 0 ? c : articulos.get(a.getKey()).getTitulo().compareToIgnoreCase(articulos.get(b.getKey()).getTitulo());
        });
        List<List<String>> filasTop = new ArrayList<>();
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Recurso r = articulos.get(ranking.get(i).getKey());
            filasTop.add(List.of(String.valueOf(i + 1), r.getCodigo(), r.getTitulo(),
                    CategoriaRecurso.desdeValor(r.getTipoRecurso()).getEtiqueta(), String.valueOf(ranking.get(i).getValue()[0])));
        }
        secciones.add(new Seccion("Los 10 artículos más prestados", null,
                List.of(NUM("N.º"), TXT("Código"), TXT("Artículo"), TXT("Categoría"), NUM("Préstamos")),
                filasTop, false, "No hubo préstamos en este periodo."));

        List<Recurso> sinMovimiento = new ArrayList<>();
        for (Recurso r : recursoService.listarPorLibreria(libreriaId)) {
            if (!porArticulo.containsKey(r.getId())) {
                sinMovimiento.add(r);
            }
        }
        sinMovimiento.sort(Comparator.comparing(Recurso::getTitulo, String.CASE_INSENSITIVE_ORDER));
        List<List<String>> filasSin = new ArrayList<>();
        for (int i = 0; i < Math.min(30, sinMovimiento.size()); i++) {
            Recurso r = sinMovimiento.get(i);
            filasSin.add(List.of(r.getCodigo(), r.getTitulo(), CategoriaRecurso.desdeValor(r.getTipoRecurso()).getEtiqueta(),
                    String.valueOf(r.getStockTotal() == null ? 0 : r.getStockTotal())));
        }
        String tituloSin = sinMovimiento.size() > 30
                ? "Artículos sin movimiento (30 de " + sinMovimiento.size() + ")" : "Artículos sin movimiento";
        secciones.add(new Seccion(tituloSin, null,
                List.of(TXT("Código"), TXT("Artículo"), TXT("Categoría"), NUM("Stock")),
                filasSin, false, "Todos los artículos tuvieron movimiento en este periodo."));
        return secciones;
    }

    private List<Seccion> seccionesLectores(Integer libreriaId, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<Lector> lectores = lectorService.listarPorLibreria(libreriaId, null);

        // Multas pendientes por lector
        Map<Integer, int[]> cuentaMultas = new HashMap<>();
        Map<Integer, BigDecimal> deudaLector = new HashMap<>();
        BigDecimal deudaTotal = BigDecimal.ZERO;
        for (Multa m : multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PENDIENTE")) {
            if (m.getLector() == null || m.getMonto() == null) {
                continue;
            }
            cuentaMultas.computeIfAbsent(m.getLector().getId(), k -> new int[1])[0]++;
            deudaLector.merge(m.getLector().getId(), m.getMonto(), BigDecimal::add);
            deudaTotal = deudaTotal.add(m.getMonto());
        }

        // Préstamos del periodo por lector [préstamos, con retraso]
        Map<Integer, int[]> actividad = new HashMap<>();
        for (Prestamo p : prestamoRepository.findByLibreriaId(libreriaId)) {
            if (p.getFechaPrestamo() == null || p.getLector() == null
                    || !enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)
                    || "CANCELADO".equals(p.getEstadoPrestamo())) {
                continue;
            }
            int[] a = actividad.computeIfAbsent(p.getLector().getId(), k -> new int[2]);
            a[0]++;
            if (tarde(p, hoy)) {
                a[1]++;
            }
        }

        int nuevos = 0, bloqueados = 0;
        for (Lector l : lectores) {
            if (l.getFechaRegistro() != null && enRango(l.getFechaRegistro().toLocalDate(), desde, hasta)) {
                nuevos++;
            }
            if ("BLOQUEADO".equalsIgnoreCase(l.getEstadoCuenta())) {
                bloqueados++;
            }
        }

        List<Seccion> secciones = new ArrayList<>();
        secciones.add(new Seccion("Resumen", List.of(
                new Dato("Lectores registrados", String.valueOf(lectores.size()), Tipo.NUMERO),
                new Dato("Nuevos en el periodo", String.valueOf(nuevos), Tipo.NUMERO),
                new Dato("Con préstamos en el periodo", String.valueOf(actividad.size()), Tipo.NUMERO),
                new Dato("Bloqueados", String.valueOf(bloqueados), Tipo.NUMERO),
                new Dato("Con multas pendientes", String.valueOf(cuentaMultas.size()), Tipo.NUMERO),
                new Dato("Deuda pendiente", d(deudaTotal), Tipo.DINERO)
        ), null, null, false, null));

        List<Lector> activos = new ArrayList<>();
        for (Lector l : lectores) {
            if (actividad.containsKey(l.getId())) {
                activos.add(l);
            }
        }
        activos.sort((a, b) -> {
            int c = Integer.compare(actividad.get(b.getId())[0], actividad.get(a.getId())[0]);
            return c != 0 ? c : nombre(a).compareToIgnoreCase(nombre(b));
        });
        List<List<String>> filasActivos = new ArrayList<>();
        for (int i = 0; i < Math.min(10, activos.size()); i++) {
            Lector l = activos.get(i);
            int[] a = actividad.get(l.getId());
            filasActivos.add(List.of(String.valueOf(i + 1), l.getCodigoLector(), nombre(l), String.valueOf(a[0]), String.valueOf(a[1])));
        }
        secciones.add(new Seccion("Los 10 lectores más activos del periodo", null,
                List.of(NUM("N.º"), TXT("Código"), TXT("Lector"), NUM("Préstamos"), NUM("Con retraso")),
                filasActivos, false, "Ningún lector tuvo préstamos en este periodo."));

        List<Lector> conDeuda = new ArrayList<>();
        for (Lector l : lectores) {
            if ("BLOQUEADO".equalsIgnoreCase(l.getEstadoCuenta()) || cuentaMultas.containsKey(l.getId())) {
                conDeuda.add(l);
            }
        }
        conDeuda.sort((a, b) -> deudaLector.getOrDefault(b.getId(), BigDecimal.ZERO)
                .compareTo(deudaLector.getOrDefault(a.getId(), BigDecimal.ZERO)));
        List<List<String>> filasDeuda = new ArrayList<>();
        for (Lector l : conDeuda) {
            int[] n = cuentaMultas.get(l.getId());
            filasDeuda.add(List.of(l.getCodigoLector(), nombre(l),
                    "BLOQUEADO".equalsIgnoreCase(l.getEstadoCuenta()) ? "Bloqueado" : "Habilitado",
                    String.valueOf(n == null ? 0 : n[0]), d(deudaLector.getOrDefault(l.getId(), BigDecimal.ZERO))));
        }
        secciones.add(new Seccion("Lectores bloqueados o con deuda (al " + hoy.format(FECHA) + ")", null,
                List.of(TXT("Código"), TXT("Lector"), TXT("Cuenta"), NUM("Multas pendientes"), DIN("Deuda")),
                filasDeuda, false, "No hay lectores bloqueados ni con deuda."));
        return secciones;
    }

    private Cabecera cabecera(Libreria lib) {
        if (lib == null) {
            return new Cabecera("Librería", "", "", "");
        }
        List<String> contacto = new ArrayList<>();
        if (lib.getTelefonoContacto() != null && !lib.getTelefonoContacto().isBlank()) {
            contacto.add("Tel. " + lib.getTelefonoContacto());
        }
        if (lib.getCorreoContacto() != null && !lib.getCorreoContacto().isBlank()) {
            contacto.add(lib.getCorreoContacto());
        }
        return new Cabecera(lib.getNombreComercial(),
                lib.getRuc() == null ? "" : "RUC " + lib.getRuc(),
                lib.getDireccion() == null ? "" : lib.getDireccion(),
                String.join(" · ", contacto));
    }

    /** Un préstamo cuenta "con retraso" si se devolvió después de su plazo, o si sigue activo y ya pasó el plazo. */
    private boolean tarde(Prestamo p, LocalDate hoy) {
        LocalDate limite = p.getFechaDevolucionLimite();
        if (limite == null) {
            return false;
        }
        if ("DEVUELTO".equals(p.getEstadoPrestamo())) {
            return p.getFechaDevolucionReal() != null && p.getFechaDevolucionReal().toLocalDate().isAfter(limite);
        }
        return "ACTIVO".equals(p.getEstadoPrestamo()) && limite.isBefore(hoy);
    }

    private static boolean enRango(LocalDate dia, LocalDate desde, LocalDate hasta) {
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    private static LocalDate fecha(String texto, LocalDate porDefecto) {
        try {
            return texto == null || texto.isBlank() ? porDefecto : LocalDate.parse(texto.trim());
        } catch (Exception e) {
            return porDefecto;
        }
    }

    private static String d(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nombre(Lector l) {
        return (l.getNombres() + " " + l.getApellidos()).trim();
    }

    /** "Préstamos y artículos" -> "prestamos-y-articulos" (para el nombre del archivo). */
    private static String limpiar(String texto) {
        String sin = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sin.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}