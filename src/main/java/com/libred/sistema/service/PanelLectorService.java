package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.PanelLectorVista;
import com.libred.sistema.model.PanelLectorVista.EnCurso;
import com.libred.sistema.model.PanelLectorVista.Categoria;
import com.libred.sistema.model.PanelLectorVista.Mes;
import com.libred.sistema.model.PanelLectorVista.SedeRed;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Arma todo lo que muestra la Vista General del lector: números, préstamos en curso, gráficos y su red. */
@Service
public class PanelLectorService {

    private static final List<String> EN_CURSO = List.of("ACTIVO", "VENCIDO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.of("es", "PE"));

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final PrestamoRepository prestamoRepository;
    private final MultaRepository multaRepository;
    private final PoliticaPrestamoService politicaService;
    private final CalificacionService calificacionService;

    public PanelLectorService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository,
                              PrestamoRepository prestamoRepository, MultaRepository multaRepository,
                              PoliticaPrestamoService politicaService,
                              CalificacionService calificacionService) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.prestamoRepository = prestamoRepository;
        this.multaRepository = multaRepository;
        this.politicaService = politicaService;
        this.calificacionService = calificacionService;
    }

    /** Vacío si el lector ya no existe (el controlador cierra la sesión). */
    @Transactional(readOnly = true)
    public Optional<PanelLectorVista> armar(Integer lectorId) {
        Lector lector = lectorRepository.findById(lectorId).orElse(null);
        if (lector == null) {
            return Optional.empty();
        }
        Integer libreriaId = lector.getLibreriaId();
        Libreria sede = libreriaId == null ? null : libreriaRepository.findById(libreriaId).orElse(null);
        LocalDate hoy = LocalDate.now();

        // ---------- Préstamos en curso ----------
        List<Prestamo> propios = prestamoRepository.findByLectorIdOrderByFechaPrestamoDesc(lectorId);
        List<Prestamo> enCursoBd = propios.stream()
                .filter(p -> EN_CURSO.contains(p.getEstadoPrestamo()))
                .sorted(Comparator.comparing(
                        (Prestamo p) -> p.getFechaDevolucionLimite() == null ? LocalDate.MAX : p.getFechaDevolucionLimite()))
                .toList();

        List<EnCurso> enCurso = new ArrayList<>();
        long vencidos = 0;
        String vencidoTitulo = null;      // el más atrasado (la lista va ordenada por fecha límite)
        Prestamo proximo = null;          // el de fecha límite más cercana
        for (Prestamo p : enCursoBd) {
            LocalDate limite = p.getFechaDevolucionLimite();
            long faltan = limite == null ? 999 : ChronoUnit.DAYS.between(hoy, limite);
            String nivel = faltan < 0 ? "vencido" : (faltan <= 1 ? "pronto" : "ok");
            if (proximo == null) {
                proximo = p;
            }
            if (faltan < 0) {
                vencidos++;
                if (vencidoTitulo == null) {
                    vencidoTitulo = p.getRecurso().getTitulo();
                }
            }
            Recurso r = p.getRecurso();
            CategoriaRecurso cat = CategoriaRecurso.desdeValor(r.getTipoRecurso());
            enCurso.add(new EnCurso(r.getTitulo(), cat.getEtiqueta(), cat.getIcono(),
                    limite == null ? "—" : limite.format(FECHA), textoRestante(faltan), nivel, porcentaje(p, hoy)));
        }

        // Próxima devolución
        String proximaNivel = "ninguna", proximaValor = "—", proximaDetalle = "No tienes préstamos en curso";
        if (proximo != null && proximo.getFechaDevolucionLimite() != null) {
            long faltan = ChronoUnit.DAYS.between(hoy, proximo.getFechaDevolucionLimite());
            proximaNivel = faltan < 0 ? "vencido" : (faltan <= 1 ? "pronto" : "ok");
            proximaValor = faltan < 0 ? "Vencido" : faltan == 0 ? "Hoy" : faltan == 1 ? "Mañana"
                    : faltan + " días";
            proximaDetalle = proximo.getRecurso().getTitulo() + " · "
                    + proximo.getFechaDevolucionLimite().format(FECHA);
        }

        // ---------- Multas ----------
        long multasPend = 0;
        BigDecimal montoPend = BigDecimal.ZERO;
        for (Multa m : multaRepository.buscarPorLector(lectorId)) {
            if (!"PAGADO".equalsIgnoreCase(m.getEstadoPago())) {
                multasPend++;
                montoPend = montoPend.add(m.getMonto() == null ? BigDecimal.ZERO : m.getMonto());
            }
        }
        boolean bloqueado = "BLOQUEADO".equalsIgnoreCase(lector.getEstadoCuenta()) || multasPend > 0;

        // ---------- Calificaciones ----------
        var calif = calificacionService.resumen(lectorId);

        // ---------- Gráficos: préstamos por mes (últimos 6) y puntualidad ----------
        YearMonth mesActual = YearMonth.from(hoy);
        long[] porMes = new long[6];
        long aTiempo = 0, devueltosConFecha = 0;
        java.util.Map<String, Long> porCategoria = new java.util.LinkedHashMap<>();
        for (Prestamo p : propios) {
            if (p.getFechaPrestamo() != null) {
                long atras = ChronoUnit.MONTHS.between(YearMonth.from(p.getFechaPrestamo()), mesActual);
                if (atras >= 0 && atras < 6) {
                    porMes[5 - (int) atras]++;
                }
            }
            porCategoria.merge(CategoriaRecurso.desdeValor(p.getRecurso().getTipoRecurso()).getValor(), 1L, Long::sum);
            if ("DEVUELTO".equalsIgnoreCase(p.getEstadoPrestamo())
                    && p.getFechaDevolucionReal() != null && p.getFechaDevolucionLimite() != null) {
                devueltosConFecha++;
                if (!p.getFechaDevolucionReal().toLocalDate().isAfter(p.getFechaDevolucionLimite())) {
                    aTiempo++;
                }
            }
        }
        long maximo = Math.max(1, java.util.Arrays.stream(porMes).max().orElse(1));
        List<Mes> meses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = mesActual.minusMonths(5 - i);
            String etiqueta = ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.of("es", "PE"))
                    .replace(".", "");
            int alto = porMes[i] == 0 ? 0 : (int) Math.max(8, Math.round(porMes[i] * 100.0 / maximo));
            meses.add(new Mes(etiqueta, porMes[i], alto));
        }
        long maxCat = Math.max(1, porCategoria.values().stream().mapToLong(Long::longValue).max().orElse(1));
        List<Categoria> categorias = porCategoria.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    CategoriaRecurso c = CategoriaRecurso.desdeValor(e.getKey());
                    return new Categoria(c.getEtiqueta(), c.getIcono(), e.getValue(),
                            (int) Math.max(8, Math.round(e.getValue() * 100.0 / maxCat)));
                })
                .toList();
        int puntualidad = devueltosConFecha == 0 ? -1 : (int) Math.round(aTiempo * 100.0 / devueltosConFecha);

        // ---------- Otras librerías donde ya tiene cuenta ----------
        List<SedeRed> otras = new ArrayList<>();
        for (Lector l : lectorRepository.findAllByCodigoLector(lector.getCodigoLector())) {
            if (l.getLibreriaId() == null || l.getLibreriaId().equals(libreriaId)) {
                continue;
            }
            libreriaRepository.findById(l.getLibreriaId()).ifPresent(lib ->
                    otras.add(new SedeRed(lib.getId(), lib.getNombreComercial(),
                            iniciales(lib.getNombreComercial()), lib.getId() % 6)));
        }
        otras.sort(Comparator.comparing(SedeRed::getNombre, String.CASE_INSENSITIVE_ORDER));
        int otrasMas = Math.max(0, otras.size() - 3);
        List<SedeRed> otrasVisibles = new ArrayList<>(otras.subList(0, Math.min(3, otras.size())));

        String primerNombre = lector.getNombres() == null ? "" : lector.getNombres().trim().split("\\s+")[0];
        String fecha = hoy.format(FECHA_LARGA);
        fecha = fecha.substring(0, 1).toUpperCase() + fecha.substring(1);

        return Optional.of(new PanelLectorVista(
                saludo(), primerNombre, fecha, bloqueado ? "BLOQUEADO" : "HABILITADO",
                enCursoBd.size(), sede == null ? null : politicaService.obtenerMaxActivos(libreriaId),
                proximaNivel, proximaValor, proximaDetalle, multasPend, montoPend,
                calif.devueltos(), calif.calificadas(), calif.promedio(),
                vencidos, vencidoTitulo, calif.pendientes(), enCurso, meses, categorias, propios.size(), puntualidad,
                sede == null ? "Sin librería" : sede.getNombreComercial(),
                sede == null ? "L" : iniciales(sede.getNombreComercial()),
                sede == null || sede.getHorarioAtencion() == null ? "" : sede.getHorarioAtencion(),
                otrasVisibles, otrasMas));
    }

    private String saludo() {
        int h = LocalDateTime.now().getHour();
        return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
    }

    private String textoRestante(long faltan) {
        if (faltan >= 999) return "Sin fecha límite";
        if (faltan < 0) return "Venció hace " + (-faltan) + (faltan == -1 ? " día" : " días");
        if (faltan == 0) return "Vence hoy";
        if (faltan == 1) return "Vence mañana";
        return "Vence en " + faltan + " días";
    }

    /** Qué parte del plazo ya pasó (0-100). Un préstamo vencido siempre marca 100. */
    private int porcentaje(Prestamo p, LocalDate hoy) {
        LocalDate limite = p.getFechaDevolucionLimite();
        if (limite == null || p.getFechaPrestamo() == null) {
            return 0;
        }
        long total = Math.max(1, ChronoUnit.DAYS.between(p.getFechaPrestamo().toLocalDate(), limite));
        long pasados = ChronoUnit.DAYS.between(p.getFechaPrestamo().toLocalDate(), hoy);
        return (int) Math.max(0, Math.min(100, Math.round(pasados * 100.0 / total)));
    }

    private String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "L";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : nombre.trim().split("\\s+")) {
            if (!p.isEmpty() && Character.isLetter(p.charAt(0))) {
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
            if (sb.length() == 2) {
                break;
            }
        }
        return sb.length() == 0 ? "L" : sb.toString();
    }
}