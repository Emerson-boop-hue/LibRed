package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del flujo en que el lector solicita su propio préstamo desde el portal Lector
 * (PrestamoLectorService).
 */
@ExtendWith(MockitoExtension.class)
class PrestamoLectorServiceTest {

    private static final List<String> EN_CURSO = List.of("ACTIVO", "VENCIDO", "POR_ENTREGAR");
    private static final MembresiaService.Estado ACTIVA =
            new MembresiaService.Estado(MembresiaService.Fase.ACTIVA, 10, "ambar");
    private static final MembresiaService.Estado SUSPENDIDA =
            new MembresiaService.Estado(MembresiaService.Fase.SUSPENDIDA, 0, "rojo");

    @Mock
    private LectorRepository lectorRepository;

    @Mock
    private LibreriaRepository libreriaRepository;

    @Mock
    private RecursoRepository recursoRepository;

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private PrestamoService prestamoService;

    @Mock
    private PoliticaPrestamoService politicaService;

    @Mock
    private MembresiaService membresiaService;

    @InjectMocks
    private PrestamoLectorService prestamoLectorService;

    private Lector lector;

    @BeforeEach
    void setUp() {
        lector = new Lector();
        lector.setId(10);
        lector.setLibreriaId(1);
        lector.setEstadoCuenta("HABILITADO");
    }

    @Test
    @DisplayName("CP-59: Debe registrar el préstamo solicitado por el lector con origen LECTOR y la fecha límite pedida")
    void solicitar_Valido_RegistraPrestamo() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(politicaService.obtenerMaxActivos(1)).thenReturn(null); // sin límite de préstamos activos
        when(prestamoService.registrarPrestamo(eq(1), any(Prestamo.class))).thenAnswer(i -> i.getArgument(1));

        Prestamo resultado = prestamoLectorService.solicitar(10, 100, 3);

        assertNotNull(resultado);
        ArgumentCaptor<Prestamo> captor = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoService, times(1)).registrarPrestamo(eq(1), captor.capture());
        assertEquals("LECTOR", captor.getValue().getOrigen());
        assertEquals(LocalDate.now().plusDays(3), captor.getValue().getFechaDevolucionLimite());
    }

    @Test
    @DisplayName("CP-60: Debe rechazar la solicitud si el registro del lector ya no existe")
    void solicitar_LectorInexistente_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(99)).thenReturn(Optional.empty());

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(99, 100, 3));

        assertTrue(ex.getMessage().contains("ya no existe"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-61: Debe rechazar la solicitud si la librería tiene la membresía suspendida")
    void solicitar_MembresiaSuspendida_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(SUSPENDIDA);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("no está recibiendo pedidos"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-62: Debe exigir que el lector seleccione un artículo")
    void solicitar_SinArticulo_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, null, 3));

        assertTrue(ex.getMessage().contains("Selecciona el artículo"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-63: Debe rechazar un plazo mayor al máximo de días de la librería")
    void solicitar_DiasFueraDeRango_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 15));

        assertTrue(ex.getMessage().contains("máximo de 7 días"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-64: Debe rechazar la solicitud de un lector con la cuenta BLOQUEADA")
    void solicitar_LectorBloqueado_LanzaExcepcion() throws Exception {
        lector.setEstadoCuenta("BLOQUEADO");
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("BLOQUEADA"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-65: Debe rechazar la solicitud si el lector tiene préstamos vencidos")
    void solicitar_ConPrestamoVencido_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), eq(List.of("VENCIDO")))).thenReturn(1L);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("préstamos vencidos"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-66: Debe rechazar la solicitud si el lector alcanzó el límite de préstamos activos")
    void solicitar_LimiteDeActivos_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(politicaService.obtenerMaxActivos(1)).thenReturn(2);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), eq(List.of("VENCIDO")))).thenReturn(0L);
        when(prestamoRepository.countByLectorIdAndEstadoPrestamoIn(eq(10), eq(EN_CURSO))).thenReturn(2L);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("límite de 2 préstamos activos"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-67: Debe rechazar un artículo que el lector ya tiene en préstamo")
    void solicitar_ArticuloDuplicado_LanzaExcepcion() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(politicaService.obtenerMaxActivos(1)).thenReturn(null); // sin límite de préstamos activos
        when(prestamoRepository.existsByLectorIdAndRecursoIdAndEstadoPrestamoIn(eq(10), eq(100), eq(EN_CURSO)))
                .thenReturn(true);

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("Ya tienes este artículo"));
        verify(prestamoService, never()).registrarPrestamo(any(), any());
    }

    @Test
    @DisplayName("CP-68: Debe mostrar al lector el mensaje de la regla de negocio que rechaza el préstamo")
    void solicitar_ReglaDeNegocioRechaza_PropagaMensaje() throws Exception {
        when(lectorRepository.findById(10)).thenReturn(Optional.of(lector));
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        when(politicaService.obtenerMaxDias(1)).thenReturn(7);
        when(politicaService.obtenerMaxActivos(1)).thenReturn(null); // sin límite de préstamos activos
        when(prestamoService.registrarPrestamo(eq(1), any(Prestamo.class)))
                .thenThrow(new Exception("No hay stock disponible"));

        Exception ex = assertThrows(AjustesException.class, () -> prestamoLectorService.solicitar(10, 100, 3));

        assertTrue(ex.getMessage().contains("No hay stock disponible"));
    }

    @Test
    @DisplayName("CP-69: No debe armar la pantalla si el registro del lector ya no existe")
    void armarPantalla_LectorInexistente_DevuelveVacio() {
        when(lectorRepository.findById(99)).thenReturn(Optional.empty());

        assertTrue(prestamoLectorService.armarPantalla(99).isEmpty());
    }
}