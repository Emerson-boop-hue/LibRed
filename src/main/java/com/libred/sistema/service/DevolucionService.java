package com.libred.sistema.service;

import com.libred.sistema.model.*;
import com.libred.sistema.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final PrestamoRepository prestamoRepository;
    private final RecursoRepository recursoRepository;
    private final MultaRepository multaRepository;
    private final LectorRepository lectorRepository;

    public DevolucionService(DevolucionRepository devolucionRepository,
                             PrestamoRepository prestamoRepository,
                             RecursoRepository recursoRepository,
                             MultaRepository multaRepository,
                             LectorRepository lectorRepository) {
        this.devolucionRepository = devolucionRepository;
        this.prestamoRepository = prestamoRepository;
        this.recursoRepository = recursoRepository;
        this.multaRepository = multaRepository;
        this.lectorRepository = lectorRepository;
    }

    @Transactional
    public Devolucion procesarDevolucion(Integer libreriaId, Integer prestamoId, BigDecimal tarifaDiaria) throws Exception {
        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new Exception("El registro de préstamo no existe."));

        // El préstamo debe pertenecer a la librería que está procesando la devolución
        if (!libreriaId.equals(prestamo.getLibreriaId())) {
            throw new Exception("El préstamo no existe o no pertenece a tu librería.");
        }

        if ("DEVUELTO".equalsIgnoreCase(prestamo.getEstadoPrestamo())) {
            throw new Exception("Este préstamo ya fue devuelto anteriormente.");
        }

        // 1. Marcar préstamo como DEVUELTO y guardar fecha real
        prestamo.setEstadoPrestamo("DEVUELTO");
        prestamo.setFechaDevolucionReal(LocalDateTime.now());
        prestamoRepository.save(prestamo);

        // 2. Incrementar stock disponible del recurso y actualizar estado (NORMAL / CRITICO)
        Recurso recurso = prestamo.getRecurso();
        if (recurso != null) {
            recurso.setStockDisponible(recurso.getStockDisponible() + 1);
            if (recurso.getStockDisponible() > 2) {
                recurso.setEstado("NORMAL");
            } else if (recurso.getStockDisponible() > 0) {
                recurso.setEstado("CRITICO");
            }
            recursoRepository.save(recurso);
        }

        // 3. Calcular días de retraso y monto acumulado por mora
        int diasRetraso = 0;
        BigDecimal montoMora = BigDecimal.ZERO;
        if (prestamo.getFechaDevolucionLimite() != null) {
            LocalDate hoy = LocalDate.now();
            LocalDate limite = prestamo.getFechaDevolucionLimite();
            if (hoy.isAfter(limite)) {
                diasRetraso = (int) ChronoUnit.DAYS.between(limite, hoy);
                if (tarifaDiaria != null && tarifaDiaria.compareTo(BigDecimal.ZERO) > 0) {
                    montoMora = tarifaDiaria.multiply(new BigDecimal(diasRetraso));
                }
            }
        }

        // 4. Buscar y saldar multas pendientes de ESTA librería asociadas a este préstamo (Daño / Pérdida)
        BigDecimal montoMulta = BigDecimal.ZERO;
        List<Multa> multasPendientes = multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PENDIENTE");
        for (Multa m : multasPendientes) {
            if (m.getPrestamo() != null && m.getPrestamo().getId().equals(prestamoId)) {
                montoMulta = montoMulta.add(m.getMonto());
                m.setEstadoPago("PAGADO");
                m.setFechaPago(LocalDateTime.now());
                multaRepository.save(m);
            }
        }

        // 5. Guardar la transacción formal de devolución con su mora y multas separadas
        Devolucion devolucion = new Devolucion();
        devolucion.setLibreriaId(prestamo.getLibreriaId());
        devolucion.setPrestamo(prestamo);
        devolucion.setDiasRetraso(diasRetraso);
        devolucion.setMontoMora(montoMora);
        devolucion.setMontoMulta(montoMulta);
        devolucion.setMontoTotal(montoMora.add(montoMulta));
        Devolucion devGuardada = devolucionRepository.save(devolucion);

        // 6. Sincronizar estado del lector (HABILITADO / SANCIONADO)
        if (prestamo.getLector() != null) {
            Lector lector = prestamo.getLector();
            boolean tieneOtrasMultas = multaRepository.existsByLectorIdAndEstadoPago(lector.getId(), "PENDIENTE");
            String estadoFinal = tieneOtrasMultas ? "SANCIONADO" : "HABILITADO";
            if (!estadoFinal.equalsIgnoreCase(lector.getEstadoCuenta())) {
                lector.setEstadoCuenta(estadoFinal);
                lectorRepository.save(lector);
            }
        }

        return devGuardada;
    }

    public List<Devolucion> listarPorLibreria(Integer libreriaId) {
        return devolucionRepository.findByLibreriaId(libreriaId);
    }
}