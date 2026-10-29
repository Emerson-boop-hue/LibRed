package com.libred.sistema.repository;

import com.libred.sistema.model.Lector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LectorRepository extends JpaRepository<Lector, Integer> {
    
    Optional<Lector> findByCodigoLector(String codigoLector);

    @Query("SELECT l FROM Lector l WHERE LOWER(l.nombres) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.apellidos) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.codigoLector) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Lector> search(String keyword);

    @Query("SELECT l.codigoLector FROM Lector l ORDER BY l.id DESC LIMIT 1")
    String findLastCodigo();

    long countByEstadoCuenta(String estadoCuenta);

    // --- NUEVOS MÉTODOS FILTRADOS POR LIBRERÍA ---
    List<Lector> findByLibreriaId(Integer libreriaId);

    @Query("SELECT l FROM Lector l WHERE l.libreriaId = :libreriaId AND (LOWER(l.nombres) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.apellidos) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.codigoLector) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Lector> searchByLibreria(Integer libreriaId, String keyword);

    @Query("SELECT l.codigoLector FROM Lector l WHERE l.libreriaId = :libreriaId ORDER BY l.id DESC LIMIT 1")
    String findLastCodigoByLibreria(Integer libreriaId);

    long countByLibreriaId(Integer libreriaId);

    long countByLibreriaIdAndEstadoCuenta(Integer libreriaId, String estadoCuenta);
}