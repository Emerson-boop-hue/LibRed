package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.util.PasswordUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la validación de credenciales de lectores y administradores (AuthService).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CLAVE = "clave123";

    @Mock
    private LectorRepository lectorRepository;

    @Mock
    private LibreriaRepository libreriaRepository;

    @InjectMocks
    private AuthService authService;

    private Lector lector;

    @BeforeEach
    void setUp() {
        lector = new Lector();
        lector.setId(10);
        lector.setCodigoLector("LEC-1001");
        lector.setNombres("Ana");
        lector.setApellidos("Torres");
        lector.setTelefono("987654321");
        lector.setLibreriaId(1);
        lector.setEstadoCuenta("HABILITADO");
        lector.setPassword(PasswordUtil.hash(CLAVE));
    }

    @Test
    @DisplayName("CP-27: Debe autenticar al lector con credenciales correctas en su librería")
    void autenticarLector_Correcto_DevuelveLector() {
        when(libreriaRepository.existsById(1)).thenReturn(true);
        when(lectorRepository.findAllByCodigoLector("LEC-1001")).thenReturn(List.of(lector));

        Optional<Lector> resultado = authService.autenticarLector("LEC-1001", CLAVE, 1);

        assertTrue(resultado.isPresent());
        assertEquals("LEC-1001", resultado.get().getCodigoLector());
        verify(lectorRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-28: Debe rechazar al lector con contraseña incorrecta")
    void autenticarLector_PasswordIncorrecta_DevuelveVacio() {
        when(libreriaRepository.existsById(1)).thenReturn(true);
        when(lectorRepository.findAllByCodigoLector("LEC-1001")).thenReturn(List.of(lector));

        Optional<Lector> resultado = authService.autenticarLector("LEC-1001", "otra-clave", 1);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("CP-29: Debe rechazar el acceso a una librería que no existe")
    void autenticarLector_LibreriaInexistente_DevuelveVacio() {
        when(libreriaRepository.existsById(99)).thenReturn(false);

        Optional<Lector> resultado = authService.autenticarLector("LEC-1001", CLAVE, 99);

        assertTrue(resultado.isEmpty());
        verifyNoInteractions(lectorRepository);
    }

    @Test
    @DisplayName("CP-30: Debe crear un registro limpio del lector al ingresar a otra librería de la red")
    void autenticarLector_OtraLibreria_CreaRegistroHabilitado() {
        lector.setEstadoCuenta("BLOQUEADO");
        when(libreriaRepository.existsById(2)).thenReturn(true);
        when(lectorRepository.findAllByCodigoLector("LEC-1001")).thenReturn(List.of(lector));
        when(lectorRepository.save(any(Lector.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Lector> resultado = authService.autenticarLector("LEC-1001", CLAVE, 2);

        assertTrue(resultado.isPresent());
        assertEquals(2, resultado.get().getLibreriaId());
        assertEquals("HABILITADO", resultado.get().getEstadoCuenta());
        assertEquals("LEC-1001", resultado.get().getCodigoLector());
    }

    @Test
    @DisplayName("CP-31: Debe convertir a hash una contraseña guardada en texto plano al iniciar sesión")
    void autenticarLector_PasswordEnTextoPlano_SeGuardaComoHash() {
        lector.setPassword(CLAVE);
        when(libreriaRepository.existsById(1)).thenReturn(true);
        when(lectorRepository.findAllByCodigoLector("LEC-1001")).thenReturn(List.of(lector));

        Optional<Lector> resultado = authService.autenticarLector("LEC-1001", CLAVE, 1);

        assertTrue(resultado.isPresent());
        assertNotEquals(CLAVE, lector.getPassword());
        assertTrue(PasswordUtil.esHash(lector.getPassword()));
        verify(lectorRepository, times(1)).save(lector);
    }

    @Test
    @DisplayName("CP-32: Debe autenticar al administrador con credenciales correctas")
    void autenticarAdmin_Correcto_DevuelveLibreria() {
        Libreria libreria = new Libreria();
        libreria.setId(1);
        libreria.setPassword(PasswordUtil.hash(CLAVE));
        when(libreriaRepository.findByCodigoAdmin("ADM-001")).thenReturn(Optional.of(libreria));

        Optional<Libreria> resultado = authService.autenticarAdmin("ADM-001", CLAVE);

        assertTrue(resultado.isPresent());
        verify(libreriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-33: Debe rechazar al administrador con contraseña incorrecta")
    void autenticarAdmin_PasswordIncorrecta_DevuelveVacio() {
        Libreria libreria = new Libreria();
        libreria.setId(1);
        libreria.setPassword(PasswordUtil.hash(CLAVE));
        when(libreriaRepository.findByCodigoAdmin("ADM-001")).thenReturn(Optional.of(libreria));

        Optional<Libreria> resultado = authService.autenticarAdmin("ADM-001", "otra-clave");

        assertTrue(resultado.isEmpty());
    }
}