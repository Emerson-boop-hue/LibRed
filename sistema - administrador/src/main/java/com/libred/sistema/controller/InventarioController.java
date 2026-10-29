package com.libred.sistema.controller;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.service.RecursoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/inventario")
public class InventarioController {

    private final RecursoService recursoService;

    public InventarioController(RecursoService recursoService) {
        this.recursoService = recursoService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String mostrarInventario(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoria,
            Model model,
            HttpSession session) {

        Integer libreriaId = libreriaActual(session);

        // 1. Filtramos los artículos estrictamente de la librería en sesión
        List<Recurso> listaRecursos = recursoService.filtrarRecursos(libreriaId, keyword, categoria);

        // 2. Obtenemos la lista completa de esta librería para calcular los KPIs gerenciales
        List<Recurso> listaTotal = recursoService.listarPorLibreria(libreriaId);
        long totalArticulos = listaTotal.stream().mapToInt(Recurso::getStockTotal).sum();
        long stockCritico = listaTotal.stream().filter(r -> "CRITICO".equals(r.getEstado())).count();
        long articulosAgotados = listaTotal.stream().filter(r -> "AGOTADO".equals(r.getEstado())).count();

        // 3. Enviamos los datos al frontend
        model.addAttribute("recursos", listaRecursos);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoriaSeleccionada", categoria);
        model.addAttribute("totalArticulos", totalArticulos);
        model.addAttribute("stockCritico", stockCritico);
        model.addAttribute("articulosAgotados", articulosAgotados);

        return "admin/inventario";
    }

    @ResponseBody
    @GetMapping("/codigo-sugerido")
    public String obtenerCodigoSugerido(@RequestParam String tipoRecurso, HttpSession session) {
        return recursoService.generarCodigoSugerido(libreriaActual(session), tipoRecurso);
    }

    @PostMapping("/guardar")
    public String guardarNuevoArticulo(Recurso recurso, RedirectAttributes redirectAttributes, HttpSession session) {

        Integer libreriaId = libreriaActual(session);

        // Asociamos el recurso a la librería de la sesión de manera obligatoria
        Libreria libreria = new Libreria();
        libreria.setId(libreriaId);
        recurso.setLibreria(libreria);

        try {
            boolean esNuevo = (recurso.getId() == null);
            recursoService.guardar(libreriaId, recurso);
            String mensaje = esNuevo ? "¡Artículo registrado correctamente!" : "¡Artículo modificado correctamente!";
            redirectAttributes.addFlashAttribute("exito", mensaje);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/inventario";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarArticulo(@PathVariable Integer id, RedirectAttributes redirectAttributes, HttpSession session) {
        try {
            recursoService.eliminar(libreriaActual(session), id);
            redirectAttributes.addFlashAttribute("exito", "¡Artículo eliminado correctamente del inventario!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el artículo.");
        }
        return "redirect:/admin/inventario";
    }
}