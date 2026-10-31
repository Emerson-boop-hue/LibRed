package com.libred.sistema.controller;

import com.libred.sistema.service.CatalogoService;
import com.libred.sistema.service.LectorPanelService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantallas generales del panel del lector:
 *  - Vista General (/lector/panel)
 *  - Cerrar sesión (/lector/logout)
 *  - Páginas "próximamente" de los módulos que todavía no se desarrollan.
 *    A medida que se creen los módulos (Préstamos, Historial, Red Global...), se borra su método de aquí
 *    y se crea su propio controller.
 */
@Controller
public class LectorPanelController {

    private final LectorPanelService lectorPanelService;
    private final CatalogoService catalogoService;

    public LectorPanelController(LectorPanelService lectorPanelService, CatalogoService catalogoService) {
        this.lectorPanelService = lectorPanelService;
        this.catalogoService = catalogoService;
    }

    @GetMapping("/lector/panel")
    public String panelLector(Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        // Librería a la que entró el lector (la que eligió al iniciar sesión)
        Integer libreriaId = catalogoService.sedeDelLector(lectorId);

        var resumen = lectorPanelService.obtenerResumen(lectorId);
        if (resumen.isEmpty()) {
            // El lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("lector", resumen.get().lector());
        model.addAttribute("sede", resumen.get().sede());
        model.addAttribute("totalArticulos", catalogoService.contarArticulos(libreriaId));
        return "lector/panel_lector";
    }

    @GetMapping("/lector/logout")
    public String cerrarSesion(HttpSession session) {
        session.removeAttribute("lectorId");
        session.removeAttribute("lectorNombre");
        session.removeAttribute("lectorCodigo");
        return "redirect:/login";
    }

    // ---------- Módulos pendientes (páginas "próximamente") ----------

    @GetMapping("/lector/prestamos")
    public String prestamos(Model model) {
        return proximamente(model, "Mis préstamos", "Solicita artículos y consulta tus préstamos vigentes", "prestamos");
    }

    @GetMapping("/lector/historial")
    public String historial(Model model) {
        return proximamente(model, "Historial de movimientos", "Consulta tus préstamos anteriores y tus multas", "historial");
    }

    @GetMapping("/lector/red-global")
    public String redGlobal(Model model) {
        return proximamente(model, "Red Global LibRed", "Explora las librerías afiliadas a la plataforma", "red-global");
    }

    @GetMapping("/lector/ayuda")
    public String ayuda(Model model) {
        return proximamente(model, "Ayuda y Soporte", "Resuelve tus dudas sobre LibRed", "ayuda");
    }

    private String proximamente(Model model, String titulo, String descripcion, String menuActivo) {
        model.addAttribute("titulo", titulo);
        model.addAttribute("descripcion", descripcion);
        model.addAttribute("menuActivo", menuActivo);
        return "lector/proximamente";
    }
}