package com.libred.sistema.model;

/**
 * Datos de un artículo ya preparados para mostrarse en una tarjeta del catálogo del lector.
 * (No es una tabla: es solo un objeto para la vista. Usa getters clásicos para que Thymeleaf lo lea sin problemas.)
 *
 * nivel: "ok" (hay stock suficiente), "poco" (últimas unidades) o "agotado".
 */
public class ArticuloCatalogo {

    private final Integer id;
    private final String titulo;
    private final String autor;
    private final String codigo;
    private final String categoria;
    private final String icono;
    private final int stockDisponible;
    private final int stockTotal;
    private final String nivel;
    private final String textoDisponibilidad;

    public ArticuloCatalogo(Integer id, String titulo, String autor, String codigo, String categoria,
                            String icono, int stockDisponible, int stockTotal, String nivel,
                            String textoDisponibilidad) {
        this.id = id;
        this.titulo = titulo;
        this.autor = autor;
        this.codigo = codigo;
        this.categoria = categoria;
        this.icono = icono;
        this.stockDisponible = stockDisponible;
        this.stockTotal = stockTotal;
        this.nivel = nivel;
        this.textoDisponibilidad = textoDisponibilidad;
    }

    public Integer getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getAutor() { return autor; }
    public String getCodigo() { return codigo; }
    public String getCategoria() { return categoria; }
    public String getIcono() { return icono; }
    public int getStockDisponible() { return stockDisponible; }
    public int getStockTotal() { return stockTotal; }
    public String getNivel() { return nivel; }
    public String getTextoDisponibilidad() { return textoDisponibilidad; }
}