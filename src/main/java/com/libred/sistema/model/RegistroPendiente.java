package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de una librería nueva que todavía no termina el proceso. Se crea SOLO cuando el correo ya fue verificado
 * con el código. Estados:
 */
@Entity
@Table(name = "registro_pendiente")
public class RegistroPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(nullable = false, length = 120)
    private String correo;

    @Column(length = 20)
    private String plan;

    @Column(nullable = false, length = 20)
    private String estado = "INICIADO";

    @Column(name = "terminos_aceptados_en")
    private LocalDateTime terminosAceptadosEn;

    @Column(name = "pago_id")
    private Integer pagoId;

    @Column(name = "libreria_id")
    private Integer libreriaId;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void alCrear() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = this.fechaCreacion;
    }

    @PreUpdate
    protected void alActualizar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    public RegistroPendiente() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getTerminosAceptadosEn() { return terminosAceptadosEn; }
    public void setTerminosAceptadosEn(LocalDateTime terminosAceptadosEn) { this.terminosAceptadosEn = terminosAceptadosEn; }

    public Integer getPagoId() { return pagoId; }
    public void setPagoId(Integer pagoId) { this.pagoId = pagoId; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}