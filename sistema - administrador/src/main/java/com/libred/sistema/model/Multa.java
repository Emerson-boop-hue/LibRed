package com.libred.sistema.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "multa")
public class Multa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "libreria_id", nullable = false)
    private Long libreriaId;

    @ManyToOne
    @JoinColumn(name = "prestamo_id", nullable = false)
    private Prestamo prestamo;

    @ManyToOne
    @JoinColumn(name = "lector_id", nullable = false)
    private Lector lector;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private String motivo; 

    @Column(name = "estado_pago", nullable = false)
    private String estadoPago = "PENDIENTE";

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "fecha_emision", insertable = false, updatable = false)
    private LocalDateTime fechaEmision;

    public Multa() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Long libreriaId) { this.libreriaId = libreriaId; }

    public Prestamo getPrestamo() { return prestamo; }
    public void setPrestamo(Prestamo prestamo) { this.prestamo = prestamo; }

    public Lector getLector() { return lector; }
    public void setLector(Lector lector) { this.lector = lector; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }

    public LocalDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(LocalDateTime fechaPago) { this.fechaPago = fechaPago; }

    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime fechaEmision) { this.fechaEmision = fechaEmision; }
}