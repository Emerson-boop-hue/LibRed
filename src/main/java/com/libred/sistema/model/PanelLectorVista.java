package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/** Datos de la Vista General del lector (su resumen en la librería donde está). */
public class PanelLectorVista {

    // ---- Cabecera (va en el título de la página) ----
    private final String saludo;          // "Buenas noches"
    private final String nombre;          // primer nombre
    private final String fechaHoy;        // "Lunes, 2 de noviembre"
    private final String estadoCuenta;    // HABILITADO / SANCIONADO

    // ---- Números ----
    private final long activos;
    private final Integer maxActivos;     // null = sin límite
    private final String proximaNivel;    // ninguna | ok | pronto | vencido
    private final String proximaValor;    // "3 días", "Mañana", "Hoy", "Vencido", "—"
    private final String proximaDetalle;  // título y fecha
    private final long multasPendientes;
    private final BigDecimal montoPendiente;
    private final long devueltos;
    private final long calificadas;
    private final String promedioCalif;

    private final long vencidos;           // préstamos con la fecha límite ya pasada
    private final String vencidoTitulo;    // el más atrasado (para decirle qué devolver); null si no hay
    private final long porCalificar;      // préstamos devueltos que todavía no calificó (recordatorio flotante)
    private final List<EnCurso> enCurso;

    // ---- Gráficos de "Tu actividad" ----
    private final List<Mes> meses;             // préstamos por mes (últimos 6)
    private final List<Categoria> categorias;  // préstamos por categoría (de mayor a menor)
    private final long totalPrestamos;
    private final int puntualidad;             // % de devoluciones a tiempo; -1 si todavía no hay devoluciones

    // ---- Su librería y su red ----
    private final String sedeNombre;
    private final String sedeIniciales;
    private final String sedeHorario;
    private final List<SedeRed> otrasSedes;    // las primeras 3
    private final int otrasMas;                // cuántas más hay (no se muestran)

    public PanelLectorVista(String saludo, String nombre, String fechaHoy, String estadoCuenta,
                            long activos, Integer maxActivos, String proximaNivel, String proximaValor,
                            String proximaDetalle, long multasPendientes, BigDecimal montoPendiente,
                            long devueltos, long calificadas, String promedioCalif,
                            long vencidos, String vencidoTitulo, long porCalificar, List<EnCurso> enCurso,
                            List<Mes> meses, List<Categoria> categorias, long totalPrestamos, int puntualidad,
                            String sedeNombre, String sedeIniciales, String sedeHorario,
                            List<SedeRed> otrasSedes, int otrasMas) {
        this.saludo = saludo; this.nombre = nombre; this.fechaHoy = fechaHoy; this.estadoCuenta = estadoCuenta;
        this.activos = activos; this.maxActivos = maxActivos; this.proximaNivel = proximaNivel;
        this.proximaValor = proximaValor; this.proximaDetalle = proximaDetalle;
        this.multasPendientes = multasPendientes; this.montoPendiente = montoPendiente;
        this.devueltos = devueltos; this.calificadas = calificadas; this.promedioCalif = promedioCalif;
        this.vencidos = vencidos; this.vencidoTitulo = vencidoTitulo; this.porCalificar = porCalificar; this.enCurso = enCurso;
        this.meses = meses; this.categorias = categorias; this.totalPrestamos = totalPrestamos;
        this.puntualidad = puntualidad;
        this.sedeNombre = sedeNombre; this.sedeIniciales = sedeIniciales; this.sedeHorario = sedeHorario;
        this.otrasSedes = otrasSedes; this.otrasMas = otrasMas;
    }

    public String getSaludo() { return saludo; }
    public String getNombre() { return nombre; }
    public String getFechaHoy() { return fechaHoy; }
    public String getEstadoCuenta() { return estadoCuenta; }
    public boolean isSancionado() { return "SANCIONADO".equalsIgnoreCase(estadoCuenta); }
    public long getActivos() { return activos; }
    public Integer getMaxActivos() { return maxActivos; }
    public boolean isLimitado() { return maxActivos != null; }
    public String getProximaNivel() { return proximaNivel; }
    public String getProximaValor() { return proximaValor; }
    public String getProximaDetalle() { return proximaDetalle; }
    public long getMultasPendientes() { return multasPendientes; }
    public BigDecimal getMontoPendiente() { return montoPendiente; }
    public long getDevueltos() { return devueltos; }
    public long getCalificadas() { return calificadas; }
    public String getPromedioCalif() { return promedioCalif; }
    public long getVencidos() { return vencidos; }
    public String getVencidoTitulo() { return vencidoTitulo; }
    public long getPorCalificar() { return porCalificar; }
    public List<EnCurso> getEnCurso() { return enCurso; }
    public List<Mes> getMeses() { return meses; }
    public List<Categoria> getCategorias() { return categorias; }
    public long getTotalPrestamos() { return totalPrestamos; }
    public int getPuntualidad() { return puntualidad; }
    public boolean isConPuntualidad() { return puntualidad >= 0; }
    public String getSedeNombre() { return sedeNombre; }
    public String getSedeIniciales() { return sedeIniciales; }
    public String getSedeHorario() { return sedeHorario; }
    public List<SedeRed> getOtrasSedes() { return otrasSedes; }
    public int getOtrasMas() { return otrasMas; }

    /** Barra del gráfico de préstamos por mes. */
    public static class Mes {
        private final String etiqueta;
        private final long cantidad;
        private final int porcentaje; // altura de la barra respecto al mes con más préstamos
        public Mes(String etiqueta, long cantidad, int porcentaje) {
            this.etiqueta = etiqueta; this.cantidad = cantidad; this.porcentaje = porcentaje;
        }
        public String getEtiqueta() { return etiqueta; }
        public long getCantidad() { return cantidad; }
        public int getPorcentaje() { return porcentaje; }
    }

    /** Barra horizontal del gráfico de préstamos por categoría. */
    public static class Categoria {
        private final String etiqueta, icono;
        private final long cantidad;
        private final int porcentaje; // ancho de la barra respecto a la categoría con más préstamos
        public Categoria(String etiqueta, String icono, long cantidad, int porcentaje) {
            this.etiqueta = etiqueta; this.icono = icono; this.cantidad = cantidad; this.porcentaje = porcentaje;
        }
        public String getEtiqueta() { return etiqueta; }
        public String getIcono() { return icono; }
        public long getCantidad() { return cantidad; }
        public int getPorcentaje() { return porcentaje; }
    }

    /** Un préstamo en curso (ACTIVO o VENCIDO) con su barra de tiempo. */
    public static class EnCurso {
        private final String titulo, categoria, icono, fechaLimite, restante, nivel;
        private final int porcentaje; // tiempo ya transcurrido del plazo (0-100)
        public EnCurso(String titulo, String categoria, String icono, String fechaLimite,
                       String restante, String nivel, int porcentaje) {
            this.titulo = titulo; this.categoria = categoria; this.icono = icono;
            this.fechaLimite = fechaLimite; this.restante = restante; this.nivel = nivel; this.porcentaje = porcentaje;
        }
        public String getTitulo() { return titulo; }
        public String getCategoria() { return categoria; }
        public String getIcono() { return icono; }
        public String getFechaLimite() { return fechaLimite; }
        public String getRestante() { return restante; }
        public String getNivel() { return nivel; }   // ok | pronto | vencido
        public int getPorcentaje() { return porcentaje; }
    }

    /** Otra librería donde el lector ya está registrado (para saltar a ella). */
    public static class SedeRed {
        private final Integer id;
        private final String nombre, iniciales;
        private final int tono;
        public SedeRed(Integer id, String nombre, String iniciales, int tono) {
            this.id = id; this.nombre = nombre; this.iniciales = iniciales; this.tono = tono;
        }
        public Integer getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public int getTono() { return tono; }
    }
}