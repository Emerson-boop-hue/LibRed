package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.PanelLectorVista;
import com.libred.sistema.model.PanelLectorVista.Alerta;
import com.libred.sistema.model.PanelLectorVista.EnCurso;
import com.libred.sistema.model.PanelLectorVista.Novedad;
import com.libred.sistema.model.PanelLectorVista.SedeRed;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Arma todo lo que muestra la Vista General del lector: alertas, números, préstamos en curso, novedades y su red. */
@Service
public class PanelLectorService {

    private static final List<String> EN_CURSO = List.of("ACTIVO", "VENCIDO");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.of("es", "PE"));
    private static final int MAX_ALERTAS = 3;
    private static final int MAX_NOVEDADES = 8;

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final PrestamoRepository prestamoRepository;
    private final MultaRepository multaRepository;
    private final RecursoRepository recursoRepository;
    private final PoliticaPrestamoService politicaService;
    private final CalificacionService calificacionService;

    public PanelLectorService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository,
                              PrestamoRepository prestamoRepository, MultaRepository multaRepository,
                              RecursoRepository recursoRepository, PoliticaPrestamoService politicaService,
                              CalificacionService calificacionService) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.prestamoRepository = prestamoRepository;
        this.multaRepository = multaRepository;
        this.recursoRepository = recursoRepository;
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
        long porVencer = 0;               // vencen hoy o mañana
        Prestamo proximo = null;          // el de fecha límite más cercana
        Prestamo unicoPorVencer = null;
        for (Prestamo p : enCursoBd) {
            LocalDate limite = p.getFechaDevolucionLimite();
            long faltan = limite == null ? 999 : ChronoUnit.DAYS.between(hoy, limite);
            String nivel = faltan < 0 ? "vencido" : (faltan <= 1 ? "pronto" : "ok");
            if (proximo == null) {
                proximo = p;
            }
            if (faltan < 0) {
                vencidos++;
            } else if (faltan <= 1) {
                porVencer++;
                unicoPorVencer = p;
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
        boolean sancionado = "SANCIONADO".equalsIgnoreCase(lector.getEstadoCuenta()) || multasPend > 0;

        // ---------- Calificaciones ----------
        var calif = calificacionService.resumen(lectorId);

        // ---------- Alertas (por prioridad) ----------
        List<Alerta> alertas = new ArrayList<>();
        if (vencidos > 0) {
            alertas.add(new Alerta("peligro", "fa-triangle-exclamation",
                    "Tienes " + vencidos + (vencidos == 1 ? " préstamo vencido" : " préstamos vencidos"),
                    "Devuélvelo en la librería para poder solicitar otro artículo.",
                    "/lector/prestamos", "Ver préstamos"));
        }
        if (sancionado) {
            alertas.add(new Alerta("peligro", "fa-ban",
                    "Tu cuenta está sancionada en esta librería",
                    multasPend > 0
                            ? "Tienes " + multasPend + (multasPend == 1 ? " multa pendiente" : " multas pendientes")
                              + " por S/ " + montoPend.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                              + ". Regularízalas en la librería para volver a pedir préstamos."
                            : "Acércate a la librería para regularizar tu cuenta.",
                    "/lector/historial", "Ver mis multas"));
        }
        if (porVencer > 0) {
            String cuando = unicoPorVencer != null && unicoPorVencer.getFechaDevolucionLimite().isEqual(hoy) ? "hoy" : "mañana";
            alertas.add(new Alerta("aviso", "fa-clock",
                    porVencer == 1
                            ? "Devuelve «" + unicoPorVencer.getRecurso().getTitulo() + "» " + cuando
                            : "Tienes " + porVencer + " devoluciones para hoy o mañana",
                    "Entrégalo a tiempo para evitar multas.",
                    "/lector/prestamos", "Ver préstamos"));
        }
        if (calif.pendientes() > 0) {
            alertas.add(new Alerta("calificar", "fa-star",
                    "Tienes " + calif.pendientes() + (calif.pendientes() == 1 ? " préstamo por calificar" : " préstamos por calificar"),
                    "Cuéntanos cómo te fue: tu opinión ayuda a otros lectores de la red.",
                    "/lector/historial", "Calificar ahora"));
        }
        if (alertas.isEmpty()) {
            if (calif.calificadas() > 0) {
                alertas.add(new Alerta("ok", "fa-face-smile-beam", "¡Estás al día! 😊 No tienes nada pendiente",
                        "Has calificado " + calif.calificadas() + (calif.calificadas() == 1 ? " préstamo" : " préstamos")
                                + " · Tu promedio: " + calif.promedio() + " ★",
                        "/lector/historial", "Ver mis calificaciones"));
            } else if (calif.devueltos() == 0) {
                alertas.add(new Alerta("info", "fa-star", "Aquí podrás calificar tus préstamos",
                        "Cuando devuelvas tu primer artículo, cuéntanos cómo te fue. Tu opinión ayuda a toda la red.",
                        "/lector/prestamos", "Pedir un préstamo"));
            } else {
                alertas.add(new Alerta("ok", "fa-circle-check", "Todo en orden",
                        "No tienes avisos ni pendientes por ahora.", "/lector/catalogo", "Explorar catálogo"));
            }
        } else if (alertas.size() > MAX_ALERTAS) {
            alertas = new ArrayList<>(alertas.subList(0, MAX_ALERTAS));
        }

        // ---------- Novedades del catálogo (lo último agregado con stock) ----------
        List<Novedad> novedades = new ArrayList<>();
        if (libreriaId != null) {
            Set<String> activas = new java.util.HashSet<>();
            politicaService.categoriasActivas(libreriaId).forEach(c -> activas.add(c.getValor()));
            recursoRepository.findByLibreriaId(libreriaId).stream()
                    .filter(r -> r.getStockDisponible() != null && r.getStockDisponible() > 0)
                    .filter(r -> activas.contains(CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor()))
                    .sorted(Comparator.comparing(Recurso::getId).reversed())
                    .limit(MAX_NOVEDADES)
                    .forEach(r -> {
                        CategoriaRecurso c = CategoriaRecurso.desdeValor(r.getTipoRecurso());
                        novedades.add(new Novedad(r.getId(), r.getTitulo(),
                                r.getAutorOCreador() == null || r.getAutorOCreador().isBlank() ? "Autor no registrado" : r.getAutorOCreador(),
                                c.getEtiqueta(), c.getIcono(), r.getStockDisponible()));
                    });
        }

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

        String primerNombre = lector.getNombres() == null ? "" : lector.getNombres().trim().split("\\s+")[0];
        String fecha = hoy.format(FECHA_LARGA);
        fecha = fecha.substring(0, 1).toUpperCase() + fecha.substring(1);

        return Optional.of(new PanelLectorVista(
                saludo(), primerNombre, fecha, sancionado ? "SANCIONADO" : "HABILITADO",
                enCursoBd.size(), sede == null ? null : politicaService.obtenerMaxActivos(libreriaId),
                proximaNivel, proximaValor, proximaDetalle, multasPend, montoPend,
                calif.devueltos(), calif.calificadas(), calif.promedio(),
                alertas, enCurso, novedades,
                sede == null ? "Sin librería" : sede.getNombreComercial(),
                sede == null ? "L" : iniciales(sede.getNombreComercial()),
                sede == null || sede.getDireccion() == null ? "" : sede.getDireccion(),
                sede == null || sede.getHorarioAtencion() == null ? "" : sede.getHorarioAtencion(),
                otras));
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