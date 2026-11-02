package com.libred.sistema.controller;

import com.libred.sistema.service.RedGlobalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Módulo RED GLOBAL del lector: directorio de las librerías de la red (solo consulta). */
@Controller
public class RedGlobalController {

    private final RedGlobalService redGlobalService;

    public RedGlobalController(RedGlobalService redGlobalService) {
        this.redGlobalService = redGlobalService;
    }

    @GetMapping("/lector/red-global")
    public String mostrar(Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        var vista = redGlobalService.armar(lectorId);
        if (vista.isEmpty()) {
            // El lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("r", vista.get());
        return "lector/red_global";
    }
}