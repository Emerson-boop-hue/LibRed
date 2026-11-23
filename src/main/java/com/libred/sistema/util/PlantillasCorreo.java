package com.libred.sistema.util;

import org.springframework.web.util.HtmlUtils;

/** Correos HTML de LibRed (código de verificación, recuperación de acceso y comprobante de pago). */
public final class PlantillasCorreo {

    private PlantillasCorreo() {
    }

    private static String e(String texto) {
        return HtmlUtils.htmlEscape(texto == null ? "" : texto);
    }

    private static String marco(String titulo, String contenido) {
        return "<!DOCTYPE html><html lang=\"es\"><body style=\"margin:0;padding:0;background:#f1f5f9;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f1f5f9;padding:28px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:480px;width:100%;background:#ffffff;border-radius:14px;border:1px solid #e2e8f0;\">"
                + "<tr><td style=\"padding:26px 32px 0;font-family:Arial,Helvetica,sans-serif;font-size:20px;font-weight:700;color:#1e293b;\">Lib<span style=\"color:#2563eb;\">Red</span></td></tr>"
                + "<tr><td style=\"padding:18px 32px 30px;font-family:Arial,Helvetica,sans-serif;color:#334155;font-size:14px;line-height:1.6;\">"
                + "<h1 style=\"margin:0 0 10px;font-size:19px;color:#0f172a;\">" + e(titulo) + "</h1>"
                + contenido
                + "</td></tr></table>"
                + "<p style=\"font-family:Arial,Helvetica,sans-serif;font-size:11.5px;color:#94a3b8;margin:14px 0 0;\">Este mensaje se envió automáticamente. Por favor no respondas a este correo.</p>"
                + "</td></tr></table></body></html>";
    }

    /** Correo con el código de 6 dígitos (registro o recuperación). */
    public static String codigo(String titulo, String intro, String codigo, int minutos, String aviso) {
        String partido = codigo.length() == 6 ? codigo.substring(0, 3) + " " + codigo.substring(3) : codigo;
        return marco(titulo,
                "<p style=\"margin:0 0 18px;\">" + e(intro) + "</p>"
                        + "<div style=\"background:#f1f5f9;border-radius:10px;padding:16px;text-align:center;font-size:32px;font-weight:700;letter-spacing:7px;color:#0f172a;\">" + e(partido) + "</div>"
                        + "<p style=\"margin:18px 0 0;\">El código vence en <strong>" + minutos + " minutos</strong> y solo sirve una vez.</p>"
                        + "<p style=\"margin:10px 0 0;color:#64748b;font-size:12.5px;\">" + e(aviso) + "</p>");
    }

    /** Correo de recuperación: además del código recuerda el código de administrador. */
    public static String recuperacion(String codigo, String codigoAdmin, String libreria, int minutos) {
        String partido = codigo.substring(0, 3) + " " + codigo.substring(3);
        return marco("Recupera tu acceso",
                "<p style=\"margin:0 0 6px;\">Pediste restablecer la contraseña de <strong>" + e(libreria) + "</strong>.</p>"
                        + "<p style=\"margin:0 0 18px;\">Tu código de administrador es <strong>" + e(codigoAdmin) + "</strong>.</p>"
                        + "<div style=\"background:#f1f5f9;border-radius:10px;padding:16px;text-align:center;font-size:32px;font-weight:700;letter-spacing:7px;color:#0f172a;\">" + e(partido) + "</div>"
                        + "<p style=\"margin:18px 0 0;\">El código vence en <strong>" + minutos + " minutos</strong> y solo sirve una vez.</p>"
                        + "<p style=\"margin:10px 0 0;color:#64748b;font-size:12.5px;\">Si no fuiste tú, ignora este mensaje: tu contraseña no cambiará.</p>");
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr><td style=\"padding:7px 0;color:#64748b;border-bottom:1px solid #f1f5f9;\">" + e(etiqueta)
                + "</td><td style=\"padding:7px 0;text-align:right;font-weight:600;color:#0f172a;border-bottom:1px solid #f1f5f9;\">" + e(valor) + "</td></tr>";
    }

    /** Comprobante de pago + datos de acceso de la librería nueva. */
    public static String comprobante(String libreria, String codigoAdmin, String plan, String monto, String metodo,
                                     String operacion, String fecha, String vence) {
        return marco("¡Tu membresía está activa!",
                "<p style=\"margin:0 0 16px;\">Gracias por unirte a LibRed. Este es el comprobante de tu pago y los datos para ingresar al panel administrativo.</p>"
                        + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"font-size:13.5px;\">"
                        + fila("Librería", libreria)
                        + fila("Código de administrador", codigoAdmin)
                        + fila("Plan", plan)
                        + fila("Total pagado", monto)
                        + fila("Método de pago", metodo)
                        + fila("N.º de operación", operacion)
                        + fila("Fecha", fecha)
                        + fila("Vigente hasta", vence)
                        + "</table>"
                        + "<p style=\"margin:18px 0 0;color:#64748b;font-size:12.5px;\">Ingresa con tu código de administrador y la contraseña que creaste. Guarda este correo como constancia de tu pago.</p>");
    }

    /** Comprobante de una renovación o cambio de plan (automático o manual). */
    public static String comprobanteRenovacion(String libreria, String plan, String monto, String metodo, String operacion,
                                               String fecha, String vence, boolean automatica) {
        return marco(automatica ? "Renovamos tu membresía" : "Recibimos tu pago",
                "<p style=\"margin:0 0 16px;\">" + (automatica
                        ? "Tu membresía de <strong>" + e(libreria) + "</strong> se renovó automáticamente. Este es el comprobante del cobro."
                        : "Gracias. Este es el comprobante del pago de la membresía de <strong>" + e(libreria) + "</strong>.") + "</p>"
                        + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"font-size:13.5px;\">"
                        + fila("Plan", plan)
                        + fila("Total pagado", monto)
                        + fila("Método de pago", metodo)
                        + fila("N.º de operación", operacion)
                        + fila("Fecha", fecha)
                        + fila("Vigente hasta", vence)
                        + "</table>"
                        + (automatica
                        ? "<p style=\"margin:18px 0 0;color:#64748b;font-size:12.5px;\">Puedes cancelar la renovación automática cuando quieras desde «Ver plan actual» en tu panel.</p>"
                        : ""));
    }

    /** No se pudo cobrar la renovación automática. */
    public static String cobroFallido(String libreria, String plan, String metodo, String motivo, String vence, boolean desactivada) {
        return marco("No pudimos renovar tu membresía",
                "<p style=\"margin:0 0 14px;\">Intentamos cobrar el <strong>" + e(plan) + "</strong> de <strong>" + e(libreria)
                        + "</strong> con <strong>" + e(metodo) + "</strong>, pero el cobro fue rechazado.</p>"
                        + "<p style=\"margin:0 0 14px;padding:10px 12px;background:#fef2f2;border-radius:8px;color:#991b1b;\">" + e(motivo) + "</p>"
                        + (desactivada
                        ? "<p style=\"margin:0;\">Después de varios intentos desactivamos la renovación automática. Entra a <strong>Ver plan actual</strong> en tu panel y renueva con otro método para conservar tu acceso.</p>"
                        : "<p style=\"margin:0;\">Volveremos a intentarlo mañana. Si prefieres, entra a <strong>Ver plan actual</strong> y paga con otro método.</p>")
                        + "<p style=\"margin:12px 0 0;color:#64748b;font-size:12.5px;\">Fecha de vencimiento: " + e(vence) + "</p>");
    }

    /** Aviso previo para quienes renuevan a mano (Yape) o cancelaron la renovación automática. */
    public static String avisoVencimiento(String libreria, String plan, String vence, int dias) {
        return marco("Tu membresía vence pronto",
                "<p style=\"margin:0 0 14px;\">La membresía de <strong>" + e(libreria) + "</strong> (" + e(plan) + ") vence el <strong>" + e(vence)
                        + "</strong>" + (dias <= 1 ? " (mañana)." : " (en " + dias + " días).") + "</p>"
                        + "<p style=\"margin:0;\">No tiene renovación automática. Entra a <strong>Ver plan actual</strong> en tu panel y renuévala para que tus lectores sigan encontrando tu catálogo.</p>");
    }
}