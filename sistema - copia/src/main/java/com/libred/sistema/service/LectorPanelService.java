package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Datos básicos del lector que inició sesión (para la Vista General).
 * Cada registro de lector pertenece a una librería: esa es la "sede" a la que entró en este inicio de sesión.
 */
@Service
public class LectorPanelService {

    /** Lector + la librería (sede) a la que pertenece (sede puede ser null). */
    public record Resumen(Lector lector, Libreria sede) {}

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;

    public LectorPanelService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
    }

    public Optional<Resumen> obtenerResumen(Integer lectorId) {
        return lectorRepository.findById(lectorId).map(lector -> {
            Libreria sede = lector.getLibreriaId() == null
                    ? null
                    : libreriaRepository.findById(lector.getLibreriaId()).orElse(null);
            return new Resumen(lector, sede);
        });
    }
}