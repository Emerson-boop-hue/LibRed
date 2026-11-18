package com.libred.sistema.controller;

import com.libred.sistema.model.RegistroDtos.SolicitudRenovar;
import com.libred.sistema.service.SuscripcionService;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Endpoints (JSON) del módulo de suscripción: */
@RestController
@RequestMapping("/suscripcion")
public class SuscripcionController {

    private final SuscripcionService suscripcionService;

    public SuscripcionController(SuscripcionService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @GetMapping("/planes")
    public List<Map<String, Object>> planes() {
        return suscripcionService.listarPlanes();
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(HttpSession session) {
        return suscripcionService.obtenerEstado(libreriaActual(session));
    }

    @PostMapping("/pagar")
    public Map<String, Object> pagar(@RequestBody SolicitudRenovar solicitud, HttpSession session) {
        return suscripcionService.pagar(libreriaActual(session), solicitud);
    }

    @PostMapping("/yape/codigo")
    public Map<String, Object> codigoYape(@RequestBody Map<String, String> datos, HttpSession session) {
        libreriaActual(session);
        return suscripcionService.solicitarCodigoYape(datos.get("celular"));
    }

    @PostMapping("/renovacion/cancelar")
    public Map<String, Object> cancelarRenovacion(HttpSession session) {
        return suscripcionService.cancelarRenovacion(libreriaActual(session));
    }

    @PostMapping("/renovacion/reactivar")
    public Map<String, Object> reactivarRenovacion(HttpSession session) {
        return suscripcionService.reactivarRenovacion(libreriaActual(session));
    }

    // La librería que inició sesión se guarda en AuthController (atributo "libreriaId").
    private Integer libreriaActual(HttpSession session) {
        Object id = session.getAttribute("libreriaId");
        if (id instanceof Integer) {
            return (Integer) id;
        }
        throw new SuscripcionException(null, "Inicia sesión para ver tu plan.", HttpStatus.UNAUTHORIZED);
    }

    // Manejo de errores -> JSON
    @ExceptionHandler(SuscripcionException.class)
    public ResponseEntity<Map<String, Object>> manejarError(SuscripcionException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        return ResponseEntity.status(ex.getEstado()).body(cuerpo);
    }
}