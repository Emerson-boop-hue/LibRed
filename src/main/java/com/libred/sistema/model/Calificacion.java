package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Calificación (1 a 5 estrellas) que un lector da a un PRÉSTAMO ya devuelto.
 * Hay a lo sumo una por préstamo (si el lector la cambia, se reemplaza). El promedio y los votos de una
 * librería salen de todas las calificaciones de sus préstamos.
 */
@Entity
@Table(name = "calificacion",
        uniqueConstraints = @UniqueConstraint(name = "uk_calificacion_prestamo", columnNames = {"prestamo_id"}))
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    @Column(name = "prestamo_id", nullable = false)
    private Integer prestamoId;

    @Column(name = "lector_id", nullable = false)
    private Integer lectorId;

    @Column(nullable = false)
    private Integer estrellas;

    // true = el lector ya usó su única modificación (después de eso la calificación queda fija)
    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean modificada = false;

    @Column(name = "fecha_calificacion")
    private LocalDateTime fechaCalificacion;

    public Calificacion() {
    }

    @PrePersist
    @PreUpdate
    protected void alGuardar() {
        this.fechaCalificacion = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public Integer getPrestamoId() { return prestamoId; }
    public void setPrestamoId(Integer prestamoId) { this.prestamoId = prestamoId; }

    public Integer getLectorId() { return lectorId; }
    public void setLectorId(Integer lectorId) { this.lectorId = lectorId; }

    public Integer getEstrellas() { return estrellas; }
    public void setEstrellas(Integer estrellas) { this.estrellas = estrellas; }

    public boolean isModificada() { return modificada; }
    public void setModificada(boolean modificada) { this.modificada = modificada; }

    public LocalDateTime getFechaCalificacion() { return fechaCalificacion; }
    public void setFechaCalificacion(LocalDateTime fechaCalificacion) { this.fechaCalificacion = fechaCalificacion; }
}