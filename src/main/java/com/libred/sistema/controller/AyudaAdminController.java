package com.libred.sistema.controller;

import com.libred.sistema.model.AyudaAdminVista.Pantalla;
import com.libred.sistema.service.AyudaAdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** AYUDA Y SOPORTE del administrador: guías y solicitudes de soporte. */
@Controller
public class AyudaAdminController {

    private final AyudaAdminService ayudaService;

    /**
     * Número de WhatsApp de soporte (application.properties: libred.soporte.whatsapp). Sin número, el canal queda
     * apagado.
     */
    @Value("${libred.soporte.whatsapp:}")
    private String whatsapp;

    /**
     * Correo de soporte (application.properties: libred.soporte.correo). Sin correo, el canal "Correo directo" queda
     * apagado.
     */
    @Value("${libred.soporte.correo:}")
    private String correoSoporte;

    public AyudaAdminController(AyudaAdminService ayudaService) {
        this.ayudaService = ayudaService;
    }

    @GetMapping("/admin/ayuda")
    public String ayuda(@RequestParam(required = false) String tab, HttpSession session, Model model) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Pantalla pantalla = ayudaService.armar(libreriaId);
        model.addAttribute("a", pantalla);
        model.addAttribute("categorias", AyudaAdminService.CATEGORIAS);
        model.addAttribute("whatsapp", whatsapp == null ? "" : whatsapp.replaceAll("\\D", ""));
        model.addAttribute("correoSoporte", correoSoporte == null ? "" : correoSoporte.trim());
        model.addAttribute("tab", "soporte".equals(tab) || "solicitudes".equals(tab) ? tab : "guias");
        model.addAttribute("asuntoMin", AyudaAdminService.ASUNTO_MIN);
        model.addAttribute("asuntoMax", AyudaAdminService.ASUNTO_MAX);
        model.addAttribute("mensajeMax", AyudaAdminService.MENSAJE_MAX);
        return "admin/ayuda";
    }

    @PostMapping("/admin/ayuda/solicitud")
    public String enviarSolicitud(@RequestParam String categoria, @RequestParam(required = false) String tema,
                                  @RequestParam String asunto, @RequestParam String mensaje,
                                  HttpSession session, RedirectAttributes redirect) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        try {
            ayudaService.crearTicket(libreriaId, categoria, tema, asunto, mensaje);
        } catch (IllegalArgumentException e) {
            // solo se avisa si algo falla (en rojo); se devuelve lo escrito para no perderlo
            redirect.addFlashAttribute("errorSolicitud", e.getMessage());
            redirect.addFlashAttribute("solCategoria", categoria);
            redirect.addFlashAttribute("solTema", tema);
            redirect.addFlashAttribute("solAsunto", asunto);
            redirect.addFlashAttribute("solMensaje", mensaje);
        }
        return "redirect:/admin/ayuda?tab=soporte";
    }
}