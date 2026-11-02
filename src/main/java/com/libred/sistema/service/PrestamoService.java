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

    // NUEVO MÉTODO HELPER PARA OBTENER PRÉSTAMOS ACTIVOS POR LIBRERÍA
    public List<Prestamo> obtenerPrestamosActivosPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO");
    }

    @Transactional
    public Prestamo registrarPrestamo(Integer libreriaId, Prestamo prestamo) throws Exception {
        // 1. Validar que el lector exista, pertenezca a la librería y esté habilitado
        Lector lector = lectorRepository.findById(prestamo.getLector().getId())
                .orElseThrow(() -> new Exception("El lector seleccionado no existe."));

        if (!libreriaId.equals(lector.getLibreriaId())) {
            throw new Exception("El lector seleccionado no pertenece a tu librería.");
        }

        if ("SANCIONADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            throw new Exception("No se puede realizar el préstamo. El lector se encuentra SANCIONADO.");
        }

        // Límite de préstamos activos por lector (opcional, lo define cada librería en Ajustes)
        Integer maxActivos = politicaService.obtenerMaxActivos(libreriaId);
        if (maxActivos != null && prestamoRepository.countByLectorIdAndEstadoPrestamoIn(
                lector.getId(), List.of("ACTIVO", "VENCIDO")) >= maxActivos) {
            throw new Exception("El lector ya tiene " + maxActivos
                    + (maxActivos == 1 ? " préstamo activo" : " préstamos activos")
                    + ", que es el máximo permitido por tu librería.");
        }

        // 2. Validar que el recurso exista, sea de esta librería y tenga stock disponible
        // (se bloquea la fila del artículo mientras se descuenta el stock)
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

        // 3. Validar el plazo según la política de la librería y calcular el servicio (días x tarifa de la categoría)
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

        // 4. Asignar metadatos, librería y datos del servicio
        prestamo.setLector(lector);
        prestamo.setRecurso(recurso);
        prestamo.setLibreriaId(libreriaId);
        prestamo.setEstadoPrestamo("ACTIVO");
        prestamo.setDiasPrestamo((int) dias);
        prestamo.setTarifaDia(tarifa);
        prestamo.setMontoServicio(tarifa.multiply(BigDecimal.valueOf(dias)).setScale(2, RoundingMode.HALF_UP));

        // 5. Descontar stock disponible del recurso usando setters correctos
        recurso.setStockDisponible(recurso.getStockDisponible() - 1);
        if (recurso.getStockDisponible() == 0) {
            recurso.setEstado("AGOTADO");
        } else if (recurso.getStockDisponible() <= 2) {
            recurso.setEstado("CRITICO");
        }
        recursoRepository.save(recurso);

        // 6. Guardar el préstamo
        return prestamoRepository.save(prestamo);
    }

    @Transactional
    public void registrarDevolucion(Integer prestamoId) throws Exception {
        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new Exception("El registro de préstamo no existe."));

        if ("DEVUELTO".equalsIgnoreCase(prestamo.getEstadoPrestamo())) {
            throw new Exception("Este préstamo ya fue devuelto anteriormente.");
        }

        prestamo.setEstadoPrestamo("DEVUELTO");
        prestamo.setFechaDevolucionReal(LocalDateTime.now());
        prestamoRepository.save(prestamo);

        // Retornar el stock al recurso
        Recurso recurso = prestamo.getRecurso();
        recurso.setStockDisponible(recurso.getStockDisponible() + 1);
        
        if (recurso.getStockDisponible() > 2) {
            recurso.setEstado("NORMAL");
        } else if (recurso.getStockDisponible() > 0) {
            recurso.setEstado("CRITICO");
        }
        recursoRepository.save(recurso);
    }
}