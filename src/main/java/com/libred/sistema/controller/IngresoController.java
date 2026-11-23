package com.libred.sistema.controller;

import com.libred.sistema.service.IngresoService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

/** Módulo INGRESOS del administrador: recaudación por servicios, mora y multas de su librería. */
@Controller
@RequestMapping("/admin/ingresos")
public class IngresoController {

    private final IngresoService ingresoService;

    public IngresoController(IngresoService ingresoService) {
        this.ingresoService = ingresoService;
    }

    @GetMapping
    public String mostrar(Model model, HttpServletResponse response, HttpSession session) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // El interceptor de WebConfig garantiza que hay un administrador con sesión
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");

        model.addAttribute("m", ingresoService.armarPantalla(libreriaId));
        model.addAttribute("hoy", LocalDate.now());
        return "admin/ingresos";
    }
}