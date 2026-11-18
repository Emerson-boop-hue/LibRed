package com.libred.sistema.controller;

import com.libred.sistema.service.AuthService;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** Inicio y cierre de sesión de administradores y lectores. */
@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Mostrar la pantalla de Login para Lectores NUEVO: se envía la lista de librerías para el desplegable "Librería"
    @GetMapping("/login")
    public String mostrarLoginLector(Model model, HttpServletRequest request) {
        model.addAttribute("isAdmin", false);
        model.addAttribute("librerias", authService.listarLibrerias());
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // Procesar el formulario de Lector El lector elige la librería a la que quiere entrar (cualquiera de la red).
    @PostMapping("/login")
    public String procesarLoginLector(@RequestParam String codigo, @RequestParam String password,
                                      @RequestParam(required = false) Integer libreriaId, Model model,
                                      HttpServletRequest request, HttpSession session) {
        Optional<Lector> lector = authService.autenticarLector(codigo, password, libreriaId);
        if (lector.isPresent()) {
            Lector l = lector.get();
            request.changeSessionId(); // nueva sesión al autenticarse: evita la fijación de sesión
            session.removeAttribute("libreriaId");
            session.setAttribute("lectorId", l.getId());
            session.setAttribute("lectorNombre", l.getNombres() + " " + l.getApellidos());
            session.setAttribute("lectorCodigo", l.getCodigoLector());
            // Contraseña temporal del administrador: se le recomienda cambiarla (aviso en el panel, no es
            // obligatorio)
            if (l.isDebeCambiarPassword()) {
                session.setAttribute("lectorCambiarPassword", true);
            } else {
                session.removeAttribute("lectorCambiarPassword");
            }
            return "redirect:/lector/panel";
        }
        model.addAttribute("isAdmin", false);
        model.addAttribute("librerias", authService.listarLibrerias());
        model.addAttribute("libreriaIdSeleccionada", libreriaId);
        model.addAttribute("error", "Código o contraseña incorrectos");
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // Mostrar la pantalla de Login para Administradores Al llegar aquí (por ejemplo con "Sí, confirmar" del cierre de
    // sesión) se olvida la librería de la sesión.
    @GetMapping("/login-admin")
    public String mostrarLoginAdmin(Model model, HttpServletRequest request, HttpSession session) {
        session.removeAttribute("libreriaId");
        session.removeAttribute("libreriaNombre");
        session.removeAttribute("libreriaCodigo");
        model.addAttribute("isAdmin", true);
        model.addAttribute("librerias", authService.listarLibrerias());
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

    // Procesar el formulario de Admin
    @PostMapping("/login-admin")
    public String procesarLoginAdmin(@RequestParam String codigo, @RequestParam String password, Model model, HttpServletRequest request, HttpSession session) {
        Optional<Libreria> admin = authService.autenticarAdmin(codigo, password);
        if (admin.isPresent()) {
            request.changeSessionId(); // nueva sesión al autenticarse: evita la fijación de sesión
            // Se recuerda qué librería inició sesión: TODOS los módulos filtran sus datos con este valor
            session.setAttribute("libreriaId", admin.get().getId());
            // nombre comercial y código del admin, para mostrarlos en la tarjeta de la barra lateral
            session.setAttribute("libreriaNombre", admin.get().getNombreComercial());
            session.setAttribute("libreriaCodigo", admin.get().getCodigoAdmin());
            return "redirect:/admin/panel";
        }
        model.addAttribute("isAdmin", true);
        model.addAttribute("librerias", authService.listarLibrerias());
        model.addAttribute("error", "Código o contraseña incorrectos");
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "login :: #cardContent";
        }
        return "login";
    }

}