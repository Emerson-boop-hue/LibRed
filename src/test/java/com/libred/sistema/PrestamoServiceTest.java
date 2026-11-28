package com.libred.sistema;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import com.libred.sistema.service.PoliticaPrestamoService;
import com.libred.sistema.service.PrestamoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para validar las reglas de negocio de PrestamoService.
 */
@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private RecursoRepository recursoRepository;

    @Mock
    private LectorRepository lectorRepository;

    @Mock
    private PoliticaPrestamoService politicaService;

    @InjectMocks
    private PrestamoService prestamoService;

    private Libreria libreria;
    private Lector lector;
    private Recurso recurso;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        libreria = new Libreria();
        libreria.setId(1);

        lector = new Lector();
        lector.setId(10);
        lector.setLibreriaId(1);
        lector.setEstadoCuenta("ACTIVO");

        recurso = new Recurso();
        recurso.setId(100);
        recurso.setLibreria(libreria);
        recurso.setTipoRecurso("LIBRO");
        recurso.setStockTotal(5);
        recurso.setStockDisponible(5);
        recurso.setEstado("NORMAL");

        prestamo = new Prestamo();
        prestamo.setLector(lector);
        prestamo.setRecurso(recurso);
        prestamo.setOrigen("ADMIN");
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(3));
    }

    @Test
    @DisplayName("CP-01: Debe registrar un préstamo exitosamente y descontar stock")
    void registrarPrestamo_Exitoso() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(politicaService.obtenerMaxActivos(1)).thenReturn(3);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), anyList())).thenReturn(0L);
        when(recursoRepository.bloquearPorId(100)).thenReturn(Optional.of(recurso));
        when(politicaService.categoriaActiva(1, "LIBRO")).thenReturn(true);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(politicaService.tarifaDe(1, "LIBRO")).thenReturn(new BigDecimal("2.50"));
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        Prestamo resultado = prestamoService.registrarPrestamo(1, prestamo);

        assertNotNull(resultado);
        assertEquals("ACTIVO", resultado.getEstadoPrestamo());
        assertEquals(4, recurso.getStockDisponible());
        assertEquals(new BigDecimal("7.50"), resultado.getMontoServicio());
        verify(recursoRepository, times(1)).save(recurso);
        verify(prestamoRepository, times(1)).save(prestamo);
    }

    @Test
    @DisplayName("CP-02: Debe rechazar el préstamo si el lector está BLOQUEADO")
    void registrarPrestamo_LectorBloqueado_LanzaExcepcion() {
        lector.setEstadoCuenta("BLOQUEADO");
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));

        Exception ex = assertThrows(Exception.class, () -> prestamoService.registrarPrestamo(1, prestamo));

        assertTrue(ex.getMessage().contains("BLOQUEADO"));
        verify(prestamoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-03: Debe rechazar el préstamo si el recurso no tiene stock disponible")
    void registrarPrestamo_SinStock_LanzaExcepcion() {
        recurso.setStockDisponible(0);
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(politicaService.obtenerMaxActivos(1)).thenReturn(3);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), anyList())).thenReturn(0L);
        when(recursoRepository.bloquearPorId(100)).thenReturn(Optional.of(recurso));
        when(politicaService.categoriaActiva(1, "LIBRO")).thenReturn(true);

        Exception ex = assertThrows(Exception.class, () -> prestamoService.registrarPrestamo(1, prestamo));

        assertTrue(ex.getMessage().contains("No hay stock disponible"));
        verify(prestamoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-04: Debe rechazar el préstamo si supera el plazo máximo de días")
    void registrarPrestamo_ExcedeDiasMaximos_LanzaExcepcion() {
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(15));
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(politicaService.obtenerMaxActivos(1)).thenReturn(3);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), anyList())).thenReturn(0L);
        when(recursoRepository.bloquearPorId(100)).thenReturn(Optional.of(recurso));
        when(politicaService.categoriaActiva(1, "LIBRO")).thenReturn(true);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);

        Exception ex = assertThrows(Exception.class, () -> prestamoService.registrarPrestamo(1, prestamo));

        assertTrue(ex.getMessage().contains("plazo máximo"));
        verify(prestamoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-05: Debe cancelar un pedido pendiente y restaurar el stock")
    void cancelarPedido_Exitoso() throws Exception {
        recurso.setStockDisponible(2);
        recurso.setEstado("CRITICO");
        prestamo.setId(50);
        prestamo.setLibreriaId(1);
        prestamo.setEstadoPrestamo("POR_ENTREGAR");

        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));

        Prestamo resultado = prestamoService.cancelarPedido(1, 50);

        assertEquals("CANCELADO", resultado.getEstadoPrestamo());
        assertEquals(3, recurso.getStockDisponible());
        assertEquals("NORMAL", recurso.getEstado());
        verify(recursoRepository, times(1)).save(recurso);
    }
}
