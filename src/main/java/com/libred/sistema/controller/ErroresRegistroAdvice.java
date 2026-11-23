package com.libred.sistema.controller;

import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Convierte los errores del registro y de la recuperación de acceso en JSON {ok:false, error, campo}. */
@RestControllerAdvice(assignableTypes = {RegistroController.class, RecuperacionController.class})
public class ErroresRegistroAdvice {

    @ExceptionHandler(SuscripcionException.class)
    public ResponseEntity<Map<String, Object>> negocio(SuscripcionException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        return ResponseEntity.status(ex.getEstado()).body(cuerpo);
    }

    // Dos registros simultáneos que chocan en el RUC, el correo o el código de administrador (restricciones UNIQUE)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> duplicado(DataIntegrityViolationException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", "No se pudo completar el registro: el RUC o el correo ya existen. Intenta nuevamente.");
        cuerpo.put("campo", null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(cuerpo);
    }
}