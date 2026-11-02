package com.libred.sistema.controller;

import com.libred.sistema.service.HistorialLectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Módulo HISTORIAL del lector (solo consulta): /lector/historial
 * El interceptor de WebConfig garantiza que hay un lector en la sesión.
 */
@Controller
public class HistorialLectorController {

    private final HistorialLectorService historialService;

    public HistorialLectorController(HistorialLectorService historialService) {
        this.historialService = historialService;
    }

    @GetMapping("/lector/historial")
    public String mostrar(Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        var pantalla = historialService.armarPantalla(lectorId);
        if (pantalla.isEmpty()) {
            // El registro del lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("h", pantalla.get());
        return "lector/historial";
    }
}