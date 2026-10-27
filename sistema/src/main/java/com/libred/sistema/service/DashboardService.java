package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DashboardService {
    private static final List<String> ACTIVOS = List.of("ACTIVO", "VIGENTE", "EN_CURSO");
    private final LectorRepository lectorRepository;
    private final PrestamoRepository prestamoRepository;
    private final MultaRepository multaRepository;

    public DashboardService(LectorRepository lectorRepository, PrestamoRepository prestamoRepository, MultaRepository multaRepository) {
        this.lectorRepository = lectorRepository;
        this.prestamoRepository = prestamoRepository;
        this.multaRepository = multaRepository;
    }

    public Lector obtenerLector(Integer id) {
        return lectorRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No se encontró el lector de la sesión."));
    }
    public long contarActivos(Integer id) { return prestamoRepository.countByLector_IdAndEstadoPrestamoIn(id, ACTIVOS); }
    public List<Prestamo> actividadReciente(Integer id) { return prestamoRepository.findByLector_IdOrderByFechaPrestamoDesc(id).stream().limit(5).toList(); }
    public boolean tieneMultasPendientes(Integer id) { return multaRepository.existsByLector_IdAndEstadoPagoIgnoreCase(id, "PENDIENTE"); }
    public java.util.List<com.libred.sistema.model.Multa> obtenerMultas(Integer id) { return multaRepository.findByLector_IdOrderByFechaEmisionDesc(id); }
}
