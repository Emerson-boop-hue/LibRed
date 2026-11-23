package com.libred.sistema.controller;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.PrestamoLectorVista.ArticuloVista;
import com.libred.sistema.model.PrestamoLectorVista.CategoriaVista;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.service.LectorService;
import com.libred.sistema.service.PoliticaPrestamoService;
import com.libred.sistema.service.PrestamoService;
import com.libred.sistema.service.RecursoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Módulo PRÉSTAMOS del administrador: registra un préstamo en mostrador.
 * Usa las mismas reglas que el lector (PrestamoService): plazo máximo, tarifa por categoría, stock y límites.
 */
@Controller
@RequestMapping("/admin/prestamos")
public class PrestamoController {

    private static final List<String> ESTADOS_EN_CURSO = List.of("ACTIVO", "VENCIDO");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PrestamoRepository prestamoRepository;
    private final PrestamoService prestamoService;
    private final LectorService lectorService;
    private final RecursoService recursoService;
    private final PoliticaPrestamoService politicaService;

    public PrestamoController(PrestamoRepository prestamoRepository,
                              PrestamoService prestamoService,
                              LectorService lectorService,
                              RecursoService recursoService,
                              PoliticaPrestamoService politicaService) {
        this.prestamoRepository = prestamoRepository;
        this.prestamoService = prestamoService;
        this.lectorService = lectorService;
        this.recursoService = recursoService;
        this.politicaService = politicaService;
    }

    // Librería del admin que inició sesión (el interceptor WebConfig garantiza que existe)
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String mostrarFormulario(Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);

        // Lectores de la librería, con cuántos préstamos en curso tiene cada uno
        List<Lector> lectores = lectorService.listarPorLibreria(libreriaId, null);
        Map<Integer, Long> enCursoPorLector = new HashMap<>();
        for (Lector l : lectores) {
            enCursoPorLector.put(l.getId(),
                    prestamoRepository.countByLectorIdAndEstadoPrestamoIn(l.getId(), ESTADOS_EN_CURSO));
        }
        model.addAttribute("lectores", lectores);
        model.addAttribute("enCursoPorLector", enCursoPorLector);

        // Categorías ACTIVAS (con tarifa) y artículos con stock de esas categorías
        Map<String, BigDecimal> tarifas = politicaService.tarifasPorValor(libreriaId);
        List<Recurso> conStock = recursoService.listarPorLibreria(libreriaId).stream()
                .filter(r -> r.getStockDisponible() != null && r.getStockDisponible() > 0)
                .sorted(Comparator.comparing(r -> r.getTitulo().toLowerCase()))
                .toList();

        List<CategoriaRecurso> activas = politicaService.categoriasActivas(libreriaId);
        List<String> valoresActivos = activas.stream().map(CategoriaRecurso::getValor).toList();

        List<ArticuloVista> articulos = new ArrayList<>();
        for (Recurso r : conStock) {
            String valor = CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor();
            if (valoresActivos.contains(valor)) {
                articulos.add(new ArticuloVista(r.getId(), r.getTitulo(), r.getCodigo(),
                        r.getAutorOCreador() == null ? "" : r.getAutorOCreador(), valor, r.getStockDisponible()));
            }
        }
        List<CategoriaVista> categorias = new ArrayList<>();
        for (CategoriaRecurso c : activas) {
            long disponibles = articulos.stream().filter(a -> a.getCategoria().equals(c.getValor())).count();
            if (disponibles > 0) {
                categorias.add(new CategoriaVista(c.getValor(), c.getEtiqueta(), c.getIcono(),
                        tarifas.get(c.getValor()), disponibles));
            }
        }
        model.addAttribute("categorias", categorias);
        model.addAttribute("articulos", articulos);

        // Política de la librería
        model.addAttribute("maxDias", politicaService.obtenerMaxDias(libreriaId));
        model.addAttribute("maxActivos", politicaService.obtenerMaxActivos(libreriaId)); // null = sin límite

        return "admin/prestamos";
    }

    // Procesa el registro del nuevo préstamo desde el formulario
    @PostMapping("/guardar")
    public String guardarPrestamo(@RequestParam("lectorId") Integer lectorId,
                                  @RequestParam("recursoId") Integer recursoId,
                                  @RequestParam("fechaDevolucionLimite") String fechaDevolucionLimite,
                                  RedirectAttributes redirectAttributes,
                                  HttpSession session) {
        try {
            Prestamo prestamo = new Prestamo();

            Lector lector = new Lector();
            lector.setId(lectorId);
            prestamo.setLector(lector);

            Recurso recurso = new Recurso();
            recurso.setId(recursoId);
            prestamo.setRecurso(recurso);

            prestamo.setFechaDevolucionLimite(LocalDate.parse(fechaDevolucionLimite));
            prestamo.setOrigen("ADMIN");

            // Ejecuta la lógica de negocio (pertenencia, sanciones, límites, plazo, tarifa, stock)
            Prestamo guardado = prestamoService.registrarPrestamo(libreriaActual(session), prestamo);

            // Datos para la ventana de confirmación
            Map<String, String> ok = new LinkedHashMap<>();
            ok.put("numero", "#" + guardado.getId());
            ok.put("lector", guardado.getLector().getNombres() + " " + guardado.getLector().getApellidos());
            ok.put("articulo", guardado.getRecurso().getTitulo());
            ok.put("fechaLimite", guardado.getFechaDevolucionLimite().format(FORMATO_FECHA));
            ok.put("dias", guardado.getDiasPrestamo() + (guardado.getDiasPrestamo() == 1 ? " día" : " días"));
            ok.put("monto", "S/ " + guardado.getMontoServicio().toPlainString());
            redirectAttributes.addFlashAttribute("prestamoOk", ok);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/prestamos";
    }
}