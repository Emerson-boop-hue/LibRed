package com.libred.sistema.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * "Banco de pruebas" de LibRed: tarjetas, cuentas Yape y cuentas PayPal INVENTADAS con las que se puede pagar la
 * membresía sin dinero real.
 */
@Entity
@Table(name = "medio_pago_prueba")
public class MedioPagoPrueba {

    public static final String TARJETA_CREDITO = "TARJETA_CREDITO";
    public static final String TARJETA_DEBITO = "TARJETA_DEBITO";
    public static final String YAPE = "YAPE";
    public static final String PAYPAL = "PAYPAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // TARJETA_CREDITO, TARJETA_DEBITO, YAPE o PAYPAL
    @Column(nullable = false, length = 20)
    private String tipo;

    // Número de tarjeta (sin espacios), celular de Yape o correo de PayPal
    @Column(nullable = false, unique = true, length = 60)
    private String identificador;

    @Column(nullable = false, length = 100)
    private String titular;

    @Column(length = 20)
    private String marca;

    @Column(name = "vence_mes")
    private Integer venceMes;

    @Column(name = "vence_anio")
    private Integer venceAnio;

    @Column(length = 4)
    private String cvv;

    // Solo PayPal (contraseña de la cuenta de prueba)
    @Column(length = 40)
    private String clave;

    // Dinero disponible (tarjeta de débito, Yape), línea disponible (crédito) o dólares (PayPal)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    // PEN o USD
    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    // ACTIVO, BLOQUEADO (tarjeta bloqueada / cuenta suspendida) o RECHAZADO (el banco rechaza toda operación)
    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    public MedioPagoPrueba() {
    }

    public boolean esTarjeta() {
        return TARJETA_CREDITO.equals(tipo) || TARJETA_DEBITO.equals(tipo);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public Integer getVenceMes() { return venceMes; }
    public void setVenceMes(Integer venceMes) { this.venceMes = venceMes; }

    public Integer getVenceAnio() { return venceAnio; }
    public void setVenceAnio(Integer venceAnio) { this.venceAnio = venceAnio; }

    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}