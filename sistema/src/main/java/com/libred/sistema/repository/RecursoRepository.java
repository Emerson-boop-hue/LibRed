package com.libred.sistema.repository;

import com.libred.sistema.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecursoRepository extends JpaRepository<Recurso, Integer> {
    List<Recurso> findByLibreria_Id(Integer libreriaId);
}
