package com.libred.sistema.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Tarifa de servicio por día de UNA categoría de artículo en UNA librería.
 * Cada librería configura sus propias tarifas (Ajustes del administrador > Política de préstamos).
 * Si una categoría no tiene fila, su tarifa es S/ 0.00 (servicio gratuito).
 */
@Entity
@Table(name = "tarifa_categoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_tarifa_libreria_categoria", columnNames = {"libreria_id", "categoria"}))
public class TarifaCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    // Mismo valor que recurso.tipo_recurso (LIBROS, NOVELAS LIGERAS, ...)
    @Column(nullable = false, length = 50)
    private String categoria;

    @Column(name = "tarifa_dia", nullable = false, precision = 8, scale = 2)
    private BigDecimal tarifaDia = BigDecimal.ZERO;

    // true = la librería usa esta categoría (aparece en inventario, catálogo y préstamos)
    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean activa = true;

    public TarifaCategoria() {
    }

    public TarifaCategoria(Integer libreriaId, String categoria, BigDecimal tarifaDia) {
        this.libreriaId = libreriaId;
        this.categoria = categoria;
        this.tarifaDia = tarifaDia;
        this.activa = true;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public BigDecimal getTarifaDia() { return tarifaDia; }
    public void setTarifaDia(BigDecimal tarifaDia) { this.tarifaDia = tarifaDia; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}