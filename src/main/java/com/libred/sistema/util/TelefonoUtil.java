package com.libred.sistema.util;

/**
 * Teléfonos de lectores: en la base de datos se guardan SOLO con dígitos ("945612378") y se muestran
 * siempre con el mismo formato ("945 612 378"), vengan del registro manual, del login o de la Red Global.
 */
public final class TelefonoUtil {

    private TelefonoUtil() {
    }

    /** Deja solo los dígitos. Devuelve null si el teléfono está vacío o no tiene ningún dígito. */
    public static String normalizar(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** 9 dígitos -> "945 612 378". Cualquier otro largo se muestra tal cual (solo dígitos). Null si no hay teléfono. */
    public static String formatear(String telefono) {
        String d = normalizar(telefono);
        if (d == null) {
            return null;
        }
        if (d.length() == 9) {
            return d.substring(0, 3) + " " + d.substring(3, 6) + " " + d.substring(6);
        }
        return d;
    }
}