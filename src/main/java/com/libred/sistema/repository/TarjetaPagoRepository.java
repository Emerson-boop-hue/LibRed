package com.libred.sistema.repository;

import com.libred.sistema.model.TarjetaPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TarjetaPagoRepository extends JpaRepository<TarjetaPago, Integer> {

    // Cada librería tiene como máximo una tarjeta
    Optional<TarjetaPago> findByLibreriaId(Integer libreriaId);
}