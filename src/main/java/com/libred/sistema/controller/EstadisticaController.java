package com.libred.sistema.controller;

import com.libred.sistema.model.EstadisticaVista.Pantalla;
import com.libred.sistema.service.EstadisticaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Módulo ESTADÍSTICAS del administrador (solo consulta). */
@Controller
public class EstadisticaController {

    private final EstadisticaService estadisticaService;

    public EstadisticaController(EstadisticaService estadisticaService) {
        this.estadisticaService = estadisticaService;
    }

    @GetMapping("/admin/estadisticas")
    public String estadisticas(@RequestParam(required = false) String desde,
                               @RequestParam(required = false) String hasta,
                               HttpSession session, Model model) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Pantalla pantalla = estadisticaService.armar(libreriaId, desde, hasta);
        model.addAttribute("e", pantalla);
        model.addAttribute("hoy", LocalDate.now().toString());
        return "admin/estadisticas";
    }
}