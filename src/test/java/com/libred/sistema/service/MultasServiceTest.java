package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para validar las reglas de negocio de MultaService.
 */
@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private LectorRepository lectorRepository;

    @Mock
    private PrestamoRepository prestamoRepository;

    @InjectMocks
    private MultaService multaService;

    private Lector lector;
    private Prestamo prestamo;

    @BeforeEach
    void setUp() {
        lector = new Lector();
        lector.setId(10);
        lector.setLibreriaId(1);
        lector.setEstadoCuenta("HABILITADO");

        prestamo = new Prestamo();
        prestamo.setId(50);
        prestamo.setLibreriaId(1);
    }

    /** Multa nueva tal como llega desde el formulario (solo con los ids del lector y del préstamo). */
    private Multa nuevaMultaDeFormulario() {
        Multa multa = new Multa();
        multa.setMonto(new BigDecimal("15.00"));
        multa.setMotivo("Devolución con retraso");

        Lector lectorRef = new Lector();
        lectorRef.setId(10);
        multa.setLector(lectorRef);

        Prestamo prestamoRef = new Prestamo();
        prestamoRef.setId(50);
        multa.setPrestamo(prestamoRef);
        return multa;
    }

    @Test
    @DisplayName("CP-06: Debe rechazar una multa con monto menor o igual a cero")
    void guardarMulta_MontoCero_LanzaExcepcion() {
        Multa multa = nuevaMultaDeFormulario();
        multa.setMonto(BigDecimal.ZERO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> multaService.guardarMulta(1, multa));

        assertTrue(ex.getMessage().contains("mayor a 0"));
        verify(multaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-07: Debe rechazar una multa para un lector de otra librería")
    void guardarMulta_LectorDeOtraLibreria_LanzaExcepcion() {
        lector.setLibreriaId(2);
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> multaService.guardarMulta(1, nuevaMultaDeFormulario()));

        assertTrue(ex.getMessage().contains("no pertenece a tu librería"));
        verify(multaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-08: Debe registrar la multa como PENDIENTE y bloquear al lector")
    void guardarMulta_Nueva_BloqueaLector() {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(prestamoRepository.findById(50)).thenReturn(Optional.of(prestamo));
        when(multaRepository.findByLibreriaIdAndEstadoPago(1L, "PENDIENTE")).thenReturn(List.of());
        when(multaRepository.save(any(Multa.class))).thenAnswer(i -> i.getArgument(0));
        when(multaRepository.existsByLectorIdAndEstadoPago(eq(lector.getId()), eq("PENDIENTE"))).thenReturn(true);

        Multa resultado = multaService.guardarMulta(1, nuevaMultaDeFormulario());

        assertEquals("PENDIENTE", resultado.getEstadoPago());
        assertEquals("BLOQUEADO", lector.getEstadoCuenta());
        verify(lectorRepository, times(1)).save(lector);
    }

    @Test
    @DisplayName("CP-09: Debe marcar la multa como PAGADO y habilitar al lector sin multas pendientes")
    void procesarPago_Exitoso_HabilitaLector() {
        lector.setEstadoCuenta("BLOQUEADO");
        Multa multa = new Multa();
        multa.setId(7L);
        multa.setLibreriaId(1L);
        multa.setEstadoPago("PENDIENTE");
        multa.setLector(lector);

        when(multaRepository.findById(7L)).thenReturn(Optional.of(multa));
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(multaRepository.existsByLectorIdAndEstadoPago(eq(lector.getId()), eq("PENDIENTE"))).thenReturn(false);

        boolean resultado = multaService.procesarPago(1, 7L);

        assertTrue(resultado);
        assertEquals("PAGADO", multa.getEstadoPago());
        assertNotNull(multa.getFechaPago());
        assertEquals("HABILITADO", lector.getEstadoCuenta());
        verify(multaRepository, times(1)).save(multa);
    }

    @Test
    @DisplayName("CP-10: No debe procesar el pago de una multa de otra librería")
    void procesarPago_OtraLibreria_RetornaFalse() {
        Multa multa = new Multa();
        multa.setId(7L);
        multa.setLibreriaId(2L);
        multa.setEstadoPago("PENDIENTE");

        when(multaRepository.findById(7L)).thenReturn(Optional.of(multa));

        boolean resultado = multaService.procesarPago(1, 7L);

        assertFalse(resultado);
        assertEquals("PENDIENTE", multa.getEstadoPago());
        verify(multaRepository, never()).save(any());
    }
}