package com.libred.sistema.repository;

import com.libred.sistema.model.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MultaRepository extends JpaRepository<Multa, Integer> {
    boolean existsByLector_IdAndEstadoPagoIgnoreCase(Integer lectorId, String estadoPago);
    List<Multa> findByLector_IdOrderByFechaEmisionDesc(Integer lectorId);
}
