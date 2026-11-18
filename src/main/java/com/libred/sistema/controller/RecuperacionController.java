package com.libred.sistema.controller;

import com.libred.sistema.model.RegistroDtos.SolicitudRecuperarCodigo;
import com.libred.sistema.model.RegistroDtos.SolicitudRestablecer;
import com.libred.sistema.service.RecuperacionAccesoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Recuperar acceso del administrador (desde el login): código al correo y contraseña nueva. */
@RestController
@RequestMapping("/acceso/recuperar")
public class RecuperacionController {

    private final RecuperacionAccesoService recuperacionService;

    public RecuperacionController(RecuperacionAccesoService recuperacionService) {
        this.recuperacionService = recuperacionService;
    }

    @PostMapping("/codigo")
    public Map<String, Object> codigo(@RequestBody SolicitudRecuperarCodigo s) {
        return recuperacionService.enviarCodigo(s.correo());
    }

    @PostMapping("/restablecer")
    public Map<String, Object> restablecer(@RequestBody SolicitudRestablecer s) {
        return recuperacionService.restablecer(s.correo(), s.codigo(), s.password());
    }
}