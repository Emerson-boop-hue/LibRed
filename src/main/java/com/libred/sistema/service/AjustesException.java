package com.libred.sistema.service;

/** Error de validación del módulo de Ajustes (lector y administrador). */
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