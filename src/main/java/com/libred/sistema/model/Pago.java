package com.libred.sistema.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cada intento de pago de una membresía (aprobado o rechazado). Es el historial de cobros de LibRed. */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "registro_id")
    private Integer registroId;

    // Se completa cuando la librería ya fue creada
    @Column(name = "libreria_id")
    private Integer libreriaId;

    @Column(name = "medio_id")
    private Integer medioId;

    @Column(nullable = false, length = 120)
    private String correo;

    @Column(nullable = false, length = 20)
    private String plan;

    // Monto del plan en soles
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    // Lo que realmente se cobró (en dólares si fue PayPal)
    @Column(name = "monto_cobrado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoCobrado;

    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    // TARJETA_CREDITO, TARJETA_DEBITO, YAPE o PAYPAL
    @Column(nullable = false, length = 20)
    private String metodo;

    @Column(length = 80)
    private String referencia;

    // APROBADO o RECHAZADO
    @Column(nullable = false, length = 20)
    private String estado;

    @Column(length = 120)
    private String motivo;

    @Column(name = "codigo_autorizacion", length = 20)
    private String codigoAutorizacion;

    @Column(name = "numero_operacion", length = 20)
    private String numeroOperacion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // De dónde viene el cobro: REGISTRO (alta de la librería), MANUAL (el administrador pagó/renovó) o AUTOMATICO
    // (renovación sola)
    @Column(nullable = false, length = 12, columnDefinition = "VARCHAR(12) NOT NULL DEFAULT 'REGISTRO'")
    private String origen = "REGISTRO";

    public Pago() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getRegistroId() { return registroId; }
    public void setRegistroId(Integer registroId) { this.registroId = registroId; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public Integer getMedioId() { return medioId; }
    public void setMedioId(Integer medioId) { this.medioId = medioId; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public BigDecimal getMontoCobrado() { return montoCobrado; }
    public void setMontoCobrado(BigDecimal montoCobrado) { this.montoCobrado = montoCobrado; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }

    public String getNumeroOperacion() { return numeroOperacion; }
    public void setNumeroOperacion(String numeroOperacion) { this.numeroOperacion = numeroOperacion; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
}