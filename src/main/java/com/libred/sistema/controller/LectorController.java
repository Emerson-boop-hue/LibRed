package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.service.LectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/lectores")
public class LectorController {

    private final LectorService lectorService;

    public LectorController(LectorService lectorService) {
        this.lectorService = lectorService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String moduloLectores(@RequestParam(name = "keyword", required = false) String keyword,
                                 Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);
        List<Lector> lectores = lectorService.listarPorLibreria(libreriaId, keyword);

        model.addAttribute("lectores", lectores);
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalLectores", lectorService.contarTotalLectoresPorLibreria(libreriaId));
        model.addAttribute("totalSancionados", lectorService.contarLectoresSancionadosPorLibreria(libreriaId));

        return "admin/lectores";
    }

    @PostMapping("/guardar")
    public String guardarLector(@ModelAttribute Lector lector, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            lectorService.guardar(libreriaActual(session), lector);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lectores";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarLector(@PathVariable Integer id, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            lectorService.eliminar(libreriaActual(session), id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el lector (tiene préstamos o multas asociados).");
        }
        return "redirect:/admin/lectores";
    }

    @GetMapping("/codigo-sugerido")
    @ResponseBody
    public String obtenerCodigoSugerido() {
        return lectorService.generarSiguienteCodigo();
    }
}