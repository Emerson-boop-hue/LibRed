package com.libred.sistema.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Solicitud de soporte que el administrador de una librería envía desde "Ayuda y Soporte". estado: ABIERTO (recién
 * enviada) o RESUELTO (ya tiene respuesta).
 */
@Entity
@Table(name = "ticket_soporte")
public class TicketSoporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false)
    private Integer libreriaId;

    // ERROR, DUDA, SUGERENCIA o PLAN
    @Column(nullable = false, length = 20)
    private String categoria;

    @Column(nullable = false, length = 120)
    private String asunto;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Column(nullable = false, length = 20)
    private String estado = "ABIERTO";

    @Column(length = 1000)
    private String respuesta;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    public TicketSoporte() {
    }

    public Integer getId() { return id; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime fechaRespuesta) { this.fechaRespuesta = fechaRespuesta; }
}