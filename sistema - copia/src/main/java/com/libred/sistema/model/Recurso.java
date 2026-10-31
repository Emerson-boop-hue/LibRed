package com.libred.sistema.model;

import jakarta.persistence.*;

@Entity
@Table(name = "recurso")
public class Recurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Relación con la tabla librería (Multi-inquilino)
    @ManyToOne
    @JoinColumn(name = "libreria_id", nullable = false)
    private Libreria libreria;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(name = "autor_o_creador", length = 150)
    private String autorOCreador;

    @Column(name = "tipo_recurso", nullable = false, length = 50)
    private String tipoRecurso; 

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(name = "stock_total", nullable = false)
    private Integer stockTotal = 0;

    @Column(name = "stock_disponible", nullable = false)
    private Integer stockDisponible = 0;

    @Column(length = 50)
    private String estado = "DISPONIBLE";

    // ==========================================
    // GETTERS Y SETTERS
    // ==========================================

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Libreria getLibreria() {
        return libreria;
    }

    public void setLibreria(Libreria libreria) {
        this.libreria = libreria;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutorOCreador() {
        return autorOCreador;
    }

    public void setAutorOCreador(String autorOCreador) {
        this.autorOCreador = autorOCreador;
    }

    public String getTipoRecurso() {
        return tipoRecurso;
    }

    public void setTipoRecurso(String tipoRecurso) {
        this.tipoRecurso = tipoRecurso;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Integer getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(Integer stockTotal) {
        this.stockTotal = stockTotal;
    }

    public Integer getStockDisponible() {
        return stockDisponible;
    }

    public void setStockDisponible(Integer stockDisponible) {
        this.stockDisponible = stockDisponible;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}