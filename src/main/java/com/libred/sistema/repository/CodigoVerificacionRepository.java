package com.libred.sistema.repository;

import com.libred.sistema.model.CodigoVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Acceso a los códigos de verificación de un solo uso. */
public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Integer> {

    // Último código pedido (usado o no): sirve para el tiempo de espera entre reenvíos
    Optional<CodigoVerificacion> findFirstByDestinoAndPropositoOrderByIdDesc(String destino, String proposito);

    // Código vigente que todavía se puede usar
    Optional<CodigoVerificacion> findFirstByDestinoAndPropositoAndUsadoFalseOrderByIdDesc(String destino, String proposito);

    long countByDestinoAndPropositoAndFechaCreacionAfter(String destino, String proposito, LocalDateTime desde);

    // Al pedir un código nuevo, los anteriores dejan de servir
    @Modifying
    @Query("update CodigoVerificacion c set c.usado = true where c.destino = :destino and c.proposito = :proposito and c.usado = false")
    int invalidarAnteriores(@Param("destino") String destino, @Param("proposito") String proposito);
}