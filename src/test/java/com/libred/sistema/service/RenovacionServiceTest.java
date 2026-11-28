package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MedioPagoPruebaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la tarea programada de renovación de membresías (RenovacionService).
 * Esta clase está en el mismo paquete que el servicio porque renovarVencidas() y
 * avisarProximasAVencer() son métodos de acceso de paquete.
 */
@ExtendWith(MockitoExtension.class)
class RenovacionServiceTest {

    @Mock
    private LibreriaRepository libreriaRepository;

    @Mock
    private MedioPagoPruebaRepository medioRepository;

    @Mock
    private PagoSimuladoService pagoService;

    @Mock
    private SuscripcionService suscripcionService;

    @Mock
    private CorreoSistemaService correoService;

    @Mock
    private PlatformTransactionManager txManager;

    @InjectMocks
    private RenovacionService renovacionService;

    private Libreria libreria;

    @BeforeEach
    void setUp() {
        libreria = new Libreria();
        libreria.setId(1);
        libreria.setRenovacionAutomatica(true);
        libreria.setFechaVencimiento(LocalDateTime.now().minusDays(1));
        libreria.setIntentosRenovacion(0);
    }

    @Test
    @DisplayName("CP-15: Debe registrar un intento fallido si la librería no tiene método de pago guardado")
    void renovarVencidas_SinMedioDePago_RegistraIntentoFallido() {
        when(libreriaRepository.findByRenovacionAutomaticaTrueAndFechaVencimientoLessThanEqual(any(LocalDateTime.class)))
                .thenReturn(List.of(libreria));
        when(libreriaRepository.findById(1)).thenReturn(Optional.of(libreria));

        renovacionService.renovarVencidas();

        assertEquals(1, libreria.getIntentosRenovacion());
        assertTrue(libreria.isRenovacionAutomatica());
        assertNotNull(libreria.getProximoIntentoRenovacion());
        verify(libreriaRepository, times(1)).save(libreria);
        verify(pagoService, never()).cobrarRenovacion(any(), any(), any());
    }

    @Test
    @DisplayName("CP-16: Debe desactivar la renovación automática al tercer intento fallido")
    void renovarVencidas_TercerIntentoFallido_DesactivaRenovacion() {
        libreria.setIntentosRenovacion(2);
        when(libreriaRepository.findByRenovacionAutomaticaTrueAndFechaVencimientoLessThanEqual(any(LocalDateTime.class)))
                .thenReturn(List.of(libreria));
        when(libreriaRepository.findById(1)).thenReturn(Optional.of(libreria));

        renovacionService.renovarVencidas();

        assertEquals(3, libreria.getIntentosRenovacion());
        assertFalse(libreria.isRenovacionAutomatica());
        verify(libreriaRepository, times(1)).save(libreria);
    }

    @Test
    @DisplayName("CP-17: Debe avisar el vencimiento una sola vez por periodo")
    void avisarProximasAVencer_EnviaCorreoUnaSolaVez() {
        libreria.setRenovacionAutomatica(false);
        libreria.setFechaVencimiento(LocalDateTime.now().plusDays(2));
        libreria.setCorreoCuenta("admin@libreria.com");
        libreria.setNombreComercial("Librería Central");
        libreria.setCodigoAdmin("ADM-001");

        when(libreriaRepository.findByRenovacionAutomaticaFalseAndFechaVencimientoBetween(
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(libreria));

        // Primera ejecución: envía el aviso y registra el periodo avisado
        renovacionService.avisarProximasAVencer();
        verify(correoService, times(1)).enviarEnSegundoPlano(eq("admin@libreria.com"), anyString(), anyString());
        assertEquals(libreria.getFechaVencimiento(), libreria.getAvisoVencimientoPara());

        // Segunda ejecución (la tarea corre cada 10 minutos): no repite el aviso
        renovacionService.avisarProximasAVencer();
        verify(correoService, times(1)).enviarEnSegundoPlano(anyString(), anyString(), anyString());
    }
}