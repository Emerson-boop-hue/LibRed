package com.libred.sistema.controller;

import com.libred.sistema.service.MovimientoService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

/** Módulo MOVIMIENTOS del administrador: historial de préstamos, devoluciones y multas de su librería. */
@Controller
@RequestMapping("/admin/movimientos")
public class MovimientoController {

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @GetMapping
    public String mostrar(Model model, HttpServletResponse response, HttpSession session) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // El interceptor de WebConfig garantiza que hay un administrador con sesión
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");

        model.addAttribute("m", movimientoService.armarPantalla(libreriaId));
        model.addAttribute("hoy", LocalDate.now());
        return "admin/movimientos";
    }
}