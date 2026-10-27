package com.libred.sistema.repository;

import com.libred.sistema.model.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PrestamoRepository extends JpaRepository<Prestamo, Integer> {
    List<Prestamo> findByLector_IdOrderByFechaPrestamoDesc(Integer lectorId);
    List<Prestamo> findByLector_IdAndEstadoPrestamoInOrderByFechaPrestamoDesc(Integer lectorId, Collection<String> estados);
    long countByLector_IdAndEstadoPrestamoIn(Integer lectorId, Collection<String> estados);
}
