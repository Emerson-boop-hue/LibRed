package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/** Datos de la Vista General del lector (su resumen en la librería donde está). */
public class PanelLectorVista {

    // ---- Cabecera ----
    private final String saludo;          // "Buenas noches"
    private final String nombre;          // primer nombre
    private final String fechaHoy;        // "lunes, 2 de noviembre"
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

    private final List<Alerta> alertas;
    private final List<EnCurso> enCurso;
    private final List<Novedad> novedades;

    // ---- Su librería y su red ----
    private final String sedeNombre;
    private final String sedeIniciales;
    private final String sedeDireccion;
    private final String sedeHorario;
    private final List<SedeRed> otrasSedes;

    public PanelLectorVista(String saludo, String nombre, String fechaHoy, String estadoCuenta,
                            long activos, Integer maxActivos, String proximaNivel, String proximaValor,
                            String proximaDetalle, long multasPendientes, BigDecimal montoPendiente,
                            long devueltos, long calificadas, String promedioCalif,
                            List<Alerta> alertas, List<EnCurso> enCurso, List<Novedad> novedades,
                            String sedeNombre, String sedeIniciales, String sedeDireccion, String sedeHorario,
                            List<SedeRed> otrasSedes) {
        this.saludo = saludo; this.nombre = nombre; this.fechaHoy = fechaHoy; this.estadoCuenta = estadoCuenta;
        this.activos = activos; this.maxActivos = maxActivos; this.proximaNivel = proximaNivel;
        this.proximaValor = proximaValor; this.proximaDetalle = proximaDetalle;
        this.multasPendientes = multasPendientes; this.montoPendiente = montoPendiente;
        this.devueltos = devueltos; this.calificadas = calificadas; this.promedioCalif = promedioCalif;
        this.alertas = alertas; this.enCurso = enCurso; this.novedades = novedades;
        this.sedeNombre = sedeNombre; this.sedeIniciales = sedeIniciales;
        this.sedeDireccion = sedeDireccion; this.sedeHorario = sedeHorario; this.otrasSedes = otrasSedes;
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
    public List<Alerta> getAlertas() { return alertas; }
    public List<EnCurso> getEnCurso() { return enCurso; }
    public List<Novedad> getNovedades() { return novedades; }
    public String getSedeNombre() { return sedeNombre; }
    public String getSedeIniciales() { return sedeIniciales; }
    public String getSedeDireccion() { return sedeDireccion; }
    public String getSedeHorario() { return sedeHorario; }
    public List<SedeRed> getOtrasSedes() { return otrasSedes; }

    /** Aviso prioritario de la parte de arriba. nivel: peligro | aviso | ok | info. */
    public static class Alerta {
        private final String nivel, icono, titulo, detalle, enlace, boton;
        public Alerta(String nivel, String icono, String titulo, String detalle, String enlace, String boton) {
            this.nivel = nivel; this.icono = icono; this.titulo = titulo;
            this.detalle = detalle; this.enlace = enlace; this.boton = boton;
        }
        public String getNivel() { return nivel; }
        public String getIcono() { return icono; }
        public String getTitulo() { return titulo; }
        public String getDetalle() { return detalle; }
        public String getEnlace() { return enlace; }
        public String getBoton() { return boton; }
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

    /** Artículo recién agregado al catálogo de la librería. */
    public static class Novedad {
        private final Integer id;
        private final String titulo, autor, categoria, icono;
        private final int disponibles;
        public Novedad(Integer id, String titulo, String autor, String categoria, String icono, int disponibles) {
            this.id = id; this.titulo = titulo; this.autor = autor;
            this.categoria = categoria; this.icono = icono; this.disponibles = disponibles;
        }
        public Integer getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getAutor() { return autor; }
        public String getCategoria() { return categoria; }
        public String getIcono() { return icono; }
        public int getDisponibles() { return disponibles; }
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