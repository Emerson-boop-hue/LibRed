package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Código de un solo uso (6 dígitos aleatorios) que se envía al correo (registro, recuperación de acceso) o se muestra
 * en la "app" de Yape simulada.
 */
@Entity
@Table(name = "codigo_verificacion")
public class CodigoVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Correo (registro / recuperación) o celular (Yape) al que pertenece el código
    @Column(nullable = false, length = 120)
    private String destino;

    // REGISTRO, RECUPERACION o YAPE
    @Column(nullable = false, length = 20)
    private String proposito;

    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private int intentos = 0;

    @Column(nullable = false)
    private boolean usado = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public CodigoVerificacion() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public String getProposito() { return proposito; }
    public void setProposito(String proposito) { this.proposito = proposito; }

    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }

    public LocalDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(LocalDateTime expiraEn) { this.expiraEn = expiraEn; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public boolean isUsado() { return usado; }
    public void setUsado(boolean usado) { this.usado = usado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}