package com.libred.sistema.model;

import java.math.BigDecimal;

/**
 * Planes de membresía de LibRed. Es la ÚNICA fuente de precios y duración:
 * la pantalla de registro y el modal "Ver plan actual" leen estos valores desde el servidor.
 * Para cambiar un precio o la duración, se modifica aquí y nada más.
 */
public enum PlanSuscripcion {

    MENSUAL("Plan Mensual", new BigDecimal("29.00"), 30, "por mes"),
    ANUAL("Plan Anual", new BigDecimal("290.00"), 365, "por año");

    private final String nombre;
    private final BigDecimal precio;
    private final int dias;
    private final String periodo;

    PlanSuscripcion(String nombre, BigDecimal precio, int dias, String periodo) {
        this.nombre = nombre;
        this.precio = precio;
        this.dias = dias;
        this.periodo = periodo;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public int getDias() {
        return dias;
    }

    public String getPeriodo() {
        return periodo;
    }

    /** Devuelve el plan que corresponde al texto ("MENSUAL"/"ANUAL") o null si no existe. */
    public static PlanSuscripcion desdeCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        for (PlanSuscripcion plan : values()) {
            if (plan.name().equalsIgnoreCase(codigo.trim())) {
                return plan;
            }
        }
        return null;
    }
}