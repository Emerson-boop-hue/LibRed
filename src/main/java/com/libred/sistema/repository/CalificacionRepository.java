package com.libred.sistema.repository;

import com.libred.sistema.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CalificacionRepository extends JpaRepository<Calificacion, Integer> {

    Optional<Calificacion> findByPrestamoId(Integer prestamoId);

    List<Calificacion> findByLectorId(Integer lectorId);

    long countByLectorId(Integer lectorId);

    /** Para Red Global: cuántos votos de cada puntaje (1..5) tiene cada librería. Filas: [libreriaId, estrellas, cantidad]. */
    @Query("SELECT c.libreriaId, c.estrellas, COUNT(c) FROM Calificacion c GROUP BY c.libreriaId, c.estrellas")
    List<Object[]> distribucionPorLibreria();
}