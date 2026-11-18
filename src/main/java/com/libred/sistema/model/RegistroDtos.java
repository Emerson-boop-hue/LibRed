package com.libred.sistema.model;

/** Datos que envía la página de registro (JSON). */
public final class RegistroDtos {

    private RegistroDtos() {
    }

    public record SolicitudCorreo(String correo, Boolean acepta) {}

    public record SolicitudVerificar(String correo, String codigo, Boolean acepta) {}

    public record SolicitudPaypal(String token, String plan, String correo, String clave) {}

    public record SolicitudYape(String token, String celular) {}

    /** metodo: TARJETA, YAPE o PAYPAL. Solo se usan los campos del método elegido. */
    public record SolicitudPago(String token, String plan, String metodo,
                                String numero, String titular, String vence, String cvv,
                                String celular, String codigo,
                                String correoPaypal, String clavePaypal) {}

    public record SolicitudFinalizar(String token, String nombreComercial, String ruc, String direccion, String password,
                                     String telefono, String correoContacto, String descripcion, String horario) {}

    /**
     * Pago de una librería que ya existe (renovar o pasar al plan anual). automatica: renovar sola (solo tarjeta y
     * PayPal).
     */
    public record SolicitudRenovar(String plan, String metodo,
                                   String numero, String titular, String vence, String cvv,
                                   String celular, String codigo,
                                   String correoPaypal, String clavePaypal, Boolean automatica) {}

    public record SolicitudRecuperarCodigo(String correo) {}

    // Lectores: registro propio, recuperación por celular y cambio obligatorio de contraseña
    public record SolicitudRegistroLector(String nombres, String apellidos, String celular, Integer libreriaId, String password) {}

    public record SolicitudCelular(String celular) {}

    public record SolicitudVerificarLector(String celular, String codigo) {}

    public record SolicitudRestablecerLector(String celular, String codigo, String codigoLector, String password) {}

    public record SolicitudRestablecer(String correo, String codigo, String password) {}
}