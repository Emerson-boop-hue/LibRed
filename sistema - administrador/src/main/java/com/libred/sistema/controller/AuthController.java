package com.libred.sistema.controller;

import com.libred.sistema.service.AuthService;
import com.libred.sistema.model.Libreria;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

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
    //    Al llegar aquí (por ejemplo con "Sí, confirmar" del cierre de sesión) se olvida la librería de la sesión.
    @GetMapping("/login-admin")
    public String mostrarLoginAdmin(Model model, HttpServletRequest request, HttpSession session) {
        session.removeAttribute("libreriaId");
        model.addAttribute("isAdmin", true);
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // 4. Procesar el formulario de Admin
    @PostMapping("/login-admin")
    public String procesarLoginAdmin(@RequestParam String codigo, @RequestParam String password, Model model, HttpServletRequest request, HttpSession session) {
        Optional<Libreria> admin = authService.autenticarAdmin(codigo, password);
        if (admin.isPresent()) {
            // Se recuerda qué librería inició sesión: TODOS los módulos filtran sus datos con este valor
            session.setAttribute("libreriaId", admin.get().getId());
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
        return "lector/panel_lector";
    }

    @GetMapping("/admin/panel")
    public String panelAdmin() {
        return "admin/panel_admin";
    }

    // --- RUTAS ADMINISTRATIVAS ---

    @GetMapping("/admin/apartados")
    public String panelApartados() { return "admin/apartados"; }

    // (Cola de espera eliminada definitivamente)


    @GetMapping("/admin/ingresos")
    public String panelIngresos() { return "admin/ingresos"; }

    @GetMapping("/admin/reportes")
    public String panelReportes() { return "admin/reportes"; }

    @GetMapping("/admin/estadisticas")
    public String panelEstadisticas() {
        return "admin/estadisticas";
    }

    @GetMapping("/admin/ajustes")
    public String panelAjustes() { return "admin/ajustes"; }

    @GetMapping("/admin/ayuda")
    public String panelAyuda() { return "admin/ayuda"; }

}