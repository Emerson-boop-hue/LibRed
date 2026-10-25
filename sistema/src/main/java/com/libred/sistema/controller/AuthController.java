package com.libred.sistema.controller;

import com.libred.sistema.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 1. Mostrar la pantalla de Login para Lectores
    @GetMapping("/login")
    public String mostrarLoginLector(Model model, HttpServletRequest request) {
        model.addAttribute("isAdmin", false);
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // 2. Procesar el formulario de Lector
    @PostMapping("/login")
    public String procesarLoginLector(@RequestParam String codigo, @RequestParam String password, Model model, HttpServletRequest request) {
        if (authService.autenticarLector(codigo, password)) {
            return "redirect:/lector/panel";
        }
        model.addAttribute("isAdmin", false);
        model.addAttribute("error", "Código o contraseña incorrectos");
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // 3. Mostrar la pantalla de Login para Administradores
    @GetMapping("/login-admin")
    public String mostrarLoginAdmin(Model model, HttpServletRequest request) {
        model.addAttribute("isAdmin", true);
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // 4. Procesar el formulario de Admin
    @PostMapping("/login-admin")
    public String procesarLoginAdmin(@RequestParam String codigo, @RequestParam String password, Model model, HttpServletRequest request) {
        if (authService.autenticarAdmin(codigo, password)) {
            return "redirect:/admin/panel";
        }
        model.addAttribute("isAdmin", true);
        model.addAttribute("error", "Código o contraseña incorrectos");
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // --- RUTAS PARA LOS PANELES ---

    @GetMapping("/lector/panel")
    public String panelLector() {
        return "lector/panel";
    }

    @GetMapping("/admin/panel")
    public String panelAdmin() {
        return "admin/panel";
    }
}