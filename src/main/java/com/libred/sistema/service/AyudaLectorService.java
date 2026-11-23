package com.libred.sistema.service;

import com.libred.sistema.model.AyudaLectorVista;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Arma los datos de Ayuda y Soporte: contacto y reglas de préstamo de la librería donde está el lector. */
@Service
public class AyudaLectorService {

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final PoliticaPrestamoService politicaService;

    public AyudaLectorService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository,
                              PoliticaPrestamoService politicaService) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.politicaService = politicaService;
    }

    /** Vacío si el lector ya no existe (el controlador cierra la sesión). */
    @Transactional(readOnly = true)
    public Optional<AyudaLectorVista> armar(Integer lectorId) {
        Lector lector = lectorRepository.findById(lectorId).orElse(null);
        if (lector == null) {
            return Optional.empty();
        }
        Libreria lib = lector.getLibreriaId() == null ? null : libreriaRepository.findById(lector.getLibreriaId()).orElse(null);
        if (lib == null) {
            return Optional.of(new AyudaLectorVista(null, null, null, null, null, 0, null, java.util.List.of()));
        }
        var tarifas = politicaService.filasDeTarifas(lib.getId()).stream()
                .filter(PoliticaPrestamoService.FilaTarifa::isActiva)
                .toList();
        return Optional.of(new AyudaLectorVista(
                lib.getNombreComercial(), lib.getDireccion(), lib.getTelefonoContacto(), lib.getCorreoContacto(),
                lib.getHorarioAtencion(), politicaService.obtenerMaxDias(lib.getId()),
                politicaService.obtenerMaxActivos(lib.getId()), tarifas));
    }
}