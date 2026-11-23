package com.libred.sistema.model;

/**
 * Datos que envía el asistente de registro (paso 1 y paso 3).
 * Los datos de la tarjeta (paso 2) NO se envían ni se guardan: el pago es simulado y se valida solo en el navegador.
 */
public class RegistroSuscripcionRequest {

    private String tipoPlan;
    private String nombreComercial;
    private String ruc;
    private String direccion;
    private String password;

    public RegistroSuscripcionRequest() {
    }

    public String getTipoPlan() {
        return tipoPlan;
    }

    public void setTipoPlan(String tipoPlan) {
        this.tipoPlan = tipoPlan;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}