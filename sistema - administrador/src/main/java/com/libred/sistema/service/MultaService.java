package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MultaService {

    private final MultaRepository multaRepository;
    private final LectorRepository lectorRepository;
    private final PrestamoRepository prestamoRepository;

    public MultaService(MultaRepository multaRepository,
                        LectorRepository lectorRepository,
                        PrestamoRepository prestamoRepository) {
        this.multaRepository = multaRepository;
        this.lectorRepository = lectorRepository;
        this.prestamoRepository = prestamoRepository;
    }

    // Multas pendientes SOLO de la librería indicada
    @Transactional(readOnly = true) // Mantiene la sesión abierta para leer el objeto Prestamo en las vistas
    public List<Multa> obtenerMultasPendientes(Integer libreriaId) {
        return multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PENDIENTE");
    }

    public Optional<Multa> obtenerPorId(Long id) {
        return multaRepository.findById(id);
    }

    public Multa guardarMulta(Integer libreriaId, Multa multa) {
        Long libId = libreriaId.longValue();

        if (multa.getEstadoPago() == null || multa.getEstadoPago().isBlank()) {
            multa.setEstadoPago("PENDIENTE");
        }
        
        if (multa.getMonto() == null || multa.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la multa debe ser mayor a 0.");
        }

        if (multa.getMotivo() == null || multa.getMotivo().isBlank()) {
            throw new IllegalArgumentException("El motivo de la infracción es obligatorio.");
        }

        Multa multaGuardada;

        // 1. EDICIÓN DIRECTA POR ID (solo si la multa es de esta librería)
        if (multa.getId() != null) {
            Optional<Multa> existenteOpt = multaRepository.findById(multa.getId());
            if (existenteOpt.isPresent()) {
                Multa existente = existenteOpt.get();
                if (!libId.equals(existente.getLibreriaId())) {
                    throw new IllegalArgumentException("La multa no pertenece a tu librería.");
                }
                existente.setMotivo(multa.getMotivo());
                existente.setMonto(multa.getMonto());
                multaGuardada = multaRepository.save(existente);
                actualizarEstadoLector(multaGuardada.getLector());
                return multaGuardada;
            }
        }

        // 2. VALIDAR QUE LECTOR Y PRÉSTAMO SEAN DE ESTA LIBRERÍA
        if (multa.getLector() == null || multa.getLector().getId() == null) {
            throw new IllegalArgumentException("Selecciona un lector.");
        }
        if (multa.getPrestamo() == null || multa.getPrestamo().getId() == null) {
            throw new IllegalArgumentException("Selecciona un préstamo.");
        }

        Lector lectorBD = lectorRepository.findById(multa.getLector().getId())
                .orElseThrow(() -> new IllegalArgumentException("El lector seleccionado no existe."));
        if (!libreriaId.equals(lectorBD.getLibreriaId())) {
            throw new IllegalArgumentException("El lector seleccionado no pertenece a tu librería.");
        }

        Prestamo prestamoBD = prestamoRepository.findById(multa.getPrestamo().getId())
                .orElseThrow(() -> new IllegalArgumentException("El préstamo seleccionado no existe."));
        if (!libreriaId.equals(prestamoBD.getLibreriaId())) {
            throw new IllegalArgumentException("El préstamo seleccionado no pertenece a tu librería.");
        }

        // 3. SOBREESCRIBIR SI YA EXISTE MULTA PENDIENTE EN ESTE PRÉSTAMO (solo de esta librería)
        List<Multa> pendientes = multaRepository.findByLibreriaIdAndEstadoPago(libId, "PENDIENTE");
        for (Multa mExistente : pendientes) {
            if (mExistente.getPrestamo() != null && mExistente.getPrestamo().getId().equals(prestamoBD.getId())) {
                mExistente.setMotivo(multa.getMotivo());
                mExistente.setMonto(multa.getMonto());
                multaGuardada = multaRepository.save(mExistente);
                actualizarEstadoLector(multaGuardada.getLector());
                return multaGuardada;
            }
        }

        // 4. REGISTRO NUEVO (siempre queda ligado a la librería de la sesión)
        multa.setLibreriaId(libId);
        multa.setLector(lectorBD);
        multa.setPrestamo(prestamoBD);
        multaGuardada = multaRepository.save(multa);
        actualizarEstadoLector(multaGuardada.getLector());

        return multaGuardada;
    }

    public boolean procesarPago(Integer libreriaId, Long id) {
        Optional<Multa> multaOpt = multaRepository.findById(id);
        if (multaOpt.isPresent()) {
            Multa multa = multaOpt.get();

            // Solo se puede cobrar una multa de la propia librería
            if (!Long.valueOf(libreriaId.longValue()).equals(multa.getLibreriaId())) {
                return false;
            }

            multa.setEstadoPago("PAGADO");
            multa.setFechaPago(LocalDateTime.now());
            multaRepository.save(multa);

            actualizarEstadoLector(multa.getLector());

            return true;
        }
        return false;
    }

    private void actualizarEstadoLector(Lector lectorFront) {
        if (lectorFront != null && lectorFront.getId() != null) {
            Lector lectorCompleto = lectorRepository.findById(lectorFront.getId()).orElse(null);
            if (lectorCompleto != null) {
                boolean tienePendientes = multaRepository.existsByLectorIdAndEstadoPago(lectorCompleto.getId(), "PENDIENTE");
                String estadoReal = tienePendientes ? "SANCIONADO" : "HABILITADO";
                if (!estadoReal.equals(lectorCompleto.getEstadoCuenta())) {
                    lectorCompleto.setEstadoCuenta(estadoReal);
                    lectorRepository.save(lectorCompleto);
                }
            }
        }
    }
}