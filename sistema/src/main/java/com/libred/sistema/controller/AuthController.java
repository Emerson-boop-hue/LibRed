package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    private final AuthService authService;
    private final LibreriaRepository libreriaRepository;

    // Inyección de dependencias por constructor
    public AuthController(AuthService authService, LibreriaRepository libreriaRepository) {
        this.authService = authService;
        this.libreriaRepository = libreriaRepository;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
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
    public String procesarLoginLector(@RequestParam String codigo, @RequestParam String password,
                                      Model model, HttpServletRequest request, HttpSession session) {
        Optional<Lector> lector = authService.autenticarLector(codigo, password);
        if (lector.isPresent()) {
            session.setAttribute("lectorId", lector.get().getId());
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

    // 5. Cerrar sesión
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // --- NUEVAS RUTAS PARA EL REGISTRO DE LECTORES (Módulo 1.2) ---

    // 6. Mostrar el formulario de registro con las librerías de la BD
    @GetMapping("/registro")
    public String mostrarRegistro(Model model) {
        model.addAttribute("librerias", libreriaRepository.findAll()); // Llena el select dinámico
        model.addAttribute("nuevoLector", new Lector()); // Enlaza el objeto al formulario HTML
        return "registro";
    }

    // 7. Procesar la creación de una cuenta de lector
    @PostMapping("/registro")
    public String procesarRegistro(@ModelAttribute("nuevoLector") Lector lector, 
                                   @RequestParam("libreriaId") Integer libreriaId) {
        Libreria libreria = libreriaRepository.findById(libreriaId)
                .orElseThrow(() -> new IllegalArgumentException("Librería seleccionada no válida"));
        
        lector.setLibreria(libreria); // Asigna el inquilino multi-tenant
        authService.registrarLector(lector);
        
        return "redirect:/login?registrado=true";
    }

    // --- RUTAS PARA LOS PANELES ---

    @GetMapping("/admin/panel")
    public String panelAdmin() {
        return "admin/panel";
    }
}
