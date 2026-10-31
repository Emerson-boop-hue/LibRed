package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Método de pago (tarjeta) de una librería para su membresía. Hay UNA tarjeta por librería.
 *
 * Por seguridad NO se guarda el número completo ni el CVV: solo la marca, los últimos 4 dígitos,
 * el titular y el vencimiento (lo mismo que muestran las tiendas en línea).
 */
@Entity
@Table(name = "tarjeta_pago")
public class TarjetaPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "libreria_id", nullable = false, unique = true)
    private Integer libreriaId;

    @Column(nullable = false, length = 100)
    private String titular;

    // VISA, MASTERCARD o TARJETA (si no se reconoce la marca)
    @Column(nullable = false, length = 20)
    private String marca;

    @Column(name = "ultimos_digitos", nullable = false, length = 4)
    private String ultimosDigitos;

    @Column(name = "vence_mes", nullable = false)
    private Integer venceMes;

    @Column(name = "vence_anio", nullable = false)
    private Integer venceAnio;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    protected void alGuardar() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    public TarjetaPago() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public String getUltimosDigitos() { return ultimosDigitos; }
    public void setUltimosDigitos(String ultimosDigitos) { this.ultimosDigitos = ultimosDigitos; }

    public Integer getVenceMes() { return venceMes; }
    public void setVenceMes(Integer venceMes) { this.venceMes = venceMes; }

    public Integer getVenceAnio() { return venceAnio; }
    public void setVenceAnio(Integer venceAnio) { this.venceAnio = venceAnio; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    /** Vencimiento en formato MM/AA (para mostrar en pantalla). */
    @Transient
    public String getVencimientoTexto() {
        if (venceMes == null || venceAnio == null) {
            return "";
        }
        return String.format("%02d/%02d", venceMes, venceAnio % 100);
    }
}