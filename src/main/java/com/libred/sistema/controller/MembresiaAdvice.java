package com.libred.sistema.controller;

import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.service.MembresiaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Pone en las pantallas el estado de la membresía: la franja del administrador y el aviso del lector. */
@ControllerAdvice
public class MembresiaAdvice {

    private final MembresiaService membresiaService;
    private final LectorRepository lectorRepository;

    public MembresiaAdvice(MembresiaService membresiaService, LectorRepository lectorRepository) {
        this.membresiaService = membresiaService;
        this.lectorRepository = lectorRepository;
    }

    @ModelAttribute("membresia")
    public MembresiaService.Estado membresia(HttpSession session) {
        return session.getAttribute("libreriaId") instanceof Integer id ? membresiaService.estadoDe(id) : null;
    }

    /** True si la librería donde está el lector no recibe pedidos por tener la membresía suspendida. */
    @ModelAttribute("sedeSuspendida")
    public boolean sedeSuspendida(HttpSession session) {
        if (session.getAttribute("lectorId") instanceof Integer lectorId) {
            return lectorRepository.findById(lectorId)
                    .map(l -> membresiaService.estadoDe(l.getLibreriaId()).escrituraBloqueada())
                    .orElse(false);
        }
        return false;
    }
}
