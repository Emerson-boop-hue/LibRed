package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos ya preparados para pintar la pantalla "Historial" del lector (no son tablas de la BD).
 */
public class HistorialLectorVista {

    /** Una fila de la pestaña "Préstamos". */
    public static class FilaPrestamo {
        private final Integer id;
        private final String titulo;
        private final String codigo;
        private final String categoria;
        private final String fechaPrestamo;
        private final String fechaLimite;
        private final String fechaDevuelto;   // "—" si todavía no lo devolvió
        private final int dias;
        private final BigDecimal servicio;
        private final BigDecimal cargos;      // mora + multas pagadas al devolver
        private final String detalleCargos;   // "3 días de atraso · Mora S/ 3.00 · Multa S/ 0.00"
        private final String estado;          // ACTIVO, VENCIDO, DEVUELTO
        private final String estadoTexto;
        private final String detalleEstado;
        private final int calificacion;      // 0 = todavía sin calificar

        public FilaPrestamo(Integer id, String titulo, String codigo, String categoria, String fechaPrestamo,
                            String fechaLimite, String fechaDevuelto, int dias, BigDecimal servicio,
                            BigDecimal cargos, String detalleCargos, String estado, String estadoTexto,
                            String detalleEstado, int calificacion) {
            this.id = id;
            this.titulo = titulo;
            this.codigo = codigo;
            this.categoria = categoria;
            this.fechaPrestamo = fechaPrestamo;
            this.fechaLimite = fechaLimite;
            this.fechaDevuelto = fechaDevuelto;
            this.dias = dias;
            this.servicio = servicio;
            this.cargos = cargos;
            this.detalleCargos = detalleCargos;
            this.estado = estado;
            this.estadoTexto = estadoTexto;
            this.detalleEstado = detalleEstado;
            this.calificacion = calificacion;
        }

        public Integer getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getCodigo() { return codigo; }
        public String getCategoria() { return categoria; }
        public String getFechaPrestamo() { return fechaPrestamo; }
        public String getFechaLimite() { return fechaLimite; }
        public String getFechaDevuelto() { return fechaDevuelto; }
        public int getDias() { return dias; }
        public BigDecimal getServicio() { return servicio; }
        public BigDecimal getCargos() { return cargos; }
        public String getDetalleCargos() { return detalleCargos; }
        public String getEstado() { return estado; }
        public String getEstadoTexto() { return estadoTexto; }
        public String getDetalleEstado() { return detalleEstado; }
        public int getCalificacion() { return calificacion; }
        /** Solo los préstamos ya devueltos se pueden calificar. */
        public boolean isCalificable() { return "DEVUELTO".equals(estado); }
    }

    /** Una fila de la pestaña "Calificaciones" (lo que el lector ya calificó). */
    public static class FilaCalificacion {
        private final Integer prestamoId;
        private final String titulo;
        private final String codigo;
        private final String categoria;
        private final int estrellas;
        private final String etiqueta;
        private final String fecha;
        private final boolean modificada;

        public FilaCalificacion(Integer prestamoId, String titulo, String codigo, String categoria, int estrellas,
                                String etiqueta, String fecha, boolean modificada) {
            this.prestamoId = prestamoId;
            this.titulo = titulo;
            this.codigo = codigo;
            this.categoria = categoria;
            this.estrellas = estrellas;
            this.etiqueta = etiqueta;
            this.fecha = fecha;
            this.modificada = modificada;
        }

        public Integer getPrestamoId() { return prestamoId; }
        public String getTitulo() { return titulo; }
        public String getCodigo() { return codigo; }
        public String getCategoria() { return categoria; }
        public int getEstrellas() { return estrellas; }
        public String getEtiqueta() { return etiqueta; }
        public String getFecha() { return fecha; }
        public boolean isModificada() { return modificada; }
    }

    /** Una fila de la pestaña "Multas". */
    public static class FilaMulta {
        private final Long id;
        private final Integer prestamoId;
        private final String titulo;
        private final String motivo;
        private final BigDecimal monto;
        private final String fechaEmision;
        private final boolean pagada;
        private final String fechaPago;

        public FilaMulta(Long id, Integer prestamoId, String titulo, String motivo, BigDecimal monto,
                         String fechaEmision, boolean pagada, String fechaPago) {
            this.id = id;
            this.prestamoId = prestamoId;
            this.titulo = titulo;
            this.motivo = motivo;
            this.monto = monto;
            this.fechaEmision = fechaEmision;
            this.pagada = pagada;
            this.fechaPago = fechaPago;
        }

        public Long getId() { return id; }
        public Integer getPrestamoId() { return prestamoId; }
        public String getTitulo() { return titulo; }
        public String getMotivo() { return motivo; }
        public BigDecimal getMonto() { return monto; }
        public String getFechaEmision() { return fechaEmision; }
        public boolean isPagada() { return pagada; }
        public String getFechaPago() { return fechaPago; }
    }

    /** Todo lo que necesita la pantalla. */
    public static class Pantalla {
        private final String sedeNombre;
        private final int total;
        private final int enCurso;
        private final int devueltos;
        private final int multasPendientes;
        private final BigDecimal montoMultasPendientes;
        private final List<FilaPrestamo> prestamos;
        private final List<FilaMulta> multas;
        private final List<String> categorias;
        private final int porCalificar;
        private final List<FilaCalificacion> calificaciones;

        public Pantalla(String sedeNombre, int total, int enCurso, int devueltos, int multasPendientes,
                        BigDecimal montoMultasPendientes, List<FilaPrestamo> prestamos, List<FilaMulta> multas,
                        List<String> categorias, int porCalificar,
                        List<FilaCalificacion> calificaciones) {
            this.sedeNombre = sedeNombre;
            this.total = total;
            this.enCurso = enCurso;
            this.devueltos = devueltos;
            this.multasPendientes = multasPendientes;
            this.montoMultasPendientes = montoMultasPendientes;
            this.prestamos = prestamos;
            this.multas = multas;
            this.categorias = categorias;
            this.porCalificar = porCalificar;
            this.calificaciones = calificaciones;
        }

        public String getSedeNombre() { return sedeNombre; }
        public int getTotal() { return total; }
        public int getEnCurso() { return enCurso; }
        public int getDevueltos() { return devueltos; }
        public int getMultasPendientes() { return multasPendientes; }
        public BigDecimal getMontoMultasPendientes() { return montoMultasPendientes; }
        public List<FilaPrestamo> getPrestamos() { return prestamos; }
        public List<FilaMulta> getMultas() { return multas; }
        public List<String> getCategorias() { return categorias; }
        public int getPorCalificar() { return porCalificar; }
        public List<FilaCalificacion> getCalificaciones() { return calificaciones; }
        public int getTotalCalificadas() { return calificaciones.size(); }
    }
}