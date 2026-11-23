package com.libred.sistema.repository;

import com.libred.sistema.model.MedioPagoPrueba;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/** Acceso a los medios de pago de prueba. */
public interface MedioPagoPruebaRepository extends JpaRepository<MedioPagoPrueba, Integer> {

    // Con bloqueo porque al pagar se descuenta el saldo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MedioPagoPrueba> findByIdentificador(String identificador);

    // Consulta simple (sin bloqueo) para solo comprobar que una cuenta existe
    Optional<MedioPagoPrueba> findByTipoAndIdentificador(String tipo, String identificador);
}