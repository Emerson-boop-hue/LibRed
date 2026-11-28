package com.libred.sistema.config;

import com.libred.sistema.service.MembresiaService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMapManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas del interceptor que bloquea las operaciones de escritura cuando la membresía está suspendida
 * (control de acceso, OWASP A01). Usa los objetos simulados de Spring (sin servidor ni base de datos).
 */
@ExtendWith(MockitoExtension.class)
class MembresiaInterceptorTest {

    private static final MembresiaService.Estado SUSPENDIDA =
            new MembresiaService.Estado(MembresiaService.Fase.SUSPENDIDA, 0, "rojo");
    private static final MembresiaService.Estado ACTIVA =
            new MembresiaService.Estado(MembresiaService.Fase.ACTIVA, 10, "ambar");

    @Mock
    private MembresiaService membresiaService;

    @Mock
    private FlashMapManager flashMapManager;

    @InjectMocks
    private MembresiaInterceptor interceptor;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest peticion(String metodo, String ruta) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("libreriaId", 1);
        request.setSession(session);
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, flashMapManager);
        return request;
    }

    @Test
    @DisplayName("CP-49: Debe bloquear el registro de un préstamo (POST) con la membresía suspendida")
    void preHandle_PostConMembresiaSuspendida_BloqueaYRedirige() throws Exception {
        when(membresiaService.estadoDe(1)).thenReturn(SUSPENDIDA);
        MockHttpServletRequest request = peticion("POST", "/admin/prestamos/guardar");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertFalse(continua);
        assertEquals("/admin/prestamos", response.getRedirectedUrl());
        verify(flashMapManager, times(1)).saveOutputFlashMap(any(), eq(request), eq(response));
    }

    @Test
    @DisplayName("CP-50: Debe bloquear también la eliminación hecha con GET en rutas /eliminar/ (OBS-01)")
    void preHandle_EliminarConGetYMembresiaSuspendida_Bloquea() throws Exception {
        when(membresiaService.estadoDe(1)).thenReturn(SUSPENDIDA);
        MockHttpServletRequest request = peticion("GET", "/admin/inventario/eliminar/5");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertFalse(continua);
        assertEquals("/admin/inventario", response.getRedirectedUrl());
    }

    @Test
    @DisplayName("CP-51: Debe permitir procesar devoluciones aunque la membresía esté suspendida")
    void preHandle_DevolucionConMembresiaSuspendida_Permite() throws Exception {
        when(membresiaService.estadoDe(1)).thenReturn(SUSPENDIDA);
        MockHttpServletRequest request = peticion("POST", "/admin/devoluciones/procesar");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertTrue(continua);
        assertNull(response.getRedirectedUrl());
    }

    @Test
    @DisplayName("CP-52: Debe responder HTTP 403 con JSON en los módulos con peticiones AJAX")
    void preHandle_AjaxConMembresiaSuspendida_Responde403() throws Exception {
        when(membresiaService.estadoDe(1)).thenReturn(SUSPENDIDA);
        MockHttpServletRequest request = peticion("POST", "/admin/ajustes/guardar");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertFalse(continua);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"ok\":false"));
        assertTrue(response.getContentAsString().contains("suspendida"));
    }

    @Test
    @DisplayName("CP-53: Debe permitir las operaciones de escritura con la membresía activa")
    void preHandle_PostConMembresiaActiva_Permite() throws Exception {
        when(membresiaService.estadoDe(1)).thenReturn(ACTIVA);
        MockHttpServletRequest request = peticion("POST", "/admin/prestamos/guardar");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertTrue(continua);
        assertNull(response.getRedirectedUrl());
    }

    @Test
    @DisplayName("CP-54: Debe permitir las consultas (GET) sin consultar el estado de la membresía")
    void preHandle_ConsultaGet_PermiteSinConsultarMembresia() throws Exception {
        MockHttpServletRequest request = peticion("GET", "/admin/prestamos");

        boolean continua = interceptor.preHandle(request, response, new Object());

        assertTrue(continua);
        verifyNoInteractions(membresiaService);
    }
}