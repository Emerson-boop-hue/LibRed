package com.libred.sistema.controller;

import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.service.CorreoSistemaService;
import com.libred.sistema.service.CorreoSistemaService.Resultado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Páginas públicas del menú del login: Conócenos, Contáctenos y Ayuda. El formulario de contacto envía el mensaje por
 * correo al correo de soporte (libred.soporte.correo).
 */
@Controller
public class InfoController {

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final List<String> MOTIVOS = List.of(
            "Consulta general", "Quiero registrar mi librería", "Problema con mi cuenta", "Sugerencia", "Otro");
    /** Máximo de mensajes por dirección IP en una hora (evita abusos del formulario). */
    private static final int MAX_POR_HORA = 5;
    private static final long HORA_MS = 3_600_000L;

    @Value("${libred.soporte.correo:}")
    private String correoSoporte;
    @Value("${libred.soporte.whatsapp:}")
    private String whatsapp;

    private final CorreoSistemaService correoSistema;
    private final Map<String, Deque<Long>> envios = new ConcurrentHashMap<>();

    public InfoController(CorreoSistemaService correoSistema) {
        this.correoSistema = correoSistema;
    }

    private void comunes(Model model) {
        model.addAttribute("correoSoporte", correoSoporte == null ? "" : correoSoporte.trim());
        model.addAttribute("whatsapp", whatsapp == null ? "" : whatsapp.replaceAll("\\D", ""));
    }

    @GetMapping("/conocenos")
    public String conocenos(Model model) {
        comunes(model);
        BigDecimal mensual = PlanSuscripcion.MENSUAL.getPrecio();
        BigDecimal anual = PlanSuscripcion.ANUAL.getPrecio();
        model.addAttribute("planMensual", PlanSuscripcion.MENSUAL);
        model.addAttribute("planAnual", PlanSuscripcion.ANUAL);
        model.addAttribute("ahorroAnual", mensual.multiply(BigDecimal.valueOf(12)).subtract(anual).setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("equivalenteMensual", anual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
        return "info/conocenos";
    }

    @GetMapping("/contacto")
    public String contacto(Model model) {
        comunes(model);
        model.addAttribute("motivos", MOTIVOS);
        return "info/contacto";
    }

    @GetMapping("/ayuda")
    public String ayuda(Model model) {
        comunes(model);
        return "info/ayuda";
    }

    /** Recibe el formulario de Contáctenos. Responde JSON: {ok:true} o {ok:false, mensaje:"..."}. */
    @PostMapping("/contacto/enviar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> enviar(@RequestParam String nombre, @RequestParam String correo,
                                                      @RequestParam String motivo, @RequestParam String mensaje,
                                                      @RequestParam(required = false) String web,
                                                      HttpServletRequest request) {
        // Campo trampa (invisible para personas): si viene lleno, es un robot. Se responde "ok" sin enviar nada.
        if (web != null && !web.isBlank()) {
            return ok();
        }
        nombre = limpiar(nombre);
        correo = limpiar(correo);
        motivo = limpiar(motivo);
        mensaje = mensaje == null ? "" : mensaje.trim();

        if (nombre.length() < 2 || nombre.length() > 80) {
            return error(HttpStatus.BAD_REQUEST, "Escribe tu nombre (entre 2 y 80 caracteres).");
        }
        if (correo.length() > 120 || !CORREO.matcher(correo).matches()) {
            return error(HttpStatus.BAD_REQUEST, "Escribe un correo válido para poder responderte.");
        }
        if (!MOTIVOS.contains(motivo)) {
            return error(HttpStatus.BAD_REQUEST, "Elige un motivo de la lista.");
        }
        if (mensaje.length() < 10 || mensaje.length() > 1000) {
            return error(HttpStatus.BAD_REQUEST, "El mensaje debe tener entre 10 y 1000 caracteres.");
        }
        if (correoSoporte == null || correoSoporte.isBlank()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Por ahora no podemos recibir mensajes por aquí. Escríbenos por WhatsApp.");
        }
        if (!permitido(request.getRemoteAddr())) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Enviaste varios mensajes seguidos. Inténtalo de nuevo en un rato.");
        }

        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#0f1b3d;line-height:1.5\">"
                + "<h2 style=\"margin:0 0 12px\">Mensaje desde el sitio de LibRed</h2>"
                + "<p style=\"margin:0\"><b>Nombre:</b> " + HtmlUtils.htmlEscape(nombre) + "</p>"
                + "<p style=\"margin:0\"><b>Correo:</b> <a href=\"mailto:" + HtmlUtils.htmlEscape(correo) + "\">" + HtmlUtils.htmlEscape(correo) + "</a></p>"
                + "<p style=\"margin:0 0 12px\"><b>Motivo:</b> " + HtmlUtils.htmlEscape(motivo) + "</p>"
                + "<hr style=\"border:0;border-top:1px solid #dbe3f0\">"
                + "<p style=\"white-space:pre-wrap\">" + HtmlUtils.htmlEscape(mensaje) + "</p>"
                + "</div>";
        Resultado r = correoSistema.enviarYEsperar(correoSoporte.trim(), "[Contacto LibRed] " + motivo + " - " + nombre, html,
                correo, nombre + " (vía LibRed)", 8);
        if (r == Resultado.FALLO) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "No pudimos enviar tu mensaje ahora. Escríbenos por WhatsApp o por correo.");
        }
        return ok();
    }

    /** Una línea, sin saltos de línea (para que no se cuelen en el asunto del correo). */
    private static String limpiar(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\r\\n]+", " ").trim();
    }

    private boolean permitido(String ip) {
        long ahora = System.currentTimeMillis();
        Deque<Long> marcas = envios.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > HORA_MS) {
                marcas.pollFirst();
            }
            if (marcas.size() >= MAX_POR_HORA) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private static ResponseEntity<Map<String, Object>> ok() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        return ResponseEntity.ok(cuerpo);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}