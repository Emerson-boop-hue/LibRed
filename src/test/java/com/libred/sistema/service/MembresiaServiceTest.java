package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias del estado de la membresía de una librería (MembresiaService):
 * ACTIVA, GRACIA y SUSPENDIDA.
 */
@ExtendWith(MockitoExtension.class)
class MembresiaServiceTest {

    @Mock
    private LibreriaRepository libreriaRepository;

    @InjectMocks
    private MembresiaService membresiaService;

    private Libreria conVencimiento(LocalDateTime vencimiento) {
        Libreria libreria = new Libreria();
        libreria.setFechaVencimiento(vencimiento);
        return libreria;
    }

    @Test
    @DisplayName("CP-41: Una librería sin fecha de vencimiento debe estar ACTIVA y sin aviso")
    void estadoDe_SinFechaDeVencimiento_EsActiva() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(null));

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertEquals("", estado.aviso());
        assertFalse(estado.escrituraBloqueada());
    }

    @Test
    @DisplayName("CP-42: Con más de 30 días de vigencia debe estar ACTIVA y sin franja de aviso")
    void estadoDe_MasDe30Dias_SinAviso() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().plusDays(45)));

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertEquals("", estado.aviso());
        assertFalse(estado.mostrarFranja());
    }

    @Test
    @DisplayName("CP-43: Con 15 días de vigencia debe mostrar un aviso informativo")
    void estadoDe_QuinceDias_AvisoInfo() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().plusDays(15)));

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertEquals("info", estado.aviso());
    }

    @Test
    @DisplayName("CP-44: Con 5 días de vigencia debe mostrar un aviso ámbar")
    void estadoDe_CincoDias_AvisoAmbar() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().plusDays(5)));

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertEquals("ambar", estado.aviso());
        assertEquals(5L, estado.diasRestantes());
    }

    @Test
    @DisplayName("CP-45: Con 2 días de vigencia debe mostrar un aviso rojo")
    void estadoDe_DosDias_AvisoRojo() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().plusDays(2)));

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertEquals("rojo", estado.aviso());
    }

    @Test
    @DisplayName("CP-46: Una membresía vencida hace 1 día debe estar en GRACIA y seguir permitiendo escritura")
    void estadoDe_VencidaHaceUnDia_EsGracia() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().minusDays(1)));

        assertEquals(MembresiaService.Fase.GRACIA, estado.fase());
        assertEquals("rojo", estado.aviso());
        assertFalse(estado.escrituraBloqueada());
    }

    @Test
    @DisplayName("CP-47: Una membresía vencida hace 10 días debe estar SUSPENDIDA y bloquear la escritura")
    void estadoDe_VencidaHaceDiezDias_EsSuspendida() {
        MembresiaService.Estado estado = membresiaService.estadoDe(conVencimiento(LocalDateTime.now().minusDays(10)));

        assertEquals(MembresiaService.Fase.SUSPENDIDA, estado.fase());
        assertEquals(0L, estado.diasRestantes());
        assertTrue(estado.escrituraBloqueada());
    }

    @Test
    @DisplayName("CP-48: Si la librería no existe debe devolverse el estado ACTIVA por defecto")
    void estadoDe_LibreriaInexistente_EsActiva() {
        when(libreriaRepository.findById(99)).thenReturn(Optional.empty());

        MembresiaService.Estado estado = membresiaService.estadoDe(99);

        assertEquals(MembresiaService.Fase.ACTIVA, estado.fase());
        assertFalse(estado.escrituraBloqueada());
    }
}