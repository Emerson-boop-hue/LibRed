package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.service.LectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    // Filtros opcionales y combinables: keyword (nombre/apellido/código), estado (BLOQUEADO) y rango de fechas de registro
    @GetMapping
    public String moduloLectores(@RequestParam(name = "keyword", required = false) String keyword,
                                 @RequestParam(name = "estado", required = false) String estado,
                                 @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                 @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                 Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);
        LocalDate hoy = LocalDate.now();

        // Rango válido: sin fechas futuras y con "desde" <= "hasta"
        if (desde != null && desde.isAfter(hoy)) desde = hoy;
        if (hasta != null && hasta.isAfter(hoy)) hasta = hoy;
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            LocalDate intercambio = desde;
            desde = hasta;
            hasta = intercambio;
        }
        String estadoFiltro = "BLOQUEADO".equalsIgnoreCase(estado) ? "BLOQUEADO" : null;

        List<Lector> lectores = lectorService.listarPorLibreria(libreriaId, keyword, estadoFiltro, desde, hasta);

        boolean hayKeyword = keyword != null && !keyword.isBlank();
        model.addAttribute("lectores", lectores);
        model.addAttribute("keyword", keyword);
        model.addAttribute("estado", estadoFiltro);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("hoy", hoy);
        model.addAttribute("rangoTexto", textoRango(desde, hasta));
        model.addAttribute("hayFiltros", hayKeyword || estadoFiltro != null || desde != null || hasta != null);
        // Los KPIs muestran el total de la librería: no cambian al filtrar la tabla
        model.addAttribute("totalLectores", lectorService.contarTotalLectoresPorLibreria(libreriaId));
        model.addAttribute("totalBloqueados", lectorService.contarLectoresBloqueadosPorLibreria(libreriaId));

        return "admin/lectores";
    }

    // Texto del rango para la línea de resumen (null si no hay fechas)
    private String textoRango(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (desde != null && hasta != null) {
            return desde.equals(hasta) ? "el " + desde.format(f) : "del " + desde.format(f) + " al " + hasta.format(f);
        }
        if (desde != null) return "desde el " + desde.format(f);
        if (hasta != null) return "hasta el " + hasta.format(f);
        return null;
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