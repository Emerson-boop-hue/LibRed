package com.libred.sistema.controller;

import com.libred.sistema.service.AyudaLectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Módulo AYUDA Y SOPORTE del lector: datos de su librería, reglas de préstamo y preguntas frecuentes. */
@Controller
public class AyudaLectorController {

    private final AyudaLectorService ayudaService;

    public AyudaLectorController(AyudaLectorService ayudaService) {
        this.ayudaService = ayudaService;
    }

    @GetMapping("/lector/ayuda")
    public String mostrar(Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");
        var vista = ayudaService.armar(lectorId);
        if (vista.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("a", vista.get());
        return "lector/ayuda";
    }
}