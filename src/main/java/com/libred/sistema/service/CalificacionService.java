package com.libred.sistema.service;

import com.libred.sistema.model.Calificacion;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.CalificacionRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Calificaciones de los lectores. Solo se puede calificar un préstamo propio que ya fue devuelto,
 * y cada préstamo tiene una única calificación (si el lector la cambia, se reemplaza).
 */
@Service
public class CalificacionService {

    private static final String[] ETIQUETAS = {"", "Muy malo", "Malo", "Regular", "Bueno", "Excelente"};

    private final CalificacionRepository calificacionRepository;
    private final PrestamoRepository prestamoRepository;

    public CalificacionService(CalificacionRepository calificacionRepository, PrestamoRepository prestamoRepository) {
        this.calificacionRepository = calificacionRepository;
        this.prestamoRepository = prestamoRepository;
    }

    @Transactional
    public Calificacion calificar(Integer lectorId, Integer prestamoId, Integer estrellas) {
        if (estrellas == null || estrellas < 1 || estrellas > 5) {
            throw new AjustesException("estrellas", "Elige una calificación de 1 a 5 estrellas.");
        }
        if (prestamoId == null) {
            throw new AjustesException(null, "No se indicó qué préstamo calificar.");
        }

        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new AjustesException(null, "El préstamo que intentas calificar no existe."));
        if (prestamo.getLector() == null || !lectorId.equals(prestamo.getLector().getId())) {
            throw new AjustesException(null, "Solo puedes calificar tus propios préstamos.");
        }
        if (!"DEVUELTO".equalsIgnoreCase(prestamo.getEstadoPrestamo())) {
            throw new AjustesException(null, "Podrás calificar este préstamo cuando lo devuelvas.");
        }

        Calificacion c = calificacionRepository.findByPrestamoId(prestamoId).orElse(null);
        if (c == null) {
            c = new Calificacion();
        } else {
            // Ya estaba calificado: solo se permite cambiarla UNA vez
            if (c.isModificada()) {
                throw new AjustesException(null, "Esta calificación ya fue modificada una vez y no se puede cambiar de nuevo.");
            }
            if (c.getEstrellas().equals(estrellas)) {
                throw new AjustesException("estrellas", "Elegiste la misma calificación. Elige otra para cambiarla.");
            }
            c.setModificada(true);
        }
        c.setPrestamoId(prestamoId);
        c.setLibreriaId(prestamo.getLibreriaId());
        c.setLectorId(lectorId);
        c.setEstrellas(estrellas);
        return calificacionRepository.save(c);
    }

    /** Préstamos devueltos del lector que todavía no calificó. */
    @Transactional(readOnly = true)
    public long contarPendientes(Integer lectorId) {
        long devueltos = prestamoRepository.countByLectorIdAndEstadoPrestamoIn(lectorId, List.of("DEVUELTO"));
        return Math.max(0, devueltos - calificacionRepository.countByLectorId(lectorId));
    }

    /** Datos para la tarjeta de la Vista General: pendientes, calificadas, promedio y devueltos. */
    public record Resumen(long pendientes, long calificadas, String promedio, long devueltos) {}

    @Transactional(readOnly = true)
    public Resumen resumen(Integer lectorId) {
        List<Calificacion> hechas = calificacionRepository.findByLectorId(lectorId);
        double prom = hechas.stream().mapToInt(Calificacion::getEstrellas).average().orElse(0);
        long devueltos = prestamoRepository.countByLectorIdAndEstadoPrestamoIn(lectorId, List.of("DEVUELTO"));
        return new Resumen(Math.max(0, devueltos - hechas.size()), hechas.size(),
                String.format(java.util.Locale.US, "%.1f", prom), devueltos);
    }

    public String etiqueta(int estrellas) {
        return estrellas >= 1 && estrellas <= 5 ? ETIQUETAS[estrellas] : "";
    }
}