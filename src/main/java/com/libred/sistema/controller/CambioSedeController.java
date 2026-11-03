package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.service.AjustesException;
import com.libred.sistema.service.AuthService;
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

/**
 * Botón "Ingresar" de Red Global: cambia al lector a otra librería sin cerrar sesión.
 * Cambia en la sesión el registro del lector por el que tiene en la librería elegida (se crea si es su primera vez).
 */
@Controller
public class CambioSedeController {

    private final AuthService authService;

    public CambioSedeController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/lector/cambiar-sede")
    @ResponseBody
    public Map<String, Object> cambiar(@RequestBody Map<String, String> datos, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        Integer libreriaId;
        try {
            libreriaId = Integer.valueOf(datos.get("libreriaId") == null ? "" : datos.get("libreriaId").trim());
        } catch (NumberFormatException e) {
            throw new AjustesException(null, "No se indicó a qué librería entrar.");
        }

        AuthService.CambioSede cambio = authService.cambiarSede(lectorId, libreriaId);
        Lector l = cambio.lector();

        // Desde este momento toda la interfaz del lector usa los datos de la nueva librería
        session.setAttribute("lectorId", l.getId());
        session.setAttribute("lectorNombre", l.getNombres() + " " + l.getApellidos());
        session.setAttribute("lectorCodigo", l.getCodigoLector());
        // Aviso que se muestra una sola vez en la Vista General
        session.setAttribute("avisoSede", cambio.creado()
                ? "Te damos la bienvenida a " + cambio.libreriaNombre() + ". Creamos tu cuenta con tus mismos datos."
                : "Ahora estás en " + cambio.libreriaNombre() + ".");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("creado", cambio.creado());
        r.put("libreria", cambio.libreriaNombre());
        r.put("destino", "/lector/panel");
        return r;
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