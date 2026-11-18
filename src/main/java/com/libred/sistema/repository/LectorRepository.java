package com.libred.sistema.repository;

import com.libred.sistema.model.Lector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Acceso a los lectores. */
public interface LectorRepository extends JpaRepository<Lector, Integer> {
    
    // Un mismo código puede existir en varias librerías (una fila por librería)
    List<Lector> findAllByCodigoLector(String codigoLector);

    boolean existsByCodigoLector(String codigoLector);

    // Celular guardado solo con dígitos (lo usan el registro y la recuperación de acceso)
    List<Lector> findAllByTelefono(String telefono);

    boolean existsByTelefono(String telefono);

    // Reglas de duplicados POR LIBRERÍA (el mismo celular o nombre completo sí puede existir en otra librería).
    // El nombre se compara sin importar mayúsculas ni tildes (lo decide el cotejamiento de MySQL).
    List<Lector> findAllByLibreriaIdAndTelefono(Integer libreriaId, String telefono);

    List<Lector> findAllByLibreriaIdAndNombresIgnoreCaseAndApellidosIgnoreCase(Integer libreriaId, String nombres, String apellidos);

    @Query("SELECT l.codigoLector FROM Lector l ORDER BY l.id DESC LIMIT 1")
    String findLastCodigo();

    List<Lector> findByLibreriaId(Integer libreriaId);

    @Query("SELECT l FROM Lector l WHERE l.libreriaId = :libreriaId AND (LOWER(l.nombres) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.apellidos) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(l.codigoLector) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Lector> searchByLibreria(Integer libreriaId, String keyword);

    long countByLibreriaId(Integer libreriaId);

    long countByLibreriaIdAndEstadoCuenta(Integer libreriaId, String estadoCuenta);
}