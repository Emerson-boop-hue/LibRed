package com.libred.sistema.service;

/**
 * Error de validación del módulo de Ajustes (lector y administrador).
 * "campo" indica el campo del formulario al que se refiere el error (puede ser null);
 * el controlador lo convierte en JSON para que la pantalla marque ese campo.
 */
public class AjustesException extends RuntimeException {

    private final String campo;

    public AjustesException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}