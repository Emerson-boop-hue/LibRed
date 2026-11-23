package com.libred.sistema.repository;

import com.libred.sistema.model.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Acceso a las multas. */
public interface MultaRepository extends JpaRepository<Multa, Long> {
    
    List<Multa> findByLibreriaIdAndEstadoPago(Long libreriaId, String estadoPago);

    // Verificar si un lector tiene multas pendientes
    boolean existsByLectorIdAndEstadoPago(Integer lectorId, String estadoPago);

    // Multas de un lector, de la más reciente a la más antigua (Historial del lector)
    @Query("SELECT m FROM Multa m WHERE m.lector.id = :lectorId ORDER BY m.id DESC")
    List<Multa> buscarPorLector(@Param("lectorId") Integer lectorId);
}