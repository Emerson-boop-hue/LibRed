package com.libred.sistema.repository;

import com.libred.sistema.model.Libreria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LibreriaRepository extends JpaRepository<Libreria, Integer> {
    // Este método buscará automáticamente en la base de datos por el campo 'codigoAdmin'
    Optional<Libreria> findByCodigoAdmin(String codigoAdmin);
}
