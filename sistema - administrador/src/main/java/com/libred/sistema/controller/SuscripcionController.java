package com.libred.sistema.controller;

import com.libred.sistema.model.RegistroSuscripcionRequest;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.service.SuscripcionService;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * Endpoints (JSON) del módulo de suscripción:
 *  - /suscripcion/planes          -> planes y precios (asistente de registro, paso 1)
 *  - /suscripcion/siguiente-codigo-> código ADM-00X que se asignará (paso 3)
 *  - /suscripcion/registrar       -> da de alta la librería nueva
 *  - /suscripcion/estado          -> datos del modal "Ver plan actual"
 *  - /suscripcion/migrar-anual    -> pasa la librería del plan mensual al anual
 */
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

    @GetMapping("/siguiente-codigo")
    public Map<String, String> siguienteCodigo() {
        return Map.of("codigo", suscripcionService.generarSiguienteCodigoAdmin());
    }

    @PostMapping("/registrar")
    public Map<String, Object> registrar(@RequestBody RegistroSuscripcionRequest request) {
        Libreria nueva = suscripcionService.registrarLibreria(request);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("codigoAdmin", nueva.getCodigoAdmin());
        respuesta.put("mensaje", "Tu librería ha sido dada de alta correctamente.");
        return respuesta;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(HttpSession session) {
        return suscripcionService.obtenerEstado(libreriaActual(session));
    }

    @PostMapping("/migrar-anual")
    public Map<String, Object> migrarAnual(HttpSession session) {
        return suscripcionService.migrarAPlanAnual(libreriaActual(session));
    }

    // La librería que inició sesión se guarda en AuthController (atributo "libreriaId").
    // Ya no existe librería por defecto: sin sesión no se puede consultar ni migrar ningún plan.
    private Integer libreriaActual(HttpSession session) {
        Object id = session.getAttribute("libreriaId");
        if (id instanceof Integer) {
            return (Integer) id;
        }
        throw new SuscripcionException(null, "Inicia sesión para ver tu plan.", HttpStatus.UNAUTHORIZED);
    }

    // ---------------------- Manejo de errores -> JSON ----------------------
    @ExceptionHandler(SuscripcionException.class)
    public ResponseEntity<Map<String, Object>> manejarError(SuscripcionException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        return ResponseEntity.status(ex.getEstado()).body(cuerpo);
    }

    // Por si dos registros simultáneos chocan en el RUC o en el código (restricciones UNIQUE de la BD)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarDuplicado(DataIntegrityViolationException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", "No se pudo completar el registro: el RUC o el código ya existen. Intenta nuevamente.");
        cuerpo.put("campo", null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }
}