package com.libred.sistema.service;

import com.libred.sistema.model.ColaEspera;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.ColaEsperaRepository;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class PrestamoService {
    private static final List<String> ESTADOS_ACTIVOS = List.of("ACTIVO", "VIGENTE", "EN_CURSO");
    private final PrestamoRepository prestamoRepository;
    private final RecursoRepository recursoRepository;
    private final LectorRepository lectorRepository;
    private final MultaRepository multaRepository;
    private final ColaEsperaRepository colaRepository;

    @Value("${libred.prestamos.limite-activos:3}")
    private int limitePrestamosActivos;

    public PrestamoService(PrestamoRepository prestamoRepository, RecursoRepository recursoRepository,
            LectorRepository lectorRepository, MultaRepository multaRepository, ColaEsperaRepository colaRepository) {
        this.prestamoRepository = prestamoRepository;
        this.recursoRepository = recursoRepository;
        this.lectorRepository = lectorRepository;
        this.multaRepository = multaRepository;
        this.colaRepository = colaRepository;
    }

    public List<Prestamo> obtenerPrestamos(Integer lectorId) {
        return prestamoRepository.findByLector_IdOrderByFechaPrestamoDesc(lectorId);
    }

    public List<Prestamo> obtenerActivos(Integer lectorId) {
        return prestamoRepository.findByLector_IdAndEstadoPrestamoInOrderByFechaPrestamoDesc(lectorId, ESTADOS_ACTIVOS);
    }

    @Transactional
    public ResultadoSolicitud solicitar(Integer lectorId, Integer recursoId, int plazoDias) {
        if (plazoDias < 1 || plazoDias > 30) throw new IllegalArgumentException("El plazo debe ser de 1 a 30 días.");
        Lector lector = lectorRepository.findById(lectorId).orElseThrow(() -> new IllegalArgumentException("No se encontró el lector de la sesión."));
        String estadoCuenta = lector.getEstadoCuenta() == null ? "" : lector.getEstadoCuenta().toUpperCase();
        if (!(estadoCuenta.equals("ACTIVO") || estadoCuenta.equals("HABILITADO"))) {
            throw new IllegalArgumentException("Tu cuenta no está habilitada para solicitar préstamos.");
        }
        if (multaRepository.existsByLector_IdAndEstadoPagoIgnoreCase(lectorId, "PENDIENTE")) {
            throw new IllegalArgumentException("Tienes multas pendientes. Regularízalas antes de solicitar un préstamo.");
        }
        long activos = prestamoRepository.countByLector_IdAndEstadoPrestamoIn(lectorId, ESTADOS_ACTIVOS);
        if (activos >= limitePrestamosActivos) {
            throw new IllegalArgumentException("Alcanzaste el límite de préstamos activos (" + limitePrestamosActivos + ").");
        }
        Recurso recurso = recursoRepository.findById(recursoId).orElseThrow(() -> new IllegalArgumentException("El recurso no existe."));
        if (recurso.getStockDisponible() == null || recurso.getStockDisponible() <= 0) {
            long posicion = colaRepository.countByRecurso_IdAndEstado(recursoId, "ESPERANDO") + 1;
            ColaEspera espera = new ColaEspera();
            espera.setLector(lector);
            espera.setRecurso(recurso);
            espera.setPosicion((int) posicion);
            espera.setEstado("ESPERANDO");
            colaRepository.save(espera);
            return new ResultadoSolicitud(null, (int) posicion);
        }
        recurso.setStockDisponible(recurso.getStockDisponible() - 1);
        recursoRepository.save(recurso);
        Prestamo prestamo = new Prestamo();
        prestamo.setLector(lector);
        prestamo.setRecurso(recurso);
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(plazoDias));
        prestamo.setEstadoPrestamo("ACTIVO");
        return new ResultadoSolicitud(prestamoRepository.save(prestamo), null);
    }

    public record ResultadoSolicitud(Prestamo prestamo, Integer posicionCola) {
        public boolean estaEnCola() { return posicionCola != null; }
    }
}
