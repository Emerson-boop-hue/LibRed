package com.libred.sistema.controller;

import com.libred.sistema.model.Multa;
import com.libred.sistema.service.MultaService;
import com.libred.sistema.service.PrestamoService;
import com.libred.sistema.service.LectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/multas")
public class MultaController {

    private final MultaService multaService;
    private final PrestamoService prestamoService;
    private final LectorService lectorService;

    public MultaController(MultaService multaService, PrestamoService prestamoService, LectorService lectorService) {
        this.multaService = multaService;
        this.prestamoService = prestamoService;
        this.lectorService = lectorService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    // 1. Mostrar la vista principal de multas con la lista de pendientes
    @GetMapping
    public String listarMultas(Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);

        model.addAttribute("titulo", "Gestión de Multas");
        model.addAttribute("descripcion", "Control de sanciones y pagos pendientes por retraso o daños");
        model.addAttribute("menuActivo", "multas");
        model.addAttribute("multas", multaService.obtenerMultasPendientes(libreriaId));

        // Lectores y préstamos SOLO de esta librería para poblar los selectores del formulario
        model.addAttribute("lectores", lectorService.listarPorLibreria(libreriaId, null));
        model.addAttribute("prestamos", prestamoService.obtenerPrestamosActivosPorLibreria(libreriaId));

        return "admin/multas";
    }

    // 2. Registrar o actualizar una multa manual (por daño, pérdida o mora)
    @PostMapping("/guardar")
    public String guardarMulta(@ModelAttribute Multa multa, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            multaService.guardarMulta(libreriaActual(session), multa);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/multas";
    }

    // 3. Procesar el pago de una multa (Cobro en caja)
    @GetMapping("/pagar/{id}")
    public String pagarMulta(@PathVariable Long id, HttpSession session) {
        multaService.procesarPago(libreriaActual(session), id);
        return "redirect:/admin/multas";
    }
}