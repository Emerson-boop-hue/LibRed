package com.libred.sistema.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/** Envío de correos HTML del sistema (códigos de verificación, recuperación de acceso y comprobantes). */
@Service
public class CorreoSistemaService {

    private static final Logger log = LoggerFactory.getLogger(CorreoSistemaService.class);

    public static class CorreoNoEnviadoException extends RuntimeException {
        public CorreoNoEnviadoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    @Value("${libred.soporte.correo:}")
    private String correo;
    @Value("${libred.soporte.clave:}")
    private String clave;

    private final ObjectProvider<JavaMailSender> mailSender;

    public CorreoSistemaService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean disponible() {
        return correo != null && !correo.isBlank() && clave != null && !clave.isBlank()
                && mailSender.getIfAvailable() != null;
    }

    /** Envía y espera el resultado: si falla lanza CorreoNoEnviadoException. */
    public void enviar(String para, String asunto, String html) {
        enviar(para, asunto, html, null, null);
    }

    /**
     * Igual que enviar, pero con una dirección de respuesta (Reply-To) y un nombre visible para el remitente. Sirve
     * para el formulario de contacto: al pulsar "Responder", el correo va a la persona que escribió. Ambos son
     * opcionales (null o vacío = sin cambios).
     */
    public void enviar(String para, String asunto, String html, String responderA, String nombreRemitente) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || !disponible()) {
            throw new CorreoNoEnviadoException("El envío de correos no está configurado.", null);
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(correo, nombreRemitente == null || nombreRemitente.isBlank() ? "LibRed" : nombreRemitente);
            if (responderA != null && !responderA.isBlank()) {
                helper.setReplyTo(responderA, nombreRemitente == null ? "" : nombreRemitente);
            }
            helper.setTo(para);
            helper.setSubject(asunto);
            helper.setText(html, true);
            sender.send(mensaje);
        } catch (Exception e) {
            throw new CorreoNoEnviadoException("No se pudo enviar el correo a " + para + ": " + e.getMessage(), e);
        }
    }

    public enum Resultado { ENVIADO, EN_CURSO, FALLO }

    /**
     * Envía el correo y espera como máximo "maxSegundos" a que Gmail confirme. ENVIADO: Gmail lo aceptó. EN_CURSO:
     * sigue enviándose (no es un error:
     */
    public Resultado enviarYEsperar(String para, String asunto, String html, int maxSegundos) {
        return enviarYEsperar(para, asunto, html, null, null, maxSegundos);
    }

    /** Variante con Reply-To y nombre de remitente (ver enviar). */
    public Resultado enviarYEsperar(String para, String asunto, String html, String responderA, String nombreRemitente,
                                    int maxSegundos) {
        if (!disponible()) {
            return Resultado.FALLO;
        }
        CompletableFuture<Void> envio = CompletableFuture.runAsync(() -> enviar(para, asunto, html, responderA, nombreRemitente));
        envio.whenComplete((v, e) -> {
            if (e != null) log.warn("{}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        });
        try {
            envio.get(maxSegundos, java.util.concurrent.TimeUnit.SECONDS);
            return Resultado.ENVIADO;
        } catch (java.util.concurrent.TimeoutException e) {
            return Resultado.EN_CURSO;
        } catch (java.util.concurrent.ExecutionException e) {
            return Resultado.FALLO;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Resultado.EN_CURSO;
        }
    }

    /** Envía sin hacer esperar al usuario (comprobantes). Si falla solo se anota en el registro. */
    public void enviarEnSegundoPlano(String para, String asunto, String html) {
        if (!disponible()) {
            log.info("Correo '{}' para {} no enviado: libred.soporte.clave está vacía.", asunto, para);
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                enviar(para, asunto, html);
                log.info("Correo '{}' enviado a {}.", asunto, para);
            } catch (Exception e) {
                log.warn("{}", e.getMessage());
            }
        });
    }
}