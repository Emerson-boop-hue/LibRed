package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final RecursoRepository recursoRepository;
    private final LectorRepository lectorRepository;

    public PrestamoService(PrestamoRepository prestamoRepository, 
                           RecursoRepository recursoRepository, 
                           LectorRepository lectorRepository) {
        this.prestamoRepository = prestamoRepository;
        this.recursoRepository = recursoRepository;
        this.lectorRepository = lectorRepository;
    }

    public List<Prestamo> listarPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaId(libreriaId);
    }

    // NUEVO MÉTODO HELPER PARA OBTENER PRÉSTAMOS ACTIVOS POR LIBRERÍA
    public List<Prestamo> obtenerPrestamosActivosPorLibreria(Integer libreriaId) {
        return prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "ACTIVO");
    }

    @Transactional
    public void registrarPrestamo(Integer libreriaId, Prestamo prestamo) throws Exception {
        // 1. Validar que el lector exista, pertenezca a la librería y esté habilitado
        Lector lector = lectorRepository.findById(prestamo.getLector().getId())
                .orElseThrow(() -> new Exception("El lector seleccionado no existe."));

        if (!libreriaId.equals(lector.getLibreriaId())) {
            throw new Exception("El lector seleccionado no pertenece a tu librería.");
        }

        if ("SANCIONADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            throw new Exception("No se puede realizar el préstamo. El lector se encuentra SANCIONADO.");
        }

        // 2. Validar que el recurso exista, sea de esta librería y tenga stock disponible
        Recurso recurso = recursoRepository.findById(prestamo.getRecurso().getId())
                .orElseThrow(() -> new Exception("El recurso seleccionado no existe en el inventario."));

        if (recurso.getLibreria() == null || !libreriaId.equals(recurso.getLibreria().getId())) {
            throw new Exception("El artículo seleccionado no pertenece a tu librería.");
        }

        if (recurso.getStockDisponible() <= 0) {
            throw new Exception("No hay stock disponible de este artículo para préstamo.");
        }

        // 3. Asignar metadatos y librería
        prestamo.setLibreriaId(libreriaId);
        prestamo.setEstadoPrestamo("ACTIVO");

        // 4. Descontar stock disponible del recurso usando setters correctos
        recurso.setStockDisponible(recurso.getStockDisponible() - 1);
        if (recurso.getStockDisponible() == 0) {
            recurso.setEstado("AGOTADO");
        } else if (recurso.getStockDisponible() <= 2) {
            recurso.setEstado("CRITICO");
        }
        recursoRepository.save(recurso);

        // 5. Guardar el préstamo
        prestamoRepository.save(prestamo);
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