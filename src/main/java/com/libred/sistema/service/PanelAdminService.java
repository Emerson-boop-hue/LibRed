package com.libred.sistema.service;

import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.IngresoVista;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.PanelAdminVista.Actividad;
import com.libred.sistema.model.PanelAdminVista.Atencion;
import com.libred.sistema.model.PanelAdminVista.Dia;
import com.libred.sistema.model.PanelAdminVista.Grupo;
import com.libred.sistema.model.PanelAdminVista.Kpi;
import com.libred.sistema.model.PanelAdminVista.Pantalla;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PANEL DE CONTROL del administrador (solo consulta). Resume el estado de SU librería con datos que ya existen: no
 * tiene tablas propias.
 */
@Service
public class PanelAdminService {

    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DIAS_SEMANA = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final int MAX_ACTIVIDAD = 300;

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;
    private final RecursoService recursoService;
    private final LectorService lectorService;
    private final IngresoService ingresoService;

    public PanelAdminService(PrestamoRepository prestamoRepository,
                             DevolucionRepository devolucionRepository,
                             MultaRepository multaRepository,
                             RecursoService recursoService,
                             LectorService lectorService,
                             IngresoService ingresoService) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
        this.recursoService = recursoService;
        this.lectorService = lectorService;
        this.ingresoService = ingresoService;
    }

    @Transactional(readOnly = true)
    public Pantalla armar(Integer libreriaId) {
        LocalDate hoy = LocalDate.now();
        List<Prestamo> prestamos = prestamoRepository.findByLibreriaId(libreriaId);
        List<Devolucion> devoluciones = devolucionRepository.findByLibreriaId(libreriaId);
        List<Recurso> recursos = recursoService.listarPorLibreria(libreriaId);
        Long libLong = libreriaId.longValue();
        List<Multa> pendientes = multaRepository.findByLibreriaIdAndEstadoPago(libLong, "PENDIENTE");
        List<Multa> pagadas = multaRepository.findByLibreriaIdAndEstadoPago(libLong, "PAGADO");

        // Préstamos de hoy hacia atrás
        long enCurso = 0, vencidos = 0, porEntregar = 0;
        for (Prestamo p : prestamos) {
            String e = p.getEstadoPrestamo();
            if ("POR_ENTREGAR".equals(e)) {
                porEntregar++;
            } else if ("ACTIVO".equals(e) || "VENCIDO".equals(e)) {
                enCurso++;
                if (p.getFechaDevolucionLimite() != null) {
                    if (p.getFechaDevolucionLimite().isBefore(hoy)) {
                        vencidos++;
                    }
                }
            }
        }

        int ejemplares = 0, disponibles = 0, stockBajo = 0;
        for (Recurso r : recursos) {
            int total = r.getStockTotal() == null ? 0 : r.getStockTotal();
            int disp = r.getStockDisponible() == null ? 0 : r.getStockDisponible();
            ejemplares += total;
            disponibles += Math.max(disp, 0);
            if (disp <= 2) { // Crítico (1-2) o Agotado (0)
                stockBajo++;
            }
        }

        long lectores = lectorService.contarTotalLectoresPorLibreria(libreriaId);
        long bloqueados = lectorService.contarLectoresBloqueadosPorLibreria(libreriaId);

        // Cobrado (misma fuente que el módulo Ingresos)
        BigDecimal cobradoHoy = BigDecimal.ZERO, cobradoMes = BigDecimal.ZERO;
        for (IngresoVista.Fila f : ingresoService.armarPantalla(libreriaId).filas()) {
            LocalDate dia = LocalDate.parse(f.dia());
            if (dia.getYear() == hoy.getYear() && dia.getMonth() == hoy.getMonth() && !dia.isAfter(hoy)) {
                cobradoMes = cobradoMes.add(f.monto());
                if (dia.isEqual(hoy)) {
                    cobradoHoy = cobradoHoy.add(f.monto());
                }
            }
        }

        List<Kpi> kpis = List.of(
                new Kpi("Artículos", "fa-book", String.valueOf(recursos.size()),
                        disponibles + " de " + ejemplares + " ejemplares disponibles", "azul"),
                new Kpi("Lectores", "fa-users", String.valueOf(lectores),
                        bloqueados == 0 ? "Ninguno bloqueado" : bloqueados + (bloqueados == 1 ? " bloqueado" : " bloqueados"), "verde"),
                new Kpi("En préstamo", "fa-right-left", String.valueOf(enCurso),
                        vencidos == 0 ? "Ninguno vencido" : vencidos + (vencidos == 1 ? " vencido" : " vencidos"), "azul"),
                new Kpi("Cobrado este mes", "fa-cash-register", "S/. " + dos(cobradoMes), "Hoy: S/. " + dos(cobradoHoy), "verde"));

        BigDecimal montoMultas = BigDecimal.ZERO;
        for (Multa m : pendientes) {
            montoMultas = montoMultas.add(m.getMonto() == null ? BigDecimal.ZERO : m.getMonto());
        }
        List<Atencion> atencion = List.of(
                new Atencion("fa-box-open", "Pedidos por entregar", porEntregar,
                        "Reservados por lectores", "/admin/prestamos?tab=por-entregar", "ambar"),
                new Atencion("fa-triangle-exclamation", "Préstamos vencidos", vencidos,
                        "Ya pasó su fecha límite", "/admin/devoluciones", "rojo"),
                new Atencion("fa-file-invoice-dollar", "Multas pendientes", pendientes.size(),
                        "S/. " + dos(montoMultas) + " sin cobrar", "/admin/multas", "rojo"),
                new Atencion("fa-boxes-stacked", "Stock crítico o agotado", stockBajo,
                        "Con 2 o menos disponibles", "/admin/inventario", "ambar"));

        int[] pres = new int[7], devs = new int[7];
        for (Prestamo p : prestamos) {
            if (p.getFechaPrestamo() != null && !"CANCELADO".equals(p.getEstadoPrestamo())) {
                int i = posicion(p.getFechaPrestamo().toLocalDate(), hoy);
                if (i >= 0) {
                    pres[i]++;
                }
            }
        }
        for (Devolucion d : devoluciones) {
            if (d.getFechaDevolucion() != null) {
                int i = posicion(d.getFechaDevolucion().toLocalDate(), hoy);
                if (i >= 0) {
                    devs[i]++;
                }
            }
        }
        int max = 1, totalPres = 0, totalDev = 0;
        for (int i = 0; i < 7; i++) {
            max = Math.max(max, Math.max(pres[i], devs[i]));
            totalPres += pres[i];
            totalDev += devs[i];
        }
        List<Dia> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = hoy.minusDays(6 - i);
            String etiqueta = i == 6 ? "Hoy" : DIAS_SEMANA[d.getDayOfWeek().getValue() - 1] + " " + d.format(DIA_CORTO);
            dias.add(new Dia(etiqueta, pres[i], devs[i], pres[i] * 100 / max, devs[i] * 100 / max));
        }

        List<Grupo> historial = actividad(prestamos, devoluciones, pendientes, pagadas, hoy);
        int totalActividad = 0;
        for (Grupo g : historial) {
            totalActividad += g.movimientos().size();
        }
        return new Pantalla(kpis, atencion, dias, totalPres, totalDev, totalActividad, historial);
    }

    /**
     * Historial de movimientos (préstamos, pedidos, devoluciones y multas), del más nuevo al más antiguo, agrupado
     * por día.
     */
    private List<Grupo> actividad(List<Prestamo> prestamos, List<Devolucion> devoluciones,
                                  List<Multa> pendientes, List<Multa> pagadas, LocalDate hoy) {
        List<Object[]> lista = new ArrayList<>();
        for (Prestamo p : prestamos) {
            if (p.getFechaPrestamo() == null || "CANCELADO".equals(p.getEstadoPrestamo())) {
                continue;
            }
            boolean pedido = "POR_ENTREGAR".equals(p.getEstadoPrestamo());
            lista.add(new Object[]{p.getFechaPrestamo(), pedido ? "fa-box-open" : "fa-hand-holding",
                    pedido ? "Pedido" : "Préstamo", pedido ? "pedido" : "prestamo",
                    lector(p), titulo(p), pedido ? "ambar" : "azul"});
        }
        for (Devolucion d : devoluciones) {
            if (d.getFechaDevolucion() == null || d.getPrestamo() == null) {
                continue;
            }
            lista.add(new Object[]{d.getFechaDevolucion(), "fa-rotate-left", "Devolución", "devolucion",
                    lector(d.getPrestamo()), titulo(d.getPrestamo()), "verde"});
        }
        Map<Long, Multa> multas = new LinkedHashMap<>();
        for (Multa m : pendientes) {
            multas.put(m.getId(), m);
        }
        for (Multa m : pagadas) {
            multas.put(m.getId(), m);
        }
        for (Multa m : multas.values()) {
            if (m.getFechaEmision() == null) {
                continue;
            }
            String quien = m.getLector() == null ? "—" : m.getLector().getNombres() + " " + m.getLector().getApellidos();
            lista.add(new Object[]{m.getFechaEmision(), "fa-file-invoice-dollar", "Multa", "multa", quien,
                    "S/. " + dos(m.getMonto() == null ? BigDecimal.ZERO : m.getMonto()), "rojo"});
        }
        lista.sort(Comparator.comparing((Object[] o) -> (LocalDateTime) o[0]).reversed());

        List<Grupo> grupos = new ArrayList<>();
        String etiquetaActual = null;
        List<Actividad> actual = null;
        for (int i = 0; i < Math.min(MAX_ACTIVIDAD, lista.size()); i++) {
            Object[] o = lista.get(i);
            LocalDateTime t = (LocalDateTime) o[0];
            String etiqueta = dia(t.toLocalDate(), hoy);
            if (!etiqueta.equals(etiquetaActual)) {
                actual = new ArrayList<>();
                grupos.add(new Grupo(etiqueta, actual));
                etiquetaActual = etiqueta;
            }
            actual.add(new Actividad((String) o[1], (String) o[2], (String) o[3], (String) o[4], (String) o[5],
                    t.format(HORA), (String) o[6]));
        }
        return grupos;
    }

    private static String lector(Prestamo p) {
        return p.getLector() == null ? "—" : p.getLector().getNombres() + " " + p.getLector().getApellidos();
    }

    private static String titulo(Prestamo p) {
        return p.getRecurso() == null ? "—" : p.getRecurso().getTitulo();
    }

    /** "Hoy", "Ayer" o "Mié 05/11". */
    private static String dia(LocalDate d, LocalDate hoy) {
        if (d.isEqual(hoy)) {
            return "Hoy";
        }
        if (d.isEqual(hoy.minusDays(1))) {
            return "Ayer";
        }
        return DIAS_SEMANA[d.getDayOfWeek().getValue() - 1] + " " + d.format(DIA_CORTO);
    }

    /** Posición (0 = hace 6 días ... 6 = hoy) de un día dentro de la última semana; -1 si queda fuera. */
    private static int posicion(LocalDate dia, LocalDate hoy) {
        long atras = java.time.temporal.ChronoUnit.DAYS.between(dia, hoy);
        return atras < 0 || atras > 6 ? -1 : (int) (6 - atras);
    }

    private static String dos(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}