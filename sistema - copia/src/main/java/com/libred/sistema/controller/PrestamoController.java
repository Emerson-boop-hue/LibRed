package com.libred.sistema.controller;

import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.service.LectorService;
import com.libred.sistema.service.PrestamoService;
import com.libred.sistema.service.RecursoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/prestamos")
public class PrestamoController {

    private final PrestamoRepository prestamoRepository;
    private final PrestamoService prestamoService;
    private final LectorService lectorService;
    private final RecursoService recursoService;

    public PrestamoController(PrestamoRepository prestamoRepository,
                              PrestamoService prestamoService,
                              LectorService lectorService,
                              RecursoService recursoService) {
        this.prestamoRepository = prestamoRepository;
        this.prestamoService = prestamoService;
        this.lectorService = lectorService;
        this.recursoService = recursoService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String listarPrestamos(@RequestParam(name = "keyword", required = false) String keyword,
                                  Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);
        List<Prestamo> prestamos;

        if (keyword != null && !keyword.isBlank()) {
            prestamos = prestamoRepository.searchByLibreria(libreriaId, keyword.trim());
        } else {
            prestamos = prestamoRepository.findByLibreriaId(libreriaId);
        }

        // Datos para la tabla y KPIs
        model.addAttribute("prestamos", prestamos);
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalPrestamos", prestamoRepository.countByLibreriaId(libreriaId));
        model.addAttribute("prestamosActivos", prestamoRepository.countByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO"));
        model.addAttribute("prestamosVencidos", prestamoRepository.countByLibreriaIdAndEstadoPrestamo(libreriaId, "VENCIDO"));

        // Datos para los desplegables del formulario de nuevo préstamo
        model.addAttribute("lectores", lectorService.listarPorLibreria(libreriaId, null));
        model.addAttribute("recursos", recursoService.listarPorLibreria(libreriaId));

        return "admin/prestamos";
    }

    // Endpoint para procesar el registro del nuevo préstamo desde el formulario
    @PostMapping("/guardar")
    public String guardarPrestamo(@RequestParam("lectorId") Integer lectorId,
                                  @RequestParam("recursoId") Integer recursoId,
                                  @RequestParam("fechaDevolucionLimite") String fechaDevolucionLimite,
                                  RedirectAttributes redirectAttributes,
                                  HttpSession session) {
        try {
            Prestamo prestamo = new Prestamo();

            com.libred.sistema.model.Lector lector = new com.libred.sistema.model.Lector();
            lector.setId(lectorId);
            prestamo.setLector(lector);

            com.libred.sistema.model.Recurso recurso = new com.libred.sistema.model.Recurso();
            recurso.setId(recursoId);
            prestamo.setRecurso(recurso);

            prestamo.setFechaDevolucionLimite(LocalDate.parse(fechaDevolucionLimite));

            // Ejecutar la lógica de negocio (validación de pertenencia, sanciones, stock y descuento)
            prestamoService.registrarPrestamo(libreriaActual(session), prestamo);

            redirectAttributes.addFlashAttribute("exito", "¡Préstamo registrado correctamente y stock actualizado!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/prestamos";
    }
}