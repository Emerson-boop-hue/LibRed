package com.libred.sistema.repository;

import com.libred.sistema.model.ColaEspera;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ColaEsperaRepository extends JpaRepository<ColaEspera, Integer> {
    long countByRecurso_IdAndEstado(Integer recursoId, String estado);
    List<ColaEspera> findByLector_IdOrderByFechaRegistroDesc(Integer lectorId);
}
