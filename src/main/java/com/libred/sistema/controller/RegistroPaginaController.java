package com.libred.sistema.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Página de registro de librerías nuevas (reemplaza al antiguo asistente en ventana del login). */
@Controller
public class RegistroPaginaController {

    @GetMapping("/registro")
    public String pagina() {
        return "registro";
    }
}