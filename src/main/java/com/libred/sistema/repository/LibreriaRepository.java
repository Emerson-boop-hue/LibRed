package com.libred.sistema.repository;

import com.libred.sistema.model.Libreria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acceso a las librerías. */
public interface LibreriaRepository extends JpaRepository<Libreria, Integer> {
    // Este método buscará automáticamente en la base de datos por el campo 'codigoAdmin'
    Optional<Libreria> findByCodigoAdmin(String codigoAdmin);

    // Usados por el módulo de suscripción para evitar duplicados al registrar una librería nueva
    boolean existsByRuc(String ruc);

    boolean existsByCodigoAdmin(String codigoAdmin);

    // Registro y recuperación de acceso por correo verificado
    boolean existsByCorreoCuenta(String correoCuenta);

    Optional<Libreria> findByCorreoCuentaAndCorreoVerificadoTrue(String correoCuenta);

    // Tarea programada de renovaciones: librerías con renovación automática ya vencidas
    List<Libreria> findByRenovacionAutomaticaTrueAndFechaVencimientoLessThanEqual(LocalDateTime ahora);

    // Aviso previo: librerías SIN renovación automática que vencen pronto
    List<Libreria> findByRenovacionAutomaticaFalseAndFechaVencimientoBetween(LocalDateTime desde, LocalDateTime hasta);
}