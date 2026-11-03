package com.libred.sistema.controller;

import com.libred.sistema.model.Calificacion;
import com.libred.sistema.service.AjustesException;
import com.libred.sistema.service.CalificacionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** Califica un préstamo devuelto desde el Historial del lector (JSON). */
@Controller
public class CalificacionController {

    private final CalificacionService calificacionService;

    public CalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @PostMapping("/lector/historial/calificar")
    @ResponseBody
    public Map<String, Object> calificar(@RequestBody Map<String, String> datos, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        Integer prestamoId = aEntero(datos.get("prestamoId"), null, "No se indicó qué préstamo calificar.");
        Integer estrellas = aEntero(datos.get("estrellas"), "estrellas", "Elige una calificación de 1 a 5 estrellas.");

        Calificacion c = calificacionService.calificar(lectorId, prestamoId, estrellas);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("mensaje", "¡Gracias por tu calificación!");
        r.put("prestamoId", c.getPrestamoId());
        r.put("estrellas", c.getEstrellas());
        r.put("etiqueta", calificacionService.etiqueta(c.getEstrellas()));
        r.put("modificada", c.isModificada());
        r.put("fecha", c.getFechaCalificacion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        r.put("pendientes", calificacionService.contarPendientes(lectorId));
        return r;
    }

    private Integer aEntero(String texto, String campo, String mensaje) {
        try {
            return Integer.valueOf(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            throw new AjustesException(campo, mensaje);
        }
    }

    @ExceptionHandler(AjustesException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manejarError(AjustesException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }
}