package com.libred.sistema.controller;

import com.libred.sistema.model.RegistroDtos.SolicitudCorreo;
import com.libred.sistema.model.RegistroDtos.SolicitudFinalizar;
import com.libred.sistema.model.RegistroDtos.SolicitudPago;
import com.libred.sistema.model.RegistroDtos.SolicitudPaypal;
import com.libred.sistema.model.RegistroDtos.SolicitudVerificar;
import com.libred.sistema.model.RegistroDtos.SolicitudYape;
import com.libred.sistema.service.RegistroService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoints (JSON) de la página de registro (/registro): */
@RestController
@RequestMapping("/registro")
public class RegistroController {

    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @PostMapping("/correo")
    public Map<String, Object> correo(@RequestBody SolicitudCorreo s) {
        return registroService.enviarCodigo(s.correo(), s.acepta());
    }

    @PostMapping("/verificar")
    public Map<String, Object> verificar(@RequestBody SolicitudVerificar s) {
        return registroService.verificarCodigo(s.correo(), s.codigo(), s.acepta());
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(@RequestParam String token) {
        return registroService.estado(token);
    }

    @PostMapping("/paypal/validar")
    public Map<String, Object> validarPaypal(@RequestBody SolicitudPaypal s) {
        return registroService.validarPaypal(s.token(), s.plan(), s.correo(), s.clave());
    }

    @PostMapping("/yape/solicitar")
    public Map<String, Object> solicitarYape(@RequestBody SolicitudYape s) {
        return registroService.solicitarCodigoYape(s.token(), s.celular());
    }

    @PostMapping("/pago")
    public Map<String, Object> pago(@RequestBody SolicitudPago s) {
        return registroService.pagar(s);
    }

    @PostMapping("/finalizar")
    public Map<String, Object> finalizar(@RequestBody SolicitudFinalizar s) {
        // 1) se crea y confirma la librería  2) recién entonces se envía el comprobante y se espera a Gmail
        return registroService.enviarComprobante(registroService.finalizar(s));
    }
}