package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.service.AjustesException;
import com.libred.sistema.service.AjustesLectorService;
import com.libred.sistema.service.LectorPanelService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Módulo AJUSTES del lector: datos personales y contraseña.
 * La pantalla guarda con fetch (JSON) para mostrar el resultado sin recargar la página.
 */
@Controller
@RequestMapping("/lector/ajustes")
public class AjustesLectorController {

    private final AjustesLectorService ajustesService;
    private final LectorPanelService lectorPanelService;

    public AjustesLectorController(AjustesLectorService ajustesService, LectorPanelService lectorPanelService) {
        this.ajustesService = ajustesService;
        this.lectorPanelService = lectorPanelService;
    }

    private Integer lectorActual(HttpSession session) {
        return (Integer) session.getAttribute("lectorId");
    }

    @GetMapping
    public String mostrarAjustes(Model model, HttpSession session) {
        var resumen = lectorPanelService.obtenerResumen(lectorActual(session));
        if (resumen.isEmpty()) {
            // El registro del lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("lector", resumen.get().lector());
        model.addAttribute("sede", resumen.get().sede());
        return "lector/ajustes";
    }

    @PostMapping("/perfil")
    @ResponseBody
    public Map<String, Object> guardarPerfil(@RequestBody Map<String, String> datos, HttpSession session) {
        Lector l = ajustesService.actualizarPerfil(lectorActual(session),
                datos.get("nombres"), datos.get("apellidos"), datos.get("telefono"));

        // La barra lateral lee el nombre de la sesión: se mantiene al día
        String nombreCompleto = l.getNombres() + " " + l.getApellidos();
        session.setAttribute("lectorNombre", nombreCompleto);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Tus datos se actualizaron correctamente.");
        respuesta.put("nombres", l.getNombres());
        respuesta.put("apellidos", l.getApellidos());
        respuesta.put("telefono", l.getTelefono());
        respuesta.put("nombreCompleto", nombreCompleto);
        return respuesta;
    }

    @PostMapping("/password")
    @ResponseBody
    public Map<String, Object> cambiarPassword(@RequestBody Map<String, String> datos, HttpSession session) {
        ajustesService.cambiarPassword(lectorActual(session),
                datos.get("passwordActual"), datos.get("passwordNueva"), datos.get("passwordConfirmar"));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Tu contraseña se cambió correctamente.");
        return respuesta;
    }

    // Errores de validación -> JSON (la pantalla marca el campo indicado)
    @ExceptionHandler(AjustesException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manejarError(AjustesException ex) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("error", ex.getMessage());
        cuerpo.put("campo", ex.getCampo());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }
}