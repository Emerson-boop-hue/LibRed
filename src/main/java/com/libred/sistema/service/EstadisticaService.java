package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Calificacion;
import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.EstadisticaVista.Kpi;
import com.libred.sistema.model.EstadisticaVista.Pantalla;
import com.libred.sistema.model.IngresoVista;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.CalificacionRepository;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Módulo ESTADÍSTICAS del administrador (solo consulta). Todo sale de los datos que ya existen: no hay tablas
 * propias.
 */
@Service
public class EstadisticaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ETIQUETA_DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final CalificacionRepository calificacionRepository;
    private final IngresoService ingresoService;
    private final LectorService lectorService;
    private final RecursoService recursoService;

    public EstadisticaService(PrestamoRepository prestamoRepository,
                              DevolucionRepository devolucionRepository,
                              CalificacionRepository calificacionRepository,
                              IngresoService ingresoService,
                              LectorService lectorService,
                              RecursoService recursoService) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.calificacionRepository = calificacionRepository;
        this.ingresoService = ingresoService;
        this.lectorService = lectorService;
        this.recursoService = recursoService;
    }

    /** Cifras de un periodo (para los indicadores y su comparación). */
    private record Cifras(int prestamos, int devoluciones, BigDecimal ingresos, int lectoresNuevos,
                          Double puntualidad, Double calificacion) {
    }

    @Transactional(readOnly = true)
    public Pantalla armar(Integer libreriaId, String desdeTexto, String hastaTexto) {
        LocalDate hoy = LocalDate.now();
        LocalDate hasta = fecha(hastaTexto, hoy);
        LocalDate desde = fecha(desdeTexto, hoy.minusDays(29));
        if (hasta.isAfter(hoy)) {
            hasta = hoy;
        }
        if (desde.isAfter(hasta)) {
            desde = hasta;
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        LocalDate hastaAnt = desde.minusDays(1);
        LocalDate desdeAnt = hastaAnt.minusDays(dias - 1);

        List<Prestamo> prestamos = prestamoRepository.findByLibreriaId(libreriaId);
        List<Devolucion> devoluciones = devolucionRepository.findByLibreriaId(libreriaId);
        List<Calificacion> calificaciones = calificacionRepository.findByLibreriaId(libreriaId);
        List<IngresoVista.Fila> cobros = ingresoService.armarPantalla(libreriaId).filas();
        List<Lector> lectores = lectorService.listarPorLibreria(libreriaId, null);
        List<Recurso> recursos = recursoService.listarPorLibreria(libreriaId);

        Cifras actual = cifras(prestamos, devoluciones, calificaciones, cobros, lectores, desde, hasta);
        Cifras anterior = cifras(prestamos, devoluciones, calificaciones, cobros, lectores, desdeAnt, hastaAnt);

        List<Kpi> kpis = List.of(
                kpiNumero("Préstamos", "fa-book-open-reader", actual.prestamos(), anterior.prestamos()),
                kpiNumero("Devoluciones", "fa-rotate-left", actual.devoluciones(), anterior.devoluciones()),
                kpiDinero("Ingresos", "fa-cash-register", actual.ingresos(), anterior.ingresos()),
                kpiNumero("Lectores nuevos", "fa-user-plus", actual.lectoresNuevos(), anterior.lectoresNuevos()),
                kpiPorcentaje("Puntualidad", "fa-clock", actual.puntualidad(), anterior.puntualidad()),
                kpiCalificacion("Calificación", "fa-star", actual.calificacion(), anterior.calificacion())
        );

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tendencia", tendencia(prestamos, devoluciones, cobros, desde, hasta));
        datos.put("categorias", categorias(prestamos, desde, hasta));
        datos.put("estadoActual", estadoActual(prestamos, hoy));
        datos.put("topArticulos", topArticulos(prestamos, desde, hasta));
        datos.put("horarios", horarios(prestamos, desde, hasta));
        datos.put("calificaciones", distribucionCalificaciones(calificaciones, desde, hasta));
        datos.put("stock", stock(recursos));

        String periodo = desde.equals(hasta) ? "Día " + desde.format(FECHA)
                : "Del " + desde.format(FECHA) + " al " + hasta.format(FECHA) + " (" + dias + (dias == 1 ? " día)" : " días)");
        String periodoAnterior = desdeAnt.equals(hastaAnt) ? hastaAnt.format(FECHA)
                : desdeAnt.format(FECHA) + " al " + hastaAnt.format(FECHA);
        return new Pantalla(desde.toString(), hasta.toString(), periodo, periodoAnterior, kpis, json(datos));
    }

    private Cifras cifras(List<Prestamo> prestamos, List<Devolucion> devoluciones, List<Calificacion> calificaciones,
                          List<IngresoVista.Fila> cobros, List<Lector> lectores, LocalDate desde, LocalDate hasta) {
        int nPrestamos = 0;
        for (Prestamo p : prestamos) {
            if (cuenta(p) && enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)) {
                nPrestamos++;
            }
        }
        int nDev = 0, aTiempo = 0;
        for (Devolucion d : devoluciones) {
            if (d.getFechaDevolucion() != null && enRango(d.getFechaDevolucion().toLocalDate(), desde, hasta)) {
                nDev++;
                if (d.getDiasRetraso() == null || d.getDiasRetraso() == 0) {
                    aTiempo++;
                }
            }
        }
        BigDecimal ingresos = BigDecimal.ZERO;
        for (IngresoVista.Fila f : cobros) {
            if (enRango(LocalDate.parse(f.dia()), desde, hasta)) {
                ingresos = ingresos.add(f.monto());
            }
        }
        int nuevos = 0;
        for (Lector l : lectores) {
            if (l.getFechaRegistro() != null && enRango(l.getFechaRegistro().toLocalDate(), desde, hasta)) {
                nuevos++;
            }
        }
        int sumaEstrellas = 0, votos = 0;
        for (Calificacion c : calificaciones) {
            if (c.getFechaCalificacion() != null && c.getEstrellas() != null
                    && enRango(c.getFechaCalificacion().toLocalDate(), desde, hasta)) {
                sumaEstrellas += c.getEstrellas();
                votos++;
            }
        }
        return new Cifras(nPrestamos, nDev, ingresos, nuevos,
                nDev == 0 ? null : aTiempo * 100.0 / nDev,
                votos == 0 ? null : sumaEstrellas / (double) votos);
    }

    private Kpi kpiNumero(String titulo, String icono, int actual, int anterior) {
        return new Kpi(titulo, icono, String.valueOf(actual), String.valueOf(anterior), variacionRelativa(actual, anterior),
                tendencia(actual, anterior));
    }

    private Kpi kpiDinero(String titulo, String icono, BigDecimal actual, BigDecimal anterior) {
        return new Kpi(titulo, icono, "S/. " + dos(actual), "S/. " + dos(anterior),
                variacionRelativa(actual.doubleValue(), anterior.doubleValue()),
                tendencia(actual.doubleValue(), anterior.doubleValue()));
    }

    private Kpi kpiPorcentaje(String titulo, String icono, Double actual, Double anterior) {
        String v = actual == null ? "—" : Math.round(actual) + " %";
        String a = anterior == null ? "—" : Math.round(anterior) + " %";
        if (actual == null || anterior == null) {
            return new Kpi(titulo, icono, v, a, "", "na");
        }
        long dif = Math.round(actual) - Math.round(anterior);
        return new Kpi(titulo, icono, v, a, (dif > 0 ? "+" : "") + dif + " pts", tendencia(actual, anterior));
    }

    private Kpi kpiCalificacion(String titulo, String icono, Double actual, Double anterior) {
        String v = actual == null ? "—" : uno(actual) + " ★";
        String a = anterior == null ? "—" : uno(anterior) + " ★";
        if (actual == null || anterior == null) {
            return new Kpi(titulo, icono, v, a, "", "na");
        }
        double dif = Math.round(actual * 10) / 10.0 - Math.round(anterior * 10) / 10.0;
        return new Kpi(titulo, icono, v, a, (dif > 0 ? "+" : "") + uno(dif), tendencia(actual, anterior));
    }

    /** "+12 %" / "-5 %"; si antes era 0 no hay con qué comparar (""). */
    private String variacionRelativa(double actual, double anterior) {
        if (anterior == 0) {
            return "";
        }
        long pct = Math.round((actual - anterior) * 100 / anterior);
        return (pct > 0 ? "+" : "") + pct + " %";
    }

    private String tendencia(double actual, double anterior) {
        if (anterior == 0 && actual == 0) {
            return "igual";
        }
        if (anterior == 0) {
            return "na";
        }
        double dif = actual - anterior;
        return Math.abs(dif) < 0.0001 ? "igual" : (dif > 0 ? "sube" : "baja");
    }

    /** Préstamos, devoluciones e ingresos por día (o por semana si el periodo es largo). */
    private Map<String, Object> tendencia(List<Prestamo> prestamos, List<Devolucion> devoluciones,
                                          List<IngresoVista.Fila> cobros, LocalDate desde, LocalDate hasta) {
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        int paso = dias > 45 ? 7 : 1;
        int n = (int) ((dias + paso - 1) / paso);
        List<String> etiquetas = new ArrayList<>();
        List<String> detalle = new ArrayList<>();
        int[] pres = new int[n], devs = new int[n];
        double[] serv = new double[n], mora = new double[n], mult = new double[n];
        for (int i = 0; i < n; i++) {
            LocalDate ini = desde.plusDays((long) i * paso);
            LocalDate fin = ini.plusDays(paso - 1).isAfter(hasta) ? hasta : ini.plusDays(paso - 1);
            etiquetas.add(ini.format(ETIQUETA_DIA));
            detalle.add(paso == 1 ? ini.format(FECHA) : ini.format(ETIQUETA_DIA) + " al " + fin.format(ETIQUETA_DIA));
        }
        for (Prestamo p : prestamos) {
            if (cuenta(p)) {
                int i = indice(p.getFechaPrestamo().toLocalDate(), desde, hasta, paso);
                if (i >= 0) {
                    pres[i]++;
                }
            }
        }
        for (Devolucion d : devoluciones) {
            if (d.getFechaDevolucion() != null) {
                int i = indice(d.getFechaDevolucion().toLocalDate(), desde, hasta, paso);
                if (i >= 0) {
                    devs[i]++;
                }
            }
        }
        for (IngresoVista.Fila f : cobros) {
            int i = indice(LocalDate.parse(f.dia()), desde, hasta, paso);
            if (i < 0) {
                continue;
            }
            switch (f.concepto()) {
                case "SERVICIO" -> serv[i] += f.monto().doubleValue();
                case "MORA" -> mora[i] += f.monto().doubleValue();
                default -> mult[i] += f.monto().doubleValue();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agrupacion", paso == 1 ? "día" : "semana");
        m.put("etiquetas", etiquetas);
        m.put("detalle", detalle);
        m.put("prestamos", pres);
        m.put("devoluciones", devs);
        m.put("servicio", redondear(serv));
        m.put("mora", redondear(mora));
        m.put("multas", redondear(mult));
        return m;
    }

    private List<Map<String, Object>> categorias(List<Prestamo> prestamos, LocalDate desde, LocalDate hasta) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (Prestamo p : prestamos) {
            if (cuenta(p) && enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)) {
                String cat = p.getRecurso() == null ? "Sin categoría"
                        : CategoriaRecurso.desdeValor(p.getRecurso().getTipoRecurso()).getEtiqueta();
                cuenta.merge(cat, 1, Integer::sum);
            }
        }
        return ordenado(cuenta);
    }

    /** Foto de hoy de lo que está en circulación. */
    private List<Map<String, Object>> estadoActual(List<Prestamo> prestamos, LocalDate hoy) {
        int enPlazo = 0, vencidos = 0, porEntregar = 0;
        for (Prestamo p : prestamos) {
            String e = p.getEstadoPrestamo();
            if ("POR_ENTREGAR".equals(e)) {
                porEntregar++;
            } else if ("ACTIVO".equals(e)) {
                if (p.getFechaDevolucionLimite() != null && p.getFechaDevolucionLimite().isBefore(hoy)) {
                    vencidos++;
                } else {
                    enPlazo++;
                }
            }
        }
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("En plazo", enPlazo);
        m.put("Vencidos", vencidos);
        m.put("Por entregar", porEntregar);
        List<Map<String, Object>> lista = new ArrayList<>();
        m.forEach((k, v) -> lista.add(item(k, v)));
        return lista;
    }

    private List<Map<String, Object>> topArticulos(List<Prestamo> prestamos, LocalDate desde, LocalDate hasta) {
        Map<Integer, Integer> cuenta = new HashMap<>();
        Map<Integer, Recurso> recursos = new HashMap<>();
        for (Prestamo p : prestamos) {
            if (cuenta(p) && p.getRecurso() != null && enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)) {
                cuenta.merge(p.getRecurso().getId(), 1, Integer::sum);
                recursos.put(p.getRecurso().getId(), p.getRecurso());
            }
        }
        List<Map.Entry<Integer, Integer>> orden = new ArrayList<>(cuenta.entrySet());
        orden.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : recursos.get(a.getKey()).getTitulo().compareToIgnoreCase(recursos.get(b.getKey()).getTitulo());
        });
        List<Map<String, Object>> lista = new ArrayList<>();
        for (int i = 0; i < Math.min(10, orden.size()); i++) {
            Recurso r = recursos.get(orden.get(i).getKey());
            Map<String, Object> m = item(r.getTitulo(), orden.get(i).getValue());
            m.put("codigo", r.getCodigo());
            lista.add(m);
        }
        return lista;
    }

    /** Matriz [día de la semana (lunes=0)][hora 0-23] con los préstamos del periodo. */
    private int[][] horarios(List<Prestamo> prestamos, LocalDate desde, LocalDate hasta) {
        int[][] m = new int[7][24];
        for (Prestamo p : prestamos) {
            if (cuenta(p) && enRango(p.getFechaPrestamo().toLocalDate(), desde, hasta)) {
                m[p.getFechaPrestamo().getDayOfWeek().getValue() - 1][p.getFechaPrestamo().getHour()]++;
            }
        }
        return m;
    }

    /** Cantidad de votos de 1 a 5 estrellas en el periodo. */
    private int[] distribucionCalificaciones(List<Calificacion> calificaciones, LocalDate desde, LocalDate hasta) {
        int[] d = new int[5];
        for (Calificacion c : calificaciones) {
            if (c.getEstrellas() != null && c.getEstrellas() >= 1 && c.getEstrellas() <= 5
                    && c.getFechaCalificacion() != null && enRango(c.getFechaCalificacion().toLocalDate(), desde, hasta)) {
                d[c.getEstrellas() - 1]++;
            }
        }
        return d;
    }

    /** Foto de hoy del inventario por estado. */
    private List<Map<String, Object>> stock(List<Recurso> recursos) {
        int normal = 0, critico = 0, agotado = 0;
        for (Recurso r : recursos) {
            String e = r.getEstado() == null ? "" : r.getEstado().toUpperCase();
            if ("AGOTADO".equals(e)) {
                agotado++;
            } else if ("CRITICO".equals(e)) {
                critico++;
            } else {
                normal++;
            }
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        lista.add(item("Normal", normal));
        lista.add(item("Crítico", critico));
        lista.add(item("Agotado", agotado));
        return lista;
    }

    /** Un préstamo cuenta (para las cifras) si no fue cancelado y tiene fecha. */
    private static boolean cuenta(Prestamo p) {
        return p.getFechaPrestamo() != null && !"CANCELADO".equals(p.getEstadoPrestamo());
    }

    private static boolean enRango(LocalDate dia, LocalDate desde, LocalDate hasta) {
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    private static int indice(LocalDate dia, LocalDate desde, LocalDate hasta, int paso) {
        if (!enRango(dia, desde, hasta)) {
            return -1;
        }
        return (int) (ChronoUnit.DAYS.between(desde, dia) / paso);
    }

    private static LocalDate fecha(String texto, LocalDate porDefecto) {
        try {
            return texto == null || texto.isBlank() ? porDefecto : LocalDate.parse(texto.trim());
        } catch (Exception e) {
            return porDefecto;
        }
    }

    private static Map<String, Object> item(String nombre, int valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", nombre);
        m.put("valor", valor);
        return m;
    }

    private static List<Map<String, Object>> ordenado(Map<String, Integer> cuenta) {
        List<Map.Entry<String, Integer>> e = new ArrayList<>(cuenta.entrySet());
        e.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<String, Integer> x : e) {
            lista.add(item(x.getKey(), x.getValue()));
        }
        return lista;
    }

    private static double[] redondear(double[] v) {
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            r[i] = Math.round(v[i] * 100) / 100.0;
        }
        return r;
    }

    private static String dos(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String uno(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    // JSON mínimo (para no depender de una librería): mapas, listas, textos, números y arreglos

    private static String json(Object o) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, o);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '<' -> sb.append("\\u003c");   // que un título no pueda cerrar el <script>
                    case '>' -> sb.append("\\u003e");
                    case '&' -> sb.append("\\u0026");
                    default -> sb.append(c < 32 ? " " : String.valueOf(c));
                }
            }
            sb.append('"');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escribir(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Collection<?> c) {
            sb.append('[');
            boolean primero = true;
            for (Object x : c) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(sb, x);
            }
            sb.append(']');
        } else if (o instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                sb.append(i == 0 ? "" : ",").append(a[i]);
            }
            sb.append(']');
        } else if (o instanceof double[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                sb.append(i == 0 ? "" : ",").append(a[i]);
            }
            sb.append(']');
        } else if (o instanceof int[][] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                sb.append(i == 0 ? "" : ",");
                escribir(sb, a[i]);
            }
            sb.append(']');
        } else {
            escribir(sb, String.valueOf(o));
        }
    }
}