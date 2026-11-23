package com.libred.sistema.controller;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.service.AjustesException;
import com.libred.sistema.service.PrestamoLectorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Módulo PRÉSTAMOS del lector:
 *  - GET  /lector/prestamos           -> formulario de solicitud + "Mis préstamos"
 *  - POST /lector/prestamos/solicitar -> registra el préstamo (JSON)
 * El interceptor de WebConfig garantiza que hay un lector en la sesión.
 */
@Controller
public class PrestamoLectorController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PrestamoLectorService prestamoLectorService;

    public PrestamoLectorController(PrestamoLectorService prestamoLectorService) {
        this.prestamoLectorService = prestamoLectorService;
    }

    @GetMapping("/lector/prestamos")
    public String mostrar(@RequestParam(name = "recursoId", required = false) Integer recursoId,
                          Model model, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        var pantalla = prestamoLectorService.armarPantalla(lectorId);
        if (pantalla.isEmpty()) {
            // El registro del lector ya no existe en la BD: se cierra la sesión
            session.invalidate();
            return "redirect:/login";
        }
        model.addAttribute("p", pantalla.get());
        model.addAttribute("preseleccionado", recursoId); // viene del botón "Solicitar" del catálogo
        return "lector/prestamos";
    }

    @PostMapping("/lector/prestamos/solicitar")
    @ResponseBody
    public Map<String, Object> solicitar(@RequestBody Map<String, String> datos, HttpSession session) {
        Integer lectorId = (Integer) session.getAttribute("lectorId");

        Integer recursoId = aEntero(datos.get("recursoId"), "articulo", "Selecciona el artículo que deseas.");
        Integer dias = aEntero(datos.get("dias"), "dias", "Selecciona por cuántos días lo necesitas.");

        Prestamo p = prestamoLectorService.solicitar(lectorId, recursoId, dias);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("mensaje", "¡Tu préstamo fue registrado correctamente!");
        r.put("numero", p.getId());
        r.put("articulo", p.getRecurso().getTitulo());
        r.put("codigo", p.getRecurso().getCodigo());
        r.put("categoria", CategoriaRecurso.desdeValor(p.getRecurso().getTipoRecurso()).getEtiqueta());
        r.put("fechaPrestamo", p.getFechaPrestamo().format(FORMATO_FECHA));
        r.put("fechaLimite", p.getFechaDevolucionLimite().format(FORMATO_FECHA));
        r.put("dias", p.getDiasPrestamo());
        r.put("tarifaDia", p.getTarifaDia().toPlainString());
        r.put("monto", p.getMontoServicio().toPlainString());
        return r;
    }

    private Integer aEntero(String texto, String campo, String mensaje) {
        try {
            return Integer.valueOf(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            throw new AjustesException(campo, mensaje);
        }
    }

    // Errores de validación -> JSON (la pantalla muestra el mensaje)
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