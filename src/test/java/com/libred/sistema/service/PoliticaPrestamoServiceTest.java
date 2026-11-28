package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TarifaCategoria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.RecursoRepository;
import com.libred.sistema.repository.TarifaCategoriaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para validar las reglas de la política de préstamos (PoliticaPrestamoService).
 */
@ExtendWith(MockitoExtension.class)
class PoliticaPrestamoServiceTest {

    @Mock
    private LibreriaRepository libreriaRepository;

    @Mock
    private TarifaCategoriaRepository tarifaRepository;

    @Mock
    private RecursoRepository recursoRepository;

    @InjectMocks
    private PoliticaPrestamoService politicaService;

    @Test
    @DisplayName("CP-11: Debe rechazar un máximo de días fuera del rango permitido (1 a 60)")
    void guardar_MaxDiasFueraDeRango_LanzaExcepcion() {
        when(libreriaRepository.findById(1)).thenReturn(Optional.of(new Libreria()));

        Exception ex = assertThrows(AjustesException.class,
                () -> politicaService.guardar(1, Map.of("maxDias", "61")));

        assertTrue(ex.getMessage().contains("entre 1 y 60"));
        verify(libreriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-12: Debe rechazar un límite de préstamos activos fuera del rango permitido (1 a 50)")
    void guardar_MaxActivosFueraDeRango_LanzaExcepcion() {
        when(libreriaRepository.findById(1)).thenReturn(Optional.of(new Libreria()));

        Map<String, String> datos = Map.of(
                "maxDias", "7",
                "limitarActivos", "true",
                "maxActivos", "51");

        Exception ex = assertThrows(AjustesException.class, () -> politicaService.guardar(1, datos));

        assertTrue(ex.getMessage().contains("entre 1 y 50"));
        verify(libreriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-13: Debe rechazar una tarifa diaria que supera el máximo de S/ 50.00")
    void guardar_TarifaSuperaMaximo_LanzaExcepcion() {
        when(libreriaRepository.findById(1)).thenReturn(Optional.of(new Libreria()));

        String claveTarifa = "t_" + CategoriaRecurso.values()[0].name();
        Map<String, String> datos = Map.of(
                "maxDias", "7",
                claveTarifa, "60");

        Exception ex = assertThrows(AjustesException.class, () -> politicaService.guardar(1, datos));

        assertTrue(ex.getMessage().contains("no puede superar"));
        verify(libreriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-14: Debe devolver tarifa 0.00 si la categoría no tiene tarifa configurada")
    void tarifaDe_SinTarifaConfigurada_DevuelveCero() {
        when(tarifaRepository.findByLibreriaIdAndCategoria(eq(1), anyString())).thenReturn(Optional.empty());

        BigDecimal tarifa = politicaService.tarifaDe(1, "LIBRO");

        assertEquals(new BigDecimal("0.00"), tarifa);
    }

    @Test
    @DisplayName("Complemento: Debe devolver la tarifa configurada con dos decimales")
    void tarifaDe_Configurada_DevuelveConDosDecimales() {
        TarifaCategoria fila = new TarifaCategoria(1, "LIBRO", new BigDecimal("2.5"));
        when(tarifaRepository.findByLibreriaIdAndCategoria(eq(1), anyString())).thenReturn(Optional.of(fila));

        BigDecimal tarifa = politicaService.tarifaDe(1, "LIBRO");

        assertEquals(new BigDecimal("2.50"), tarifa);
    }
}