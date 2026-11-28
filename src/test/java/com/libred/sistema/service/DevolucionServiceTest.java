package com.libred.sistema.service;

import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para validar las reglas de negocio de DevolucionService (devoluciones y cálculo de mora).
 */
@ExtendWith(MockitoExtension.class)
class DevolucionServiceTest {

    @Mock
    private DevolucionRepository devolucionRepository;

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private RecursoRepository recursoRepository;

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private LectorRepository lectorRepository;

    @InjectMocks
    private DevolucionService devolucionService;

    private Lector lector;
    private Recurso recurso;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        lector = new Lector();
        lector.setId(10);
        lector.setLibreriaId(1);
        lector.setEstadoCuenta("HABILITADO");

        recurso = new Recurso();
        recurso.setId(100);
        recurso.setStockTotal(5);
        recurso.setStockDisponible(3);
        recurso.setEstado("NORMAL");

        prestamo = new Prestamo();
        prestamo.setId(50);
        prestamo.setLibreriaId(1);
        prestamo.setEstadoPrestamo("ACTIVO");
        prestamo.setLector(lector);
        prestamo.setRecurso(recurso);
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(2));
    }

    @Test
    @DisplayName("CP-20: Debe registrar una devolución a tiempo sin mora y restaurar el stock")
    void procesarDevolucion_ATiempo_SinMora() throws Exception {
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));
        when(multaRepository.findByLibreriaIdAndEstadoPago(1L, "PENDIENTE")).thenReturn(List.of());
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));

        Devolucion resultado = devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00"));

        assertEquals("DEVUELTO", prestamo.getEstadoPrestamo());
        assertNotNull(prestamo.getFechaDevolucionReal());
        assertEquals(0, resultado.getDiasRetraso());
        assertEquals(0, resultado.getMontoMora().compareTo(BigDecimal.ZERO));
        assertEquals(4, recurso.getStockDisponible());
        verify(prestamoRepository, times(1)).save(prestamo);
        verify(recursoRepository, times(1)).save(recurso);
        verify(lectorRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-21: Debe calcular la mora según los días de retraso y la tarifa diaria")
    void procesarDevolucion_ConRetraso_CalculaMora() throws Exception {
        prestamo.setFechaDevolucionLimite(LocalDate.now().minusDays(3));
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));
        when(multaRepository.findByLibreriaIdAndEstadoPago(1L, "PENDIENTE")).thenReturn(List.of());
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));

        Devolucion resultado = devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.50"));

        assertEquals(3, resultado.getDiasRetraso());
        assertEquals(new BigDecimal("7.50"), resultado.getMontoMora());
        assertEquals(new BigDecimal("7.50"), resultado.getMontoTotal());
    }

    @Test
    @DisplayName("CP-22: Debe rechazar la devolución de un préstamo ya devuelto")
    void procesarDevolucion_YaDevuelto_LanzaExcepcion() {
        prestamo.setEstadoPrestamo("DEVUELTO");
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));

        Exception ex = assertThrows(Exception.class,
                () -> devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00")));

        assertTrue(ex.getMessage().contains("ya fue devuelto"));
        verify(devolucionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-23: Debe rechazar la devolución de un pedido que aún no fue entregado")
    void procesarDevolucion_PorEntregar_LanzaExcepcion() {
        prestamo.setEstadoPrestamo("POR_ENTREGAR");
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));

        Exception ex = assertThrows(Exception.class,
                () -> devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00")));

        assertTrue(ex.getMessage().contains("aún no fue entregado"));
        verify(devolucionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-24: Debe rechazar la devolución de un préstamo de otra librería")
    void procesarDevolucion_OtraLibreria_LanzaExcepcion() {
        prestamo.setLibreriaId(2);
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));

        Exception ex = assertThrows(Exception.class,
                () -> devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00")));

        assertTrue(ex.getMessage().contains("no pertenece a tu librería"));
        verify(devolucionRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-25: Debe cobrar las multas pendientes del préstamo y habilitar al lector")
    void procesarDevolucion_ConMultaPendiente_CobraMultaYHabilitaLector() throws Exception {
        lector.setEstadoCuenta("BLOQUEADO");
        Multa multa = new Multa();
        multa.setPrestamo(prestamo);
        multa.setMonto(new BigDecimal("10.00"));
        multa.setEstadoPago("PENDIENTE");

        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));
        when(multaRepository.findByLibreriaIdAndEstadoPago(1L, "PENDIENTE")).thenReturn(List.of(multa));
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));
        // existsByLectorIdAndEstadoPago devuelve false por defecto: ya no quedan multas pendientes

        Devolucion resultado = devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00"));

        assertEquals("PAGADO", multa.getEstadoPago());
        assertEquals(0, resultado.getMontoMulta().compareTo(new BigDecimal("10.00")));
        assertEquals(0, resultado.getMontoTotal().compareTo(new BigDecimal("10.00")));
        assertEquals("HABILITADO", lector.getEstadoCuenta());
        verify(lectorRepository, times(1)).save(lector);
    }

    @Test
    @DisplayName("CP-26: El stock disponible nunca debe superar el stock total")
    void procesarDevolucion_StockCompleto_NoSuperaElTotal() throws Exception {
        recurso.setStockDisponible(5);
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));
        when(multaRepository.findByLibreriaIdAndEstadoPago(1L, "PENDIENTE")).thenReturn(List.of());
        when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(i -> i.getArgument(0));

        devolucionService.procesarDevolucion(1, 50, new BigDecimal("2.00"));

        assertEquals(5, recurso.getStockDisponible());
    }
}