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

/**
 * Servicio para gestionar los préstamos de recursos en la librería, incluyendo la creación de préstamos,
 * la entrega de pedidos pendientes, la cancelación y el control de políticas y stock.
 *
 * @version 1.0
 */
@Service
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final RecursoRepository recursoRepository;
    private final LectorRepository lectorRepository;
    private final PoliticaPrestamoService politicaService;

    /**
     * Constructor para la inyección de dependencias de los repositorios y servicios de políticas.
     *
     * @param prestamoRepository repositorio de acceso a datos de préstamos
     * @param recursoRepository  repositorio de acceso a datos de recursos bibliográficos
     * @param lectorRepository   repositorio de acceso a datos de lectores
     * @param politicaService    servicio de validación de políticas de préstamo
     */
    public PrestamoService(PrestamoRepository prestamoRepository, 
                           RecursoRepository recursoRepository, 
                           LectorRepository lectorRepository,
                           PoliticaPrestamoService politicaService) {
        this.prestamoRepository = prestamoRepository;
        this.recursoRepository = recursoRepository;
        this.lectorRepository = lectorRepository;
        this.politicaService = politicaService;
    }

    /**
     * Lista todos los préstamos históricos y actuales asociados a una librería.
     *
     * @param libreriaId identificador único de la librería
     * @return lista completa de préstamos de la librería
     */
    public List<Prestamo> listarPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaId(libreriaId);
    }

    /**
     * Obtiene los préstamos que se encuentran actualmente en estado ACTIVO dentro de una librería.
     *
     * @param libreriaId identificador único de la librería
     * @return lista de préstamos activos
     */
    public List<Prestamo> obtenerPrestamosActivosPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO");
    }

    /**
     * Registra un nuevo préstamo validando pertenencia a la librería, estado del lector,
     * límite de préstamos activos, disponibilidad de stock, vigencia de categoría y plazos permitidos.
     *
     * @param libreriaId identificador único de la librería donde se realiza la operación
     * @param prestamo   entidad con los datos del préstamo, lector y recurso solicitado
     * @return el préstamo persistido en la base de datos con sus tarifas y estados calculados
     * @throws Exception si el lector o recurso no existen, están bloqueados, sin stock o incumplen políticas
     */
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
        prestamo.setEstadoPrestamo("LECTOR".equals(prestamo.getOrigen()) ? "POR_ENTREGAR" : "ACTIVO");
        prestamo.setDiasPrestamo((int) dias);
        prestamo.setTarifaDia(tarifa);
        prestamo.setMontoServicio(tarifa.multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP));

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

    /**
     * Lista los pedidos realizados por lectores que están pendientes de entrega física (POR_ENTREGAR),
     * ordenados cronológicamente por fecha de solicitud.
     *
     * @param libreriaId identificador único de la librería
     * @return lista ordenada de préstamos pendientes de entrega
     */
    @Transactional(readOnly = true)
    public List<Prestamo> listarPorEntregar(Integer libreriaId) {
        List<Prestamo> lista = new java.util.ArrayList<>(
                prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "POR_ENTREGAR"));
        lista.sort(java.util.Comparator.comparing(Prestamo::getFechaPrestamo,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return lista;
    }

    /**
     * Cuenta la cantidad total de pedidos en estado POR_ENTREGAR de una librería.
     *
     * @param libreriaId identificador único de la librería
     * @return número total de pedidos pendientes de entrega
     */
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
     * Confirma la entrega física de un pedido pendiente, verificando que el lector no esté bloqueado,
     * activando el préstamo y registrando el cobro del servicio.
     *
     * @param libreriaId identificador único de la librería
     * @param prestamoId identificador único del préstamo pendiente
     * @return el préstamo actualizado en estado ACTIVO
     * @throws Exception si el pedido no existe, no pertenece a la librería o el lector está bloqueado
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
/**
* Cancela un pedido pendiente de entrega y restaura el stock disponible del recurso bibliográfico.
*
* @param libreriaId identificador único de la librería
* @param prestamoId identificador único del préstamo a cancelar
* @return el préstamo actualizado en estado CANCELADO
* @throws Exception si el pedido no existe o no se encuentra en estado POR_ENTREGAR
*/
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