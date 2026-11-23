package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Registro y entrega de préstamos. */
@Service
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final RecursoRepository recursoRepository;
    private final LectorRepository lectorRepository;
    private final PoliticaPrestamoService politicaService;

    public PrestamoService(PrestamoRepository prestamoRepository, 
                           RecursoRepository recursoRepository, 
                           LectorRepository lectorRepository,
                           PoliticaPrestamoService politicaService) {
        this.prestamoRepository = prestamoRepository;
        this.recursoRepository = recursoRepository;
        this.lectorRepository = lectorRepository;
        this.politicaService = politicaService;
    }

    public List<Prestamo> listarPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaId(libreriaId);
    }

    public List<Prestamo> obtenerPrestamosActivosPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO");
    }

    @Transactional
    public Prestamo registrarPrestamo(Integer libreriaId, Prestamo prestamo) throws Exception {
        Lector lector = lectorRepository.findById(prestamo.getLector().getId())
                .orElseThrow(() -> new Exception("El lector seleccionado no existe."));

        if (!libreriaId.equals(lector.getLibreriaId())) {
            throw new Exception("El lector seleccionado no pertenece a tu librería.");
        }

        if ("BLOQUEADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            throw new Exception("No se puede realizar el préstamo. El lector se encuentra BLOQUEADO.");
        }

        // Límite de préstamos activos por lector (opcional, lo define cada librería en Ajustes)
        Integer maxActivos = politicaService.obtenerMaxActivos(libreriaId);
        if (maxActivos != null && prestamoRepository.countByLectorIdAndEstadoPrestamoIn(
                lector.getId(), List.of("ACTIVO", "VENCIDO", "POR_ENTREGAR")) >= maxActivos) {
            throw new Exception("El lector ya tiene " + maxActivos
                    + (maxActivos == 1 ? " préstamo activo" : " préstamos activos")
                    + ", que es el máximo permitido por tu librería.");
        }

        Recurso recurso = recursoRepository.bloquearPorId(prestamo.getRecurso().getId())
                .orElseThrow(() -> new Exception("El recurso seleccionado no existe en el inventario."));

        if (recurso.getLibreria() == null || !libreriaId.equals(recurso.getLibreria().getId())) {
            throw new Exception("El artículo seleccionado no pertenece a tu librería.");
        }

        if (!politicaService.categoriaActiva(libreriaId, recurso.getTipoRecurso())) {
            throw new Exception("La categoría de este artículo está desactivada en tu librería. Actívala en Ajustes para prestarlo.");
        }

        if (recurso.getStockDisponible() <= 0) {
            throw new Exception("No hay stock disponible de este artículo para préstamo.");
        }

        LocalDate hoy = LocalDate.now();
        LocalDate limite = prestamo.getFechaDevolucionLimite();
        if (limite == null) {
            throw new Exception("Indica la fecha límite de devolución.");
        }
        long dias = ChronoUnit.DAYS.between(hoy, limite);
        int maxDias = politicaService.obtenerMaxDias(libreriaId);
        if (dias < 1) {
            throw new Exception("La fecha límite de devolución debe ser posterior a hoy.");
        }
        if (dias > maxDias) {
            throw new Exception("El plazo máximo de préstamo en esta librería es de " + maxDias
                    + (maxDias == 1 ? " día." : " días."));
        }
        BigDecimal tarifa = politicaService.tarifaDe(libreriaId, recurso.getTipoRecurso());

        prestamo.setLector(lector);
        prestamo.setRecurso(recurso);
        prestamo.setLibreriaId(libreriaId);
        // Pedido del lector: queda POR_ENTREGAR (el stock ya se reserva). En mostrador: ACTIVO
        prestamo.setEstadoPrestamo("LECTOR".equals(prestamo.getOrigen()) ? "POR_ENTREGAR" : "ACTIVO");
        prestamo.setDiasPrestamo((int) dias);
        prestamo.setTarifaDia(tarifa);
        prestamo.setMontoServicio(tarifa.multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP));

        // El servicio se cobra al registrar solo cuando el préstamo se hace en mostrador
        if ("LECTOR".equals(prestamo.getOrigen())) {
            prestamo.setServicioCobrado(false);
            prestamo.setFechaCobroServicio(null);
        } else {
            prestamo.setServicioCobrado(true);
            prestamo.setFechaCobroServicio(LocalDateTime.now());
        }

        recurso.setStockDisponible(recurso.getStockDisponible() - 1);
        if (recurso.getStockDisponible() == 0) {
            recurso.setEstado("AGOTADO");
        } else if (recurso.getStockDisponible() <= 2) {
            recurso.setEstado("CRITICO");
        }
        recursoRepository.save(recurso);

        return prestamoRepository.save(prestamo);
    }

    // POR ENTREGAR (pedidos que hizo el lector)

    /** Pedidos de lectores de ESTA librería que esperan ser entregados (los más antiguos primero). */
    @Transactional(readOnly = true)
    public List<Prestamo> listarPorEntregar(Integer libreriaId) {
        List<Prestamo> lista = new java.util.ArrayList<>(
                prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "POR_ENTREGAR"));
        lista.sort(java.util.Comparator.comparing(Prestamo::getFechaPrestamo,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return lista;
    }

    public long contarPorEntregar(Integer libreriaId) {
        return prestamoRepository.countByLibreriaIdAndEstadoPrestamo(libreriaId, "POR_ENTREGAR");
    }

    private Prestamo pedidoPorEntregar(Integer libreriaId, Integer prestamoId) throws Exception {
        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new Exception("El pedido ya no existe."));
        if (!libreriaId.equals(prestamo.getLibreriaId())) {
            throw new Exception("El pedido no pertenece a tu librería.");
        }
        if (!"POR_ENTREGAR".equalsIgnoreCase(prestamo.getEstadoPrestamo())) {
            throw new Exception("Este pedido ya fue entregado o cancelado.");
        }
        return prestamo;
    }

    /**
     * Entrega el artículo y cobra el servicio: el préstamo pasa a ACTIVO y el plazo empieza hoy (hoy + los días que
     * pidió el lector).
     */
    @Transactional
    public Prestamo confirmarEntrega(Integer libreriaId, Integer prestamoId) throws Exception {
        Prestamo prestamo = pedidoPorEntregar(libreriaId, prestamoId);
        Lector lector = prestamo.getLector();
        if (lector != null && "BLOQUEADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            throw new Exception("El lector está BLOQUEADO por multas pendientes. Regulariza su cuenta antes de entregarle el artículo.");
        }
        int dias = prestamo.getDiasPrestamo() != null && prestamo.getDiasPrestamo() > 0 ? prestamo.getDiasPrestamo() : 1;
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(dias));
        prestamo.setEstadoPrestamo("ACTIVO");
        prestamo.setServicioCobrado(true);
        prestamo.setFechaCobroServicio(LocalDateTime.now());
        return prestamoRepository.save(prestamo);
    }

    /** Cancela un pedido que nadie recogió: el artículo vuelve al stock. */
    @Transactional
    public Prestamo cancelarPedido(Integer libreriaId, Integer prestamoId) throws Exception {
        Prestamo prestamo = pedidoPorEntregar(libreriaId, prestamoId);
        prestamo.setEstadoPrestamo("CANCELADO");
        prestamoRepository.save(prestamo);

        Recurso recurso = prestamo.getRecurso();
        if (recurso != null) {
            recurso.setStockDisponible(Math.min(recurso.getStockTotal(), recurso.getStockDisponible() + 1));
            if (recurso.getStockDisponible() > 2) {
                recurso.setEstado("NORMAL");
            } else if (recurso.getStockDisponible() > 0) {
                recurso.setEstado("CRITICO");
            }
            recursoRepository.save(recurso);
        }
        return prestamo;
    }
}