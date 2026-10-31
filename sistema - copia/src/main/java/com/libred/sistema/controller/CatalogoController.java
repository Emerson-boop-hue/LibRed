package com.libred.sistema.controller;

import com.libred.sistema.model.ArticuloCatalogo;
import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.service.CatalogoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Módulo CATÁLOGO del lector: ver y filtrar los artículos de la librería a la que entró.
 * El interceptor de WebConfig garantiza que hay un lector en la sesión.
 */
@Controller
@RequestMapping("/lector/catalogo")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping
    public String mostrarCatalogo(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String autor,
            @RequestParam(required = false) String disponibilidad,
            Model model,
            HttpSession session) {

        Integer lectorId = (Integer) session.getAttribute("lectorId");

        // Librería a la que entró el lector (la que eligió al iniciar sesión)
        Integer libreriaId = catalogoService.sedeDelLector(lectorId);

        List<ArticuloCatalogo> articulos = catalogoService.buscar(libreriaId, texto, categoria, autor, disponibilidad);

        model.addAttribute("articulos", articulos);
        model.addAttribute("categorias", CategoriaRecurso.values());
        model.addAttribute("sinSede", libreriaId == null);

        // Se devuelven los filtros para que el formulario conserve lo que escribió el lector
        model.addAttribute("texto", texto == null ? "" : texto);
        model.addAttribute("categoriaSeleccionada", categoria == null ? "" : categoria);
        model.addAttribute("autor", autor == null ? "" : autor);
        model.addAttribute("disponibilidadSeleccionada", disponibilidad == null ? "" : disponibilidad);

        return "lector/catalogo";
    }
}