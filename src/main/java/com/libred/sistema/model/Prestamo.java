package com.libred.sistema.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * Préstamo
 * Entidad que representa un préstamo de un recurso a un lector en la librería, incluyendo información sobre fechas, estado y tarifas.
 * Contiene detalles sobre el recurso prestado, el lector que lo recibe, las fechas de
 * devolución y el estado del préstamo.
 */
@Entity
@Table(name = "prestamo")
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    @ManyToOne
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

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

    @Column(name = "dias_prestamo")
    private Integer diasPrestamo;

    @Column(name = "tarifa_dia", precision = 8, scale = 2)
    private BigDecimal tarifaDia;

    @Column(name = "monto_servicio", precision = 10, scale = 2)
    private BigDecimal montoServicio;

    @Column(name = "servicio_cobrado")
    private boolean servicioCobrado = false;

    @Column(name = "fecha_cobro_servicio")
    private LocalDateTime fechaCobroServicio;

    @Column(name = "origen", length = 20)
    private String origen = "ADMIN";

    public boolean isServicioCobrado() { return servicioCobrado; }
    public void setServicioCobrado(boolean servicioCobrado) { this.servicioCobrado = servicioCobrado; }

    public LocalDateTime getFechaCobroServicio() { return fechaCobroServicio; }
    public void setFechaCobroServicio(LocalDateTime fechaCobroServicio) { this.fechaCobroServicio = fechaCobroServicio; }

    @PrePersist
    public void prePersist() {
        if (fechaPrestamo == null) {
            fechaPrestamo = LocalDateTime.now();
        }
        if (estadoPrestamo == null) {
            estadoPrestamo = "ACTIVO";
        }
    }

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

    public Integer getDiasPrestamo() {
        return diasPrestamo;
    }

    public void setDiasPrestamo(Integer diasPrestamo) {
        this.diasPrestamo = diasPrestamo;
    }

    public BigDecimal getTarifaDia() {
        return tarifaDia == null ? BigDecimal.ZERO.setScale(2) : tarifaDia;
    }

    public void setTarifaDia(BigDecimal tarifaDia) {
        this.tarifaDia = tarifaDia;
    }

    public BigDecimal getMontoServicio() {
        return montoServicio == null ? BigDecimal.ZERO.setScale(2) : montoServicio;
    }

    public void setMontoServicio(BigDecimal montoServicio) {
        this.montoServicio = montoServicio;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }
}