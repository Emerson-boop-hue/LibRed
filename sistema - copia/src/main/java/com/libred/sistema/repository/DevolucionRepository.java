package com.libred.sistema.repository;

import com.libred.sistema.model.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {
    List<Devolucion> findByLibreriaId(Integer libreriaId);
}