package com.libred.sistema.repository;

import com.libred.sistema.model.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
/**
 * PrestamoRepository
 * Repositorio para la entidad Prestamo, proporcionando métodos para realizar operaciones CRUD y consultas personalizadas en la base de datos.
 * Permite buscar préstamos por librería, estado, lector y recurso, así como contar préstamos
 * por diferentes criterios.
 */
public interface PrestamoRepository extends JpaRepository<Prestamo, Integer> {

    List<Prestamo> findByLibreriaId(Integer libreriaId);

    List<Prestamo> findByLibreriaIdAndEstadoPrestamo(Integer libreriaId, String estadoPrestamo);

    @Query("SELECT p FROM Prestamo p WHERE p.libreriaId = :libreriaId AND " +
           "(LOWER(p.lector.nombres) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.lector.apellidos) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.lector.codigoLector) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.recurso.titulo) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Prestamo> searchByLibreria(@Param("libreriaId") Integer libreriaId, @Param("keyword") String keyword);

    long countByLibreriaId(Integer libreriaId);
    
    long countByLibreriaIdAndEstadoPrestamo(Integer libreriaId, String estadoPrestamo);

    List<Prestamo> findByLectorIdOrderByFechaPrestamoDesc(Integer lectorId);

    long countByLectorIdAndEstadoPrestamoIn(Integer lectorId, List<String> estados);

    // Unidades de un artículo que están prestadas ahora mismo (el stock total no puede quedar por debajo de ese
    // número)
    long countByRecursoIdAndEstadoPrestamoIn(Integer recursoId, List<String> estados);

    boolean existsByLectorIdAndRecursoIdAndEstadoPrestamoIn(Integer lectorId, Integer recursoId, List<String> estados);

    long countByLectorIdAndEstadoPrestamoAndFechaDevolucionLimiteBefore(Integer lectorId, String estado, LocalDate fecha);
}