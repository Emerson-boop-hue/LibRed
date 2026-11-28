package com.libred.sistema.service;

import com.libred.sistema.model.CodigoVerificacion;
import com.libred.sistema.repository.CodigoVerificacionRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de los códigos de verificación de 6 dígitos (VerificacionCodigoService):
 * generación, vigencia, límite de intentos y uso único.
 */
@ExtendWith(MockitoExtension.class)
class VerificacionCodigoServiceTest {

    private static final String DESTINO = "admin@libreria.com";
    private static final String PROPOSITO = VerificacionCodigoService.REGISTRO;
    private static final VerificacionCodigoService.Politica POLITICA = VerificacionCodigoService.CORREO;

    @Mock
    private CodigoVerificacionRepository repositorio;

    @InjectMocks
    private VerificacionCodigoService servicio;

    private CodigoVerificacion generarYCapturar(String[] codigoClaro) {
        when(repositorio.findFirstByDestinoAndPropositoOrderByIdDesc(DESTINO, PROPOSITO)).thenReturn(Optional.empty());
        when(repositorio.countByDestinoAndPropositoAndFechaCreacionAfter(eq(DESTINO), eq(PROPOSITO), any(LocalDateTime.class)))
                .thenReturn(0L);

        codigoClaro[0] = servicio.generar(DESTINO, PROPOSITO, POLITICA);

        ArgumentCaptor<CodigoVerificacion> captor = ArgumentCaptor.forClass(CodigoVerificacion.class);
        verify(repositorio).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("CP-34: Debe generar un código de 6 dígitos y guardar solo su huella SHA-256")
    void generar_GuardaSoloLaHuella() {
        String[] codigo = new String[1];

        CodigoVerificacion guardado = generarYCapturar(codigo);

        assertTrue(codigo[0].matches("\\d{6}"));
        assertNotEquals(codigo[0], guardado.getCodigoHash());
        assertEquals(64, guardado.getCodigoHash().length());
        assertEquals(DESTINO, guardado.getDestino());
        assertTrue(guardado.getExpiraEn().isAfter(LocalDateTime.now()));
        verify(repositorio, times(1)).invalidarAnteriores(DESTINO, PROPOSITO);
    }

    @Test
    @DisplayName("CP-35: Debe rechazar pedir otro código antes de que termine el tiempo de espera")
    void generar_MuyPronto_LanzaExcepcion() {
        CodigoVerificacion anterior = new CodigoVerificacion();
        anterior.setFechaCreacion(LocalDateTime.now());
        when(repositorio.findFirstByDestinoAndPropositoOrderByIdDesc(DESTINO, PROPOSITO)).thenReturn(Optional.of(anterior));

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> servicio.generar(DESTINO, PROPOSITO, POLITICA));

        assertTrue(ex.getMessage().contains("Espera"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("CP-36: Debe rechazar pedir más códigos que el máximo permitido por hora")
    void generar_DemasiadosPorHora_LanzaExcepcion() {
        when(repositorio.findFirstByDestinoAndPropositoOrderByIdDesc(DESTINO, PROPOSITO)).thenReturn(Optional.empty());
        when(repositorio.countByDestinoAndPropositoAndFechaCreacionAfter(eq(DESTINO), eq(PROPOSITO), any(LocalDateTime.class)))
                .thenReturn((long) POLITICA.maxPorHora());

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> servicio.generar(DESTINO, PROPOSITO, POLITICA));

        assertTrue(ex.getMessage().contains("demasiados códigos"));
        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("CP-37: Debe aceptar el código correcto y consumirlo para que no se reutilice")
    void validar_CodigoCorrecto_LoConsume() {
        String[] codigo = new String[1];
        CodigoVerificacion guardado = generarYCapturar(codigo);
        when(repositorio.findFirstByDestinoAndPropositoAndUsadoFalseOrderByIdDesc(DESTINO, PROPOSITO))
                .thenReturn(Optional.of(guardado));

        assertDoesNotThrow(() -> servicio.validar(DESTINO, PROPOSITO, codigo[0], POLITICA, "codigo"));

        // una vez al generar y otra al marcarlo como usado
        verify(repositorio, times(2)).save(guardado);
    }

    @Test
    @DisplayName("CP-38: Debe rechazar un código incorrecto y descontar un intento")
    void validar_CodigoIncorrecto_RestaIntento() {
        CodigoVerificacion guardado = new CodigoVerificacion();
        guardado.setDestino(DESTINO);
        guardado.setProposito(PROPOSITO);
        guardado.setCodigoHash("huella-que-no-coincide");
        guardado.setExpiraEn(LocalDateTime.now().plusMinutes(10));
        guardado.setIntentos(0);
        when(repositorio.findFirstByDestinoAndPropositoAndUsadoFalseOrderByIdDesc(DESTINO, PROPOSITO))
                .thenReturn(Optional.of(guardado));

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> servicio.validar(DESTINO, PROPOSITO, "000000", POLITICA, "codigo"));

        assertTrue(ex.getMessage().contains("quedan 4 intentos"));
        assertEquals(1, guardado.getIntentos());
        verify(repositorio, times(1)).save(guardado);
    }

    @Test
    @DisplayName("CP-39: Debe rechazar un código vencido")
    void validar_CodigoVencido_LanzaExcepcion() {
        CodigoVerificacion guardado = new CodigoVerificacion();
        guardado.setDestino(DESTINO);
        guardado.setProposito(PROPOSITO);
        guardado.setCodigoHash("cualquiera");
        guardado.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        when(repositorio.findFirstByDestinoAndPropositoAndUsadoFalseOrderByIdDesc(DESTINO, PROPOSITO))
                .thenReturn(Optional.of(guardado));

        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> servicio.validar(DESTINO, PROPOSITO, "123456", POLITICA, "codigo"));

        assertTrue(ex.getMessage().contains("venció"));
        verify(repositorio, times(1)).save(guardado);
    }

    @Test
    @DisplayName("CP-40: Debe rechazar un código que no tiene 6 dígitos sin consultar la base de datos")
    void validar_FormatoInvalido_LanzaExcepcion() {
        SuscripcionException ex = assertThrows(SuscripcionException.class,
                () -> servicio.validar(DESTINO, PROPOSITO, "12ab", POLITICA, "codigo"));

        assertTrue(ex.getMessage().contains("6 dígitos"));
        verifyNoInteractions(repositorio);
    }
}