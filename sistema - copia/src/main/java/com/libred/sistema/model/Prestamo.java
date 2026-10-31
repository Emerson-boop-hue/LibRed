package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamo")
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    // Relación con Recurso (Inventario)
    @ManyToOne
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    // Relación con Lector
    @ManyToOne
    @JoinColumn(name = "lector_id", nullable = false)
    private Lector lector;

    @Column(name = "fecha_prestamo", updatable = false)
    private LocalDateTime fechaPrestamo;

    @Column(name = "fecha_devolucion_limite", nullable = false)
    private LocalDate fechaDevolucionLimite;

    @Column(name = "fecha_devolucion_real")
    private LocalDateTime fechaDevolucionReal;

    @Column(name = "estado_prestamo", length = 50)
    private String estadoPrestamo = "ACTIVO"; // ACTIVO, DEVUELTO, VENCIDO

    @PrePersist
    public void prePersist() {
        if (fechaPrestamo == null) {
            fechaPrestamo = LocalDateTime.now();
        }
        if (estadoPrestamo == null) {
            estadoPrestamo = "ACTIVO";
        }
    }

    // ==========================================
    // GETTERS Y SETTERS
    // ==========================================

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getLibreriaId() {
        return libreriaId;
    }

    public void setLibreriaId(Integer libreriaId) {
        this.libreriaId = libreriaId;
    }

    public Recurso getRecurso() {
        return recurso;
    }

    public void setRecurso(Recurso recurso) {
        this.recurso = recurso;
    }

    public Lector getLector() {
        return lector;
    }

    public void setLector(Lector lector) {
        this.lector = lector;
    }

    public LocalDateTime getFechaPrestamo() {
        return fechaPrestamo;
    }

    public void setFechaPrestamo(LocalDateTime fechaPrestamo) {
        this.fechaPrestamo = fechaPrestamo;
    }

    public LocalDate getFechaDevolucionLimite() {
        return fechaDevolucionLimite;
    }

    public void setFechaDevolucionLimite(LocalDate fechaDevolucionLimite) {
        this.fechaDevolucionLimite = fechaDevolucionLimite;
    }

    public LocalDateTime getFechaDevolucionReal() {
        return fechaDevolucionReal;
    }

    public void setFechaDevolucionReal(LocalDateTime fechaDevolucionReal) {
        this.fechaDevolucionReal = fechaDevolucionReal;
    }

    public String getEstadoPrestamo() {
        return estadoPrestamo;
    }

    public void setEstadoPrestamo(String estadoPrestamo) {
        this.estadoPrestamo = estadoPrestamo;
    }
}
