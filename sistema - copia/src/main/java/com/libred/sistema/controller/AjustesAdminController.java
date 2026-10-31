package com.libred.sistema.controller;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TarjetaPago;
import com.libred.sistema.service.AjustesAdminService;
import com.libred.sistema.service.AjustesException;
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
 * Módulo AJUSTES del administrador: datos de la librería, contraseña y tarjeta de pago.
 * La pantalla guarda con fetch (JSON). El interceptor de WebConfig garantiza que hay una librería en la sesión.
 */
@Controller
@RequestMapping("/admin/ajustes")
public class AjustesAdminController {

    private final AjustesAdminService ajustesService;

    public AjustesAdminController(AjustesAdminService ajustesService) {
        this.ajustesService = ajustesService;
    }

    // Librería del admin que inició sesión
    private Integer libreriaActual(HttpSession session) {
        return (Integer) session.getAttribute("libreriaId");
    }

    @GetMapping
    public String mostrarAjustes(Model model, HttpSession session) {
        Integer libreriaId = libreriaActual(session);
        model.addAttribute("libreria", ajustesService.obtenerLibreria(libreriaId));
        model.addAttribute("tarjeta", ajustesService.obtenerTarjeta(libreriaId).orElse(null));
        return "admin/ajustes";
    }

    @PostMapping("/datos")
    @ResponseBody
    public Map<String, Object> guardarDatos(@RequestBody Map<String, String> datos, HttpSession session) {
        Libreria l = ajustesService.actualizarDatos(libreriaActual(session),
                datos.get("nombreComercial"), datos.get("ruc"), datos.get("direccion"),
                datos.get("telefonoContacto"), datos.get("correoContacto"));

        // La tarjeta de la barra lateral lee el nombre de la sesión: se mantiene al día
        session.setAttribute("libreriaNombre", l.getNombreComercial());

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Los datos de tu librería se actualizaron correctamente.");
        respuesta.put("nombreComercial", l.getNombreComercial());
        return respuesta;
    }

    @PostMapping("/password")
    @ResponseBody
    public Map<String, Object> cambiarPassword(@RequestBody Map<String, String> datos, HttpSession session) {
        ajustesService.cambiarPassword(libreriaActual(session),
                datos.get("passwordActual"), datos.get("passwordNueva"), datos.get("passwordConfirmar"));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Tu contraseña se cambió correctamente.");
        return respuesta;
    }

    @PostMapping("/tarjeta")
    @ResponseBody
    public Map<String, Object> guardarTarjeta(@RequestBody Map<String, String> datos, HttpSession session) {
        TarjetaPago t = ajustesService.guardarTarjeta(libreriaActual(session),
                datos.get("titular"), datos.get("marca"), datos.get("ultimosDigitos"),
                datos.get("venceMes"), datos.get("venceAnio"));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Tu método de pago se guardó correctamente.");
        respuesta.put("marca", t.getMarca());
        respuesta.put("ultimosDigitos", t.getUltimosDigitos());
        respuesta.put("titular", t.getTitular());
        respuesta.put("vencimiento", t.getVencimientoTexto());
        return respuesta;
    }

    @PostMapping("/tarjeta/eliminar")
    @ResponseBody
    public Map<String, Object> quitarTarjeta(HttpSession session) {
        ajustesService.quitarTarjeta(libreriaActual(session));

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("mensaje", "Se quitó tu método de pago.");
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