package com.libred.sistema.repository;

import com.libred.sistema.model.RegistroPendiente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Acceso a los registros de librería pendientes de pago. */
public interface RegistroPendienteRepository extends JpaRepository<RegistroPendiente, Integer> {

    Optional<RegistroPendiente> findByToken(String token);

    // Con bloqueo: evita que dos clics seguidos cobren dos veces o creen dos librerías
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistroPendiente r where r.token = :token")
    Optional<RegistroPendiente> buscarParaActualizar(@Param("token") String token);

    Optional<RegistroPendiente> findFirstByCorreoAndEstadoNotOrderByIdDesc(String correo, String estado);
}