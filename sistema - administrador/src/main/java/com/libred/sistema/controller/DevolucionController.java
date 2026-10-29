package com.libred.sistema.controller;

import com.libred.sistema.service.DevolucionService;
import com.libred.sistema.service.LectorService;
import com.libred.sistema.service.MultaService;
import com.libred.sistema.service.PrestamoService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;
    private final PrestamoService prestamoService;
    private final LectorService lectorService;
    private final MultaService multaService;

    public DevolucionController(DevolucionService devolucionService,
                                PrestamoService prestamoService,
                                LectorService lectorService,
                                MultaService multaService) {
        this.devolucionService = devolucionService;
        this.prestamoService = prestamoService;
        this.lectorService = lectorService;
        this.multaService = multaService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String mostrarDevoluciones(Model model, HttpServletResponse response, HttpSession session) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        Integer libreriaId = libreriaActual(session);

        model.addAttribute("titulo", "Gestión de Devoluciones");
        model.addAttribute("descripcion", "Recepción de artículos, devolución a inventario y liquidación de mora");
        model.addAttribute("menuActivo", "devoluciones");

        model.addAttribute("prestamosActivos", prestamoService.obtenerPrestamosActivosPorLibreria(libreriaId));
        model.addAttribute("lectores", lectorService.listarPorLibreria(libreriaId, null));
        model.addAttribute("multasPendientes", multaService.obtenerMultasPendientes(libreriaId));

        return "admin/devoluciones";
    }

    @PostMapping("/procesar")
    public String procesarDevolucion(@RequestParam("prestamoId") Integer prestamoId,
                                     @RequestParam(value = "tarifaDiaria", defaultValue = "1.00") BigDecimal tarifaDiaria,
                                     RedirectAttributes redirectAttributes,
                                     HttpSession session) {
        try {
            devolucionService.procesarDevolucion(libreriaActual(session), prestamoId, tarifaDiaria);
            redirectAttributes.addFlashAttribute("exito", "¡Devolución completada con éxito! Stock actualizado y préstamos/multas liquidados.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/devoluciones";
    }
}