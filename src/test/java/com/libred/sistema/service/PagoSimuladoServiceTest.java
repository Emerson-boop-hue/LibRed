package com.libred.sistema.service;

import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.model.RegistroDtos.SolicitudPago;
import com.libred.sistema.repository.MedioPagoPruebaRepository;
import com.libred.sistema.repository.PagoRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de las validaciones de la pasarela de pago simulada (PagoSimuladoService).
 */
@ExtendWith(MockitoExtension.class)
class PagoSimuladoServiceTest {

    @Mock
    private MedioPagoPruebaRepository medioRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private VerificacionCodigoService codigos;

    @InjectMocks
    private PagoSimuladoService pagoService;

    private PagoSimuladoService.Pagador pagador() {
        return new PagoSimuladoService.Pagador(null, 1, "admin@libreria.com", "RENOVACION");
    }

    @Test
    @DisplayName("CP-55: El algoritmo de Luhn debe aceptar un número de tarjeta válido y rechazar uno alterado")
    void luhn_ValidaNumerosDeTarjeta() {
        assertTrue(PagoSimuladoService.luhn("4111111111111111"));
        assertFalse(PagoSimuladoService.luhn("4111111111111112"));
        assertEquals("4111111111111111", PagoSimuladoService.digitos("4111-1111 1111 1111"));
    }

    @Test
    @DisplayName("CP-56: Debe rechazar el cobro si no se eligió un método de pago")
    void cobrar_SinMetodo_LanzaExcepcion() {
        SolicitudPago solicitud = mock(SolicitudPago.class);

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> pagoService.cobrar(pagador(), PlanSuscripcion.MENSUAL, solicitud));

        assertTrue(ex.getMessage().contains("Elige un método de pago"));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-57: Debe rechazar un número de tarjeta inválido antes de cobrar")
    void cobrar_TarjetaConNumeroInvalido_LanzaExcepcion() {
        SolicitudPago solicitud = mock(SolicitudPago.class);
        when(solicitud.metodo()).thenReturn("tarjeta");
        when(solicitud.numero()).thenReturn("1234");

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> pagoService.cobrar(pagador(), PlanSuscripcion.MENSUAL, solicitud));

        assertTrue(ex.getMessage().contains("Número de tarjeta inválido"));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-58: Debe bloquear nuevos intentos de pago tras demasiados rechazos recientes")
    void cobrar_DemasiadosRechazos_LanzaExcepcion() {
        SolicitudPago solicitud = mock(SolicitudPago.class);
        when(pagoRepository.countByLibreriaIdAndEstadoAndFechaAfter(
                eq(1), eq("RECHAZADO"), any(LocalDateTime.class))).thenReturn(15L);

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> pagoService.cobrar(pagador(), PlanSuscripcion.MENSUAL, solicitud));

        assertTrue(ex.getMessage().contains("máximo de intentos"));
        verify(pagoRepository, never()).save(any());
    }
}