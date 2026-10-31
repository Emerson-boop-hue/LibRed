package com.libred.sistema.repository;

import com.libred.sistema.model.TarifaCategoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TarifaCategoriaRepository extends JpaRepository<TarifaCategoria, Integer> {

    List<TarifaCategoria> findByLibreriaId(Integer libreriaId);

    Optional<TarifaCategoria> findByLibreriaIdAndCategoria(Integer libreriaId, String categoria);
}