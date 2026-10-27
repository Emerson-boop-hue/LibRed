
package com.libred.sistema.repository;

import com.libred.sistema.model.SolicitudSoporte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolicitudSoporteRepository
        extends JpaRepository<SolicitudSoporte, Integer> {

    List<SolicitudSoporte> findByLectorIdOrderByFechaCreacionDesc(
            Integer lectorId
    );
}
