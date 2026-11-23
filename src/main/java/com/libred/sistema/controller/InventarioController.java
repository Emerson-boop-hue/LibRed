package com.libred.sistema.controller;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.service.PoliticaPrestamoService;
import com.libred.sistema.service.PortadaService;
import com.libred.sistema.service.RecursoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Módulo Inventario del administrador: catálogo, imágenes y portadas automáticas. */
@Controller
@RequestMapping("/admin/inventario")
public class InventarioController {

    private final RecursoService recursoService;
    private final PoliticaPrestamoService politicaService;
    private final PortadaService portadaService;

    public InventarioController(RecursoService recursoService, PoliticaPrestamoService politicaService,
                                PortadaService portadaService) {
        this.recursoService = recursoService;
        this.politicaService = politicaService;
        this.portadaService = portadaService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String mostrarInventario(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String estado,
            Model model,
            HttpSession session) {

        Integer libreriaId = libreriaActual(session);

        // Solo se aceptan los dos estados que filtran los KPIs (stock crítico y agotados)
        String estadoFiltro = null;
        if ("CRITICO".equalsIgnoreCase(estado)) {
            estadoFiltro = "CRITICO";
        } else if ("AGOTADO".equalsIgnoreCase(estado)) {
            estadoFiltro = "AGOTADO";
        }

        List<Recurso> listaRecursos = recursoService.filtrarRecursos(libreriaId, keyword, categoria, estadoFiltro);

        List<Recurso> listaTotal = recursoService.listarPorLibreria(libreriaId);
        long totalArticulos = listaTotal.stream().mapToInt(Recurso::getStockTotal).sum(); // ejemplares (suma del stock total)
        long totalTitulos = listaTotal.size();                                          // títulos distintos registrados
        long stockCritico = listaTotal.stream().filter(r -> "CRITICO".equals(r.getEstado())).count();
        long articulosAgotados = listaTotal.stream().filter(r -> "AGOTADO".equals(r.getEstado())).count();

        model.addAttribute("recursos", listaRecursos);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoriaSeleccionada", categoria);
        // Solo las categorías que esta librería decidió usar (Ajustes)
        model.addAttribute("categorias", politicaService.categoriasActivas(libreriaId));
        // Las desactivadas se siguen reconociendo (artículos viejos que quedaron en ellas)
        var activas = politicaService.categoriasActivas(libreriaId);
        model.addAttribute("categoriasInactivas", java.util.Arrays.stream(com.libred.sistema.model.CategoriaRecurso.values())
                .filter(c -> !activas.contains(c)).toList());
        model.addAttribute("totalArticulos", totalArticulos);
        model.addAttribute("totalTitulos", totalTitulos);
        model.addAttribute("estadoSeleccionado", estadoFiltro);
        model.addAttribute("hayFiltros", (keyword != null && !keyword.isBlank())
                || (categoria != null && !categoria.isBlank()) || estadoFiltro != null);
        model.addAttribute("stockCritico", stockCritico);
        model.addAttribute("articulosAgotados", articulosAgotados);
        // Artículos sin imagen: si hay alguno, aparece el botón "Completar portadas"
        model.addAttribute("sinPortada", listaTotal.stream().filter(r -> r.getImagen() == null || r.getImagen().isBlank()).count());

        return "admin/inventario";
    }

    @ResponseBody
    @GetMapping("/codigo-sugerido")
    public String obtenerCodigoSugerido(@RequestParam String tipoRecurso, HttpSession session) {
        return recursoService.generarCodigoSugerido(libreriaActual(session), tipoRecurso);
    }

    // Aviso de título repetido mientras se llena el formulario (JSON: {"titulo": "mensaje"} o {} si está libre)
    @ResponseBody
    @GetMapping("/verificar-titulo")
    public Map<String, String> verificarTitulo(@RequestParam(defaultValue = "") String titulo,
                                               @RequestParam(required = false) Integer id,
                                               HttpSession session) {
        String mensaje = recursoService.tituloRepetido(libreriaActual(session), id, titulo);
        return mensaje == null ? Map.of() : Map.of("titulo", mensaje);
    }

    // Portada automática para el modal: devuelve la imagen (JPEG, PNG...) o 404 si no se encontró nada.
    @GetMapping("/portada-auto")
    public ResponseEntity<byte[]> portadaAutomatica(@RequestParam String titulo,
                                                    @RequestParam(required = false) String autor,
                                                    @RequestParam(required = false) String categoria) {
        var portada = portadaService.buscar(titulo, autor, categoria);
        if (portada.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(portada.get().datos());
    }

    // Ids de los artículos que todavía no tienen imagen (para el botón "Completar portadas")
    @ResponseBody
    @GetMapping("/portadas-pendientes")
    public List<Integer> portadasPendientes(HttpSession session) {
        return recursoService.idsSinPortada(libreriaActual(session));
    }

    // Completa la portada de UN artículo sin imagen (nunca reemplaza una imagen existente)
    @ResponseBody
    @PostMapping("/portada-auto/{id}")
    public Map<String, Object> completarPortada(@PathVariable Integer id, HttpSession session) {
        Map<String, Object> respuesta = new HashMap<>();
        try {
            String url = recursoService.completarPortada(libreriaActual(session), id);
            respuesta.put("ok", url != null);
            respuesta.put("url", url);
        } catch (Exception e) {
            respuesta.put("ok", false);
        }
        return respuesta;
    }

    @PostMapping("/guardar")
    public String guardarNuevoArticulo(Recurso recurso,
                                       @RequestParam(name = "imagenArchivo", required = false) MultipartFile imagenArchivo,
                                       @RequestParam(name = "quitarImagen", defaultValue = "false") boolean quitarImagen,
                                       RedirectAttributes redirectAttributes, HttpSession session) {

        Integer libreriaId = libreriaActual(session);

        // Asociamos el recurso a la librería de la sesión de manera obligatoria
        Libreria libreria = new Libreria();
        libreria.setId(libreriaId);
        recurso.setLibreria(libreria);

        try {
            boolean esNuevo = (recurso.getId() == null);
            recursoService.guardar(libreriaId, recurso, imagenArchivo, quitarImagen);
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