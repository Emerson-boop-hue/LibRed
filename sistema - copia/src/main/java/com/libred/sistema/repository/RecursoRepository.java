package com.libred.sistema.repository;

import com.libred.sistema.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RecursoRepository extends JpaRepository<Recurso, Integer> {
    
    List<Recurso> findByLibreriaId(Integer libreriaId);

    // Búsqueda optimizada por Título o Autor + Categoría, filtrado estrictamente por librería
    @Query("SELECT r FROM Recurso r WHERE r.libreria.id = :libreriaId AND " +
           "(:keyword IS NULL OR LOWER(r.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(r.autorOCreador) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:categoria IS NULL OR :categoria = '' OR r.tipoRecurso = :categoria)")
    List<Recurso> filtrarRecursos(@Param("libreriaId") Integer libreriaId,
                                  @Param("keyword") String keyword,
                                  @Param("categoria") String categoria);

    boolean existsByLibreriaIdAndTituloIgnoreCase(Integer libreriaId, String titulo);

    boolean existsByCodigo(String codigo);

    // NUEVO: el código del recurso solo debe ser único dentro de SU librería (cada librería tiene su LIB-001)
    boolean existsByLibreriaIdAndCodigo(Integer libreriaId, String codigo);

    long countByLibreriaIdAndTipoRecurso(Integer libreriaId, String tipoRecurso);
}