package com.libred.sistema.controller;

import com.libred.sistema.model.RegistroDtos.SolicitudCelular;
import com.libred.sistema.model.RegistroDtos.SolicitudRegistroLector;
import com.libred.sistema.model.RegistroDtos.SolicitudRestablecerLector;
import com.libred.sistema.model.RegistroDtos.SolicitudVerificarLector;
import com.libred.sistema.service.LectorAccesoService;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registro propio y recuperar acceso de los lectores (desde el login, sin sesión). */
@RestController
@RequestMapping("/acceso/lector")
public class LectorAccesoController {

    private final LectorAccesoService accesoService;

    public LectorAccesoController(LectorAccesoService accesoService) {
        this.accesoService = accesoService;
    }

    @PostMapping("/registro")
    public Map<String, Object> registro(@RequestBody SolicitudRegistroLector s, HttpServletRequest request) {
        return accesoService.registrar(s, request.getRemoteAddr());
    }

    @PostMapping("/recuperar/codigo")
    public Map<String, Object> codigo(@RequestBody SolicitudCelular s) {
        return accesoService.enviarCodigo(s.celular());
    }

    @PostMapping("/recuperar/verificar")
    public Map<String, Object> verificar(@RequestBody SolicitudVerificarLector s) {
        return accesoService.verificar(s.celular(), s.codigo());
    }

    @PostMapping("/recuperar/restablecer")
    public Map<String, Object> restablecer(@RequestBody SolicitudRestablecerLector s) {
        return accesoService.restablecer(s.celular(), s.codigo(), s.codigoLector(), s.password());
    }

    @ExceptionHandler(SuscripcionException.class)
    public ResponseEntity<Map<String, Object>> negocio(SuscripcionException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        if (ex instanceof LectorAccesoService.DuplicadosException dup) {
            cuerpo.put("errores", dup.getErrores());
        }
        return ResponseEntity.status(ex.getEstado()).body(cuerpo);
    }
}