package com.libred.sistema.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devolucion")
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    @ManyToOne
    @JoinColumn(name = "prestamo_id", nullable = false)
    private Prestamo prestamo;

    @Column(name = "dias_retraso")
    private Integer diasRetraso = 0;

    @Column(name = "monto_mora", precision = 10, scale = 2)
    private BigDecimal montoMora = BigDecimal.ZERO;

    @Column(name = "monto_multa", precision = 10, scale = 2)
    private BigDecimal montoMulta = BigDecimal.ZERO;

    @Column(name = "monto_total", precision = 10, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "fecha_devolucion", insertable = false, updatable = false)
    private LocalDateTime fechaDevolucion;

    public Devolucion() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public Prestamo getPrestamo() { return prestamo; }
    public void setPrestamo(Prestamo prestamo) { this.prestamo = prestamo; }

    public Integer getDiasRetraso() { return diasRetraso; }
    public void setDiasRetraso(Integer diasRetraso) { this.diasRetraso = diasRetraso; }

    public BigDecimal getMontoMora() { return montoMora; }
    public void setMontoMora(BigDecimal montoMora) { this.montoMora = montoMora; }

    public BigDecimal getMontoMulta() { return montoMulta; }
    public void setMontoMulta(BigDecimal montoMulta) { this.montoMulta = montoMulta; }

    public BigDecimal getMontoTotal() { return montoTotal; }
    public void setMontoTotal(BigDecimal montoTotal) { this.montoTotal = montoTotal; }

    public LocalDateTime getFechaDevolucion() { return fechaDevolucion; }
    public void setFechaDevolucion(LocalDateTime fechaDevolucion) { this.fechaDevolucion = fechaDevolucion; }
}
