package com.libred.sistema.controller;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.service.LectorService;
import com.libred.sistema.service.PoliticaPrestamoService;
import com.libred.sistema.service.PrestamoService;
import com.libred.sistema.service.RecursoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración ligera del PrestamoController con MockMvc (modo standalone: sin levantar
 * el contexto completo de Spring ni la base de datos; los servicios se simulan con Mockito).
 */
@ExtendWith(MockitoExtension.class)
class PrestamoControllerTest {

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private PrestamoService prestamoService;

    @Mock
    private LectorService lectorService;

    @Mock
    private RecursoService recursoService;

    @Mock
    private PoliticaPrestamoService politicaService;

    @InjectMocks
    private PrestamoController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("CP-18: Debe redirigir con el mensaje de error cuando la regla de negocio rechaza el préstamo")
    void guardarPrestamo_ErrorDeNegocio_RedirigeConMensajeDeError() throws Exception {
        when(prestamoService.registrarPrestamo(eq(1), any(Prestamo.class)))
                .thenThrow(new IllegalStateException("No hay stock disponible"));

        mockMvc.perform(post("/admin/prestamos/guardar")
                        .param("lectorId", "10")
                        .param("recursoId", "100")
                        .param("fechaDevolucionLimite", LocalDate.now().plusDays(3).toString())
                        .sessionAttr("libreriaId", 1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/prestamos"))
                .andExpect(flash().attribute("error", "No hay stock disponible"));
    }

    @Test
    @DisplayName("CP-19: Debe redirigir con los datos de confirmación cuando el préstamo se registra")
    void guardarPrestamo_Valido_RedirigeConConfirmacion() throws Exception {
        Lector lector = new Lector();
        lector.setNombres("Ana");
        lector.setApellidos("Torres");

        Recurso recurso = new Recurso();
        recurso.setTitulo("Cien años de soledad");

        Prestamo guardado = new Prestamo();
        guardado.setId(50);
        guardado.setLector(lector);
        guardado.setRecurso(recurso);
        guardado.setFechaDevolucionLimite(LocalDate.now().plusDays(3));
        guardado.setDiasPrestamo(3);
        guardado.setMontoServicio(new BigDecimal("7.50"));

        when(prestamoService.registrarPrestamo(eq(1), any(Prestamo.class))).thenReturn(guardado);

        mockMvc.perform(post("/admin/prestamos/guardar")
                        .param("lectorId", "10")
                        .param("recursoId", "100")
                        .param("fechaDevolucionLimite", LocalDate.now().plusDays(3).toString())
                        .sessionAttr("libreriaId", 1))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/prestamos"))
                .andExpect(flash().attributeExists("prestamoOk"));
    }
}