package com.libred.sistema.repository;

import com.libred.sistema.model.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {
    List<Devolucion> findByLibreriaId(Integer libreriaId);

    // Devoluciones de los préstamos de un lector (Historial del lector)
    @Query("SELECT d FROM Devolucion d WHERE d.prestamo.lector.id = :lectorId")
    List<Devolucion> buscarPorLector(@Param("lectorId") Integer lectorId);
}