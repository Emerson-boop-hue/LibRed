package com.libred.sistema.repository;

import com.libred.sistema.model.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MultaRepository extends JpaRepository<Multa, Long> {
    
    List<Multa> findByEstadoPago(String estadoPago);
    
    List<Multa> findByLibreriaIdAndEstadoPago(Long libreriaId, String estadoPago);

    // NUEVO: Verificar si un lector tiene multas pendientes
    boolean existsByLectorIdAndEstadoPago(Integer lectorId, String estadoPago);
}