package com.libred.sistema.repository;

import com.libred.sistema.model.TicketSoporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso a los tickets de soporte. */
public interface TicketSoporteRepository extends JpaRepository<TicketSoporte, Integer> {

    List<TicketSoporte> findByLibreriaIdOrderByIdDesc(Integer libreriaId);

    long countByLibreriaIdAndEstado(Integer libreriaId, String estado);
}