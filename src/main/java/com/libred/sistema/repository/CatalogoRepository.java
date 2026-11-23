package com.libred.sistema.repository;

import com.libred.sistema.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas del CATÁLOGO DEL LECTOR (solo lectura).
 * Trabaja sobre la misma tabla "recurso" que usa el inventario del admin, pero con su propio repositorio
 * para no mezclar los dos módulos.
 *
 * Los filtros llegan SIEMPRE como texto (vacío = "no filtrar"), así no hay parámetros nulos en la consulta.
 */
public interface CatalogoRepository extends JpaRepository<Recurso, Integer> {

    @Query("""
            SELECT r FROM Recurso r
            WHERE r.libreria.id = :libreriaId
              AND (LOWER(r.titulo) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(r.codigo) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND LOWER(COALESCE(r.autorOCreador, '')) LIKE LOWER(CONCAT('%', :autor, '%'))
              AND (:categoria = '' OR r.tipoRecurso = :categoria)
              AND (:disponibilidad = ''
                   OR (:disponibilidad = 'DISPONIBLE' AND r.stockDisponible > 0)
                   OR (:disponibilidad = 'AGOTADO' AND r.stockDisponible <= 0))
            ORDER BY r.titulo
            """)
    List<Recurso> buscarEnCatalogo(@Param("libreriaId") Integer libreriaId,
                                   @Param("texto") String texto,
                                   @Param("autor") String autor,
                                   @Param("categoria") String categoria,
                                   @Param("disponibilidad") String disponibilidad);

    long countByLibreriaId(Integer libreriaId);
}