package com.libred.sistema.controller;

import com.libred.sistema.service.PanelAdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** PANEL DE CONTROL del administrador (solo consulta). */
@Controller
public class PanelAdminController {

    private final PanelAdminService panelService;

    public PanelAdminController(PanelAdminService panelService) {
        this.panelService = panelService;
    }

    @GetMapping("/admin/panel")
    public String panel(HttpSession session, Model model) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        model.addAttribute("p", panelService.armar(libreriaId));
        return "admin/panel_admin";
    }
}