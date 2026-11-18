package com.libred.sistema.service;

import com.libred.sistema.util.TelefonoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Envío de SMS a los lectores (bienvenida con su código y código de recuperación de acceso). */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String REMITENTE = "LibRed";

    private final String modo;

    public SmsService(@Value("${libred.sms.modo:simulado}") String modo) {
        this.modo = modo == null ? "simulado" : modo.trim().toLowerCase();
    }

    public boolean simulado() {
        return "simulado".equals(modo);
    }

    /** Envía (o simula) un SMS. */
    public Optional<Map<String, Object>> enviar(String telefono, String texto, String codigo) {
        String destino = TelefonoUtil.formatear(telefono);
        if (!simulado()) {
            log.warn("[SMS] Modo '{}' sin proveedor configurado: no se envió el mensaje a {}.", modo, destino);
            return Optional.empty();
        }
        log.info("[SMS simulado] Para {}: {}", destino, texto);

        Map<String, Object> sms = new LinkedHashMap<>();
        sms.put("remitente", REMITENTE);
        sms.put("texto", texto);
        if (codigo != null) {
            sms.put("codigo", codigo);
        }
        return Optional.of(sms);
    }
}