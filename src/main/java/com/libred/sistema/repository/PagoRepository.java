package com.libred.sistema.repository;

import com.libred.sistema.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Acceso a los pagos de suscripción. */
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    long countByRegistroIdAndEstadoAndFechaAfter(Integer registroId, String estado, LocalDateTime desde);

    long countByLibreriaIdAndEstadoAndFechaAfter(Integer libreriaId, String estado, LocalDateTime desde);

    // Último pago aprobado de una librería (para mostrar con qué método paga en "Ver plan actual")
    Optional<Pago> findFirstByLibreriaIdAndEstadoOrderByIdDesc(Integer libreriaId, String estado);
}