package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.SolicitudSoporte;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.RecursoRepository;
import com.libred.sistema.repository.SolicitudSoporteRepository;
import com.libred.sistema.service.CatalogoService;
import com.libred.sistema.service.DashboardService;
import com.libred.sistema.service.PrestamoService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/lector")
public class LectorController {
    private final CatalogoService catalogoService;
    private final DashboardService dashboardService;
    private final PrestamoService prestamoService;
    private final LibreriaRepository libreriaRepository;
    private final RecursoRepository recursoRepository;
    private final LectorRepository lectorRepository;
    private final SolicitudSoporteRepository solicitudSoporteRepository;
    public LectorController(
        CatalogoService catalogoService,
        DashboardService dashboardService,
        PrestamoService prestamoService,
        LibreriaRepository libreriaRepository,
        RecursoRepository recursoRepository,
        LectorRepository lectorRepository,
        SolicitudSoporteRepository solicitudSoporteRepository) {
        this.catalogoService = catalogoService;
        this.dashboardService = dashboardService;
        this.prestamoService = prestamoService;
        this.libreriaRepository = libreriaRepository;
        this.recursoRepository = recursoRepository;
        this.lectorRepository = lectorRepository;
        this.solicitudSoporteRepository = solicitudSoporteRepository;
}

     @ModelAttribute("lectorActual")
    public Lector lectorActual(HttpSession session) {
        Object valor = session.getAttribute("lectorId");
        if (!(valor instanceof Integer id)) return null;
        return dashboardService.obtenerLector(id);
    }
        @GetMapping("/panel")
    public String panel(HttpSession session, Model model) {
        // Obtenemos el ID directamente de la sesión de forma segura
        Object valor = session.getAttribute("lectorId");
        if (!(valor instanceof Integer id)) {
            return "redirect:/login"; // Redirige al login si no hay sesión activa
        }

        Lector lector = dashboardService.obtenerLector(id);
        model.addAttribute("prestamosActivos", dashboardService.contarActivos(id));
        model.addAttribute("estadoLector", dashboardService.tieneMultasPendientes(id) ? "SANCIONADO" : lector.getEstadoCuenta());
        model.addAttribute("actividadReciente", dashboardService.actividadReciente(id));
        model.addAttribute("sede", lector.getLibreria());
        model.addAttribute("pageName", "panel");
        return "lector/panel";
    }

    @GetMapping("/catalogo")
    public String catalogo(@RequestParam(required=false) String q, @RequestParam(required=false) String tipo,
            @RequestParam(required=false) String autor, @RequestParam(required=false) String disponibilidad,
            Model model) {
        // Corregido: Uso de 'catalogoService' en minúscula
        model.addAttribute("recursos", catalogoService.buscar(q, tipo, autor, disponibilidad));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("tipo", tipo == null ? "" : tipo);
        model.addAttribute("autor", autor == null ? "" : autor);
        model.addAttribute("disponibilidad", disponibilidad == null ? "" : disponibilidad);
        model.addAttribute("pageName", "catalogo");
        return "lector/catalogo";
    }

    @GetMapping("/recurso/{id}")
    public String detalleRecurso(@PathVariable Integer id, Model model) {
        // Corregido: Uso de 'catalogoService' en minúscula
        model.addAttribute("recurso", catalogoService.obtenerPorId(id));
        model.addAttribute("pageName", "catalogo");
        return "lector/detalle-recurso";
    }

    @GetMapping("/prestamos")
    public String prestamos(HttpSession session, @RequestParam(required=false) Integer recursoId, Model model) {
        Object valor = session.getAttribute("lectorId");
        if (!(valor instanceof Integer id)) {
            return "redirect:/login";
        }
        
        model.addAttribute("prestamosActivos", prestamoService.obtenerActivos(id));
        model.addAttribute("recursoSeleccionado", recursoId == null ? null : catalogoService.obtenerPorId(recursoId));
        model.addAttribute("recursos", catalogoService.obtenerCatalogoCompleto());
        model.addAttribute("pageName", "prestamos");
        return "lector/prestamos";
    }

    @PostMapping("/prestamos/solicitar")
    public String solicitarPrestamo(HttpSession session, @RequestParam Integer recursoId,
            @RequestParam(defaultValue="7") int plazoDias, RedirectAttributes redirect) {
        Object valor = session.getAttribute("lectorId");
        if (!(valor instanceof Integer id)) {
            return "redirect:/login";
        }

        try {
            var resultado = prestamoService.solicitar(id, recursoId, plazoDias);
            if (resultado.estaEnCola()) {
                redirect.addFlashAttribute("mensajeExito", "El recurso no tiene stock. Te agregamos a la cola de espera en la posición " + resultado.posicionCola() + ".");
                return "redirect:/lector/prestamos";
            }
            return "redirect:/lector/ticket/" + resultado.prestamo().getId();
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("mensajeError", ex.getMessage());
            redirect.addAttribute("recursoId", recursoId);
            return "redirect:/lector/prestamos";
        }
    }

    @GetMapping("/ticket/{id}")
    public String ticket(@PathVariable Integer id, HttpSession session, Model model) {
        Object valor = session.getAttribute("lectorId");
        if (!(valor instanceof Integer idLector)) {
            return "redirect:/login";
        }

        var prestamo = prestamoService.obtenerPrestamos(idLector).stream()
                .filter(p -> p.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el ticket solicitado."));
        model.addAttribute("prestamo", prestamo);
        model.addAttribute("pageName", "prestamos");
        return "lector/ticket-prestamo";
    }

    @GetMapping("/historial")
    public String historial(HttpSession session, @RequestParam(required=false) String estado,
            @RequestParam(required=false) String desde, @RequestParam(required=false) String hasta, Model model) {
        var movimientos = prestamoService.obtenerPrestamos(lectorId(session));
        if (estado != null && !estado.isBlank()) movimientos = movimientos.stream()
                .filter(p -> estado.equalsIgnoreCase(p.getEstadoPrestamo())).toList();
        if (desde != null && !desde.isBlank()) movimientos = movimientos.stream()
                .filter(p -> p.getFechaPrestamo() != null && !p.getFechaPrestamo().toLocalDate().isBefore(java.time.LocalDate.parse(desde))).toList();
        if (hasta != null && !hasta.isBlank()) movimientos = movimientos.stream()
                .filter(p -> p.getFechaPrestamo() != null && !p.getFechaPrestamo().toLocalDate().isAfter(java.time.LocalDate.parse(hasta))).toList();
        model.addAttribute("movimientos", movimientos);
        model.addAttribute("multas", dashboardService.obtenerMultas(lectorId(session)));
        model.addAttribute("estado", estado == null ? "" : estado);
        model.addAttribute("desde", desde == null ? "" : desde);
        model.addAttribute("hasta", hasta == null ? "" : hasta);
        model.addAttribute("pageName", "historial");
        return "lector/historial";
    }

    @GetMapping("/red")
    public String red(@RequestParam(required=false) String q, Model model) {
        String termino = q == null ? "" : q.trim().toLowerCase();
        List<Libreria> librerias = libreriaRepository.findAll().stream()
                .filter(l -> termino.isBlank() || (l.getNombreComercial() != null && l.getNombreComercial().toLowerCase().contains(termino))
                        || (l.getDireccion() != null && l.getDireccion().toLowerCase().contains(termino)))
                .toList();
        Map<Integer, Long> conteoRecursos = recursoRepository.findAll().stream()
                .filter(r -> r.getLibreria() != null)
                .collect(Collectors.groupingBy(r -> r.getLibreria().getId(), Collectors.counting()));
        model.addAttribute("librerias", librerias);
        model.addAttribute("conteoRecursos", conteoRecursos);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("pageName", "red");
        return "lector/red";
    }
    @GetMapping("/ajustes")
public String ajustesLector(HttpSession session, Model model) {
    Integer id = (Integer) session.getAttribute("lectorId");

    if (id == null) {
        return "redirect:/login";
    }

    Lector lector = lectorRepository.findById(id).orElse(null);

    if (lector == null) {
        session.invalidate();
        return "redirect:/login";
    }

    model.addAttribute("lectorId", id);
    model.addAttribute("lector", lector);

    return "lector/ajustes";
}

@PostMapping("/ajustes/guardar")
public String guardarAjustes(
        HttpSession session,
        @ModelAttribute("lector") Lector datos,
        RedirectAttributes redirectAttributes) {

    Integer id = (Integer) session.getAttribute("lectorId");

    if (id == null) {
        return "redirect:/login";
    }

    Lector lector = lectorRepository.findById(id).orElse(null);

    if (lector == null) {
        session.invalidate();
        return "redirect:/login";
    }

    // Solo se actualizan los campos permitidos.
    // No se sobrescriben la contraseña, el DNI ni la librería.
    if (datos.getTelefono() == null|| datos.getTelefono().length() > 20) {
        redirectAttributes.addFlashAttribute(
            "error", "Ingresa un teléfono válido.");
        return "redirect:/lector/ajustes";
    }

    lector.setTelefono(datos.getTelefono().trim());
    lector.setRecibirNotificaciones(
            Boolean.TRUE.equals(datos.getRecibirNotificaciones()));

    lectorRepository.save(lector);

    redirectAttributes.addFlashAttribute(
            "mensaje", "Tus ajustes se guardaron correctamente.");

    return "redirect:/lector/ajustes";
}

@GetMapping("/ayuda")
public String ayudaLector(HttpSession session, Model model) {
    Integer id = (Integer) session.getAttribute("lectorId");

    if (id == null) {
        return "redirect:/login";
    }

    model.addAttribute("lectorId", id);
    return "lector/ayuda";
}

@PostMapping("/ayuda/enviar")
public String enviarSolicitud(
        HttpSession session,
        @ModelAttribute SolicitudSoporte solicitud,
        RedirectAttributes ra) {

    Integer id = (Integer) session.getAttribute("lectorId");

    if (id == null) {
        return "redirect:/login";
    }

    Lector lector = lectorRepository.findById(id).orElse(null);

    if (lector == null) {
        session.invalidate();
        return "redirect:/login";
    }

    // Validar la categoría y el mensaje
    if (solicitud.getCategoria() == null
            || solicitud.getCategoria().isBlank()
            || solicitud.getMensaje() == null
            || solicitud.getMensaje().isBlank()
            || solicitud.getMensaje().length() > 2000) {

        ra.addFlashAttribute(
                "errorSoporte",
                "Selecciona una categoría e ingresa un mensaje de hasta 2000 caracteres."
        );

        return "redirect:/lector/ayuda";
    }

    // Completar los datos del usuario antes de guardar
    solicitud.setLectorId(id);
    solicitud.setNombre(
            (lector.getNombres() + " " + lector.getApellidos()).trim()
    );

    solicitud.setMensaje(solicitud.getMensaje().trim());
    solicitud.setEstado("PENDIENTE");

    // Evitar guardar si el lector no tiene correo registrado
    solicitud.setLectorId(id);
    solicitud.setNombre(
    (lector.getNombres() + " " + lector.getApellidos()).trim()
);
solicitud.setMensaje(solicitud.getMensaje().trim());
solicitud.setEstado("PENDIENTE");

solicitudSoporteRepository.save(solicitud);

    ra.addFlashAttribute(
            "mensajeSoporte",
            "Tu consulta fue registrada correctamente."
    );

    return "redirect:/lector/ayuda";
}
    private Integer lectorId(HttpSession session) {
        Object valor = session.getAttribute("lectorId");
        if (valor instanceof Integer id) return id;
        throw new IllegalArgumentException("La sesión del lector expiró. Inicia sesión nuevamente.");
    }
}
