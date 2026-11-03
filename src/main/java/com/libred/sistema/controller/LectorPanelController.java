package com.libred.sistema.controller;

import com.libred.sistema.service.PanelLectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Pantallas generales del panel del lector:
 *  - Vista General (/lector/panel)
 *  - Cerrar sesión (/lector/logout)
 */
@Controller
public class LectorPanelController {

    private final PanelLectorService panelService;

    public LectorPanelController(PanelLectorService panelService) {
        this.panelService = panelService;
    }

    @GetMapping("/lector/panel")
    public String panelLector(Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        var vista = panelService.armar(lectorId);
        if (vista.isEmpty()) {
            // El lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("p", vista.get());
        // Aviso de "Ahora estás en ..." tras cambiar de librería desde Red Global (se muestra una sola vez)
        Object avisoSede = session.getAttribute("avisoSede");
        if (avisoSede != null) {
            model.addAttribute("avisoSede", avisoSede);
            session.removeAttribute("avisoSede");
        }
        return "lector/panel_lector";
    }

    @GetMapping("/lector/logout")
    public String cerrarSesion(HttpSession session) {
        session.removeAttribute("lectorId");
        session.removeAttribute("lectorNombre");
        session.removeAttribute("lectorCodigo");
        return "redirect:/login";
    }
}