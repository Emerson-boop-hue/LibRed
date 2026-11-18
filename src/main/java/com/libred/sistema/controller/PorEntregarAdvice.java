package com.libred.sistema.controller;

import com.libred.sistema.service.PrestamoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Pone en todas las pantallas del administrador el número de pedidos "Por entregar" (el numerito junto al menú). Si
 * quien navega no es un administrador, no consulta nada.
 */
@ControllerAdvice
public class PorEntregarAdvice {

    private final PrestamoService prestamoService;

    public PorEntregarAdvice(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @ModelAttribute("porEntregarCount")
    public Long porEntregarCount(HttpSession session) {
        Object id = session.getAttribute("libreriaId");
        if (id instanceof Integer libreriaId) {
            return prestamoService.contarPorEntregar(libreriaId);
        }
        return 0L;
    }
}