package com.libred.sistema.repository;

import com.libred.sistema.model.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrestamoRepository extends JpaRepository<Prestamo, Integer> {

    // Listar todos los préstamos de una librería específica
    List<Prestamo> findByLibreriaId(Integer libreriaId);

    // Listar préstamos filtrados por librería y estado (Activo, Devuelto, etc.)
    List<Prestamo> findByLibreriaIdAndEstadoPrestamo(Integer libreriaId, String estadoPrestamo);

    // Búsqueda por texto (código de lector, nombres o título del recurso) dentro de la librería
    @Query("SELECT p FROM Prestamo p WHERE p.libreriaId = :libreriaId AND " +
           "(LOWER(p.lector.nombres) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.lector.apellidos) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.lector.codigoLector) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.recurso.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Prestamo> searchByLibreria(@Param("libreriaId") Integer libreriaId, @Param("keyword") String keyword);

    // Conteo para KPIs de préstamos
    long countByLibreriaId(Integer libreriaId);
    
    long countByLibreriaIdAndEstadoPrestamo(Integer libreriaId, String estadoPrestamo);
}