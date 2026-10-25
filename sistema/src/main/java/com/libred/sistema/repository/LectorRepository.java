package com.libred.sistema.repository;

import com.libred.sistema.model.Lector;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LectorRepository extends JpaRepository<Lector, Integer> {
    // Este método buscará automáticamente en la base de datos por el campo 'codigoLector'
    Optional<Lector> findByCodigoLector(String codigoLector);
}