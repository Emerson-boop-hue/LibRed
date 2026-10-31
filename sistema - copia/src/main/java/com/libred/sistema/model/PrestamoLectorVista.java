package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos ya preparados para pintar la pantalla "Préstamos" del lector (no son tablas de la BD).
 * Son clases simples con getters para que Thymeleaf las lea sin problemas.
 */
public class PrestamoLectorVista {

    /** Una categoría con artículos disponibles y su tarifa por día. */
    public static class CategoriaVista {
        private final String valor;
        private final String etiqueta;
        private final String icono;
        private final BigDecimal tarifa;
        private final long disponibles;

        public CategoriaVista(String valor, String etiqueta, String icono, BigDecimal tarifa, long disponibles) {
            this.valor = valor;
            this.etiqueta = etiqueta;
            this.icono = icono;
            this.tarifa = tarifa;
            this.disponibles = disponibles;
        }

        public String getValor() { return valor; }
        public String getEtiqueta() { return etiqueta; }
        public String getIcono() { return icono; }
        public BigDecimal getTarifa() { return tarifa; }
        public long getDisponibles() { return disponibles; }
    }

    /** Un artículo que se puede pedir (con stock). */
    public static class ArticuloVista {
        private final Integer id;
        private final String titulo;
        private final String codigo;
        private final String autor;
        private final String categoria; // valor de la categoría
        private final int stock;

        public ArticuloVista(Integer id, String titulo, String codigo, String autor, String categoria, int stock) {
            this.id = id;
            this.titulo = titulo;
            this.codigo = codigo;
            this.autor = autor;
            this.categoria = categoria;
            this.stock = stock;
        }

        public Integer getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getCodigo() { return codigo; }
        public String getAutor() { return autor; }
        public String getCategoria() { return categoria; }
        public int getStock() { return stock; }
    }

    /** Una fila de "Mis préstamos". */
    public static class FilaPrestamo {
        private final Integer id;
        private final String titulo;
        private final String codigo;
        private final String categoria;
        private final String fechaPrestamo;
        private final String fechaLimite;
        private final int dias;
        private final BigDecimal monto;
        private final String estado;      // ACTIVO, VENCIDO, DEVUELTO
        private final String estadoTexto;
        private final String detalle;     // "Vence en 3 días", "Venció hace 2 días"...

        public FilaPrestamo(Integer id, String titulo, String codigo, String categoria, String fechaPrestamo,
                            String fechaLimite, int dias, BigDecimal monto, String estado, String estadoTexto,
                            String detalle) {
            this.id = id;
            this.titulo = titulo;
            this.codigo = codigo;
            this.categoria = categoria;
            this.fechaPrestamo = fechaPrestamo;
            this.fechaLimite = fechaLimite;
            this.dias = dias;
            this.monto = monto;
            this.estado = estado;
            this.estadoTexto = estadoTexto;
            this.detalle = detalle;
        }

        public Integer getId() { return id; }
        public String getTitulo() { return titulo; }
        public String getCodigo() { return codigo; }
        public String getCategoria() { return categoria; }
        public String getFechaPrestamo() { return fechaPrestamo; }
        public String getFechaLimite() { return fechaLimite; }
        public int getDias() { return dias; }
        public BigDecimal getMonto() { return monto; }
        public String getEstado() { return estado; }
        public String getEstadoTexto() { return estadoTexto; }
        public String getDetalle() { return detalle; }
    }

    /** Todo lo que necesita la pantalla. */
    public static class Pantalla {
        private final String lectorNombre;
        private final String lectorCodigo;
        private final String sedeNombre;
        private final int maxDias;
        private final Integer maxActivos; // null = sin límite
        private final long activos;
        private final String bloqueo; // null si puede pedir; si no, el motivo
        private final List<CategoriaVista> categorias;
        private final List<ArticuloVista> articulos;
        private final List<FilaPrestamo> prestamos;

        public Pantalla(String lectorNombre, String lectorCodigo, String sedeNombre, int maxDias, Integer maxActivos,
                        long activos, String bloqueo, List<CategoriaVista> categorias,
                        List<ArticuloVista> articulos, List<FilaPrestamo> prestamos) {
            this.lectorNombre = lectorNombre;
            this.lectorCodigo = lectorCodigo;
            this.sedeNombre = sedeNombre;
            this.maxDias = maxDias;
            this.maxActivos = maxActivos;
            this.activos = activos;
            this.bloqueo = bloqueo;
            this.categorias = categorias;
            this.articulos = articulos;
            this.prestamos = prestamos;
        }

        public String getLectorNombre() { return lectorNombre; }
        public String getLectorCodigo() { return lectorCodigo; }
        public String getSedeNombre() { return sedeNombre; }
        public int getMaxDias() { return maxDias; }
        public Integer getMaxActivos() { return maxActivos; }
        public boolean isLimitado() { return maxActivos != null; }
        public boolean isLleno() { return maxActivos != null && activos >= maxActivos; }
        public long getActivos() { return activos; }
        public String getBloqueo() { return bloqueo; }
        public List<CategoriaVista> getCategorias() { return categorias; }
        public List<ArticuloVista> getArticulos() { return articulos; }
        public List<FilaPrestamo> getPrestamos() { return prestamos; }
    }
}