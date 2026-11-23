package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.repository.LectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Ajustes del lector: datos personales y contraseña.
 *
 * Recuerda que un lector tiene UNA FILA POR LIBRERÍA (misma persona = mismo código).
 * Como son sus datos personales, los cambios se aplican a TODAS sus filas, para que sus datos
 * y su contraseña sean los mismos en cualquier librería de la red. El estado (HABILITADO/SANCIONADO)
 * NO se toca: es propio de cada librería.
 */
@Service
public class AjustesLectorService {

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'’-]{1,99}$");
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 20;

    private final LectorRepository lectorRepository;

    public AjustesLectorService(LectorRepository lectorRepository) {
        this.lectorRepository = lectorRepository;
    }

    public Lector obtener(Integer lectorId) {
        return lectorRepository.findById(lectorId)
                .orElseThrow(() -> new AjustesException(null, "Tu registro de lector ya no existe."));
    }

    @Transactional
    public Lector actualizarPerfil(Integer lectorId, String nombres, String apellidos, String telefono) {
        Lector actual = obtener(lectorId);

        String n = normalizar(nombres);
        if (!NOMBRE.matcher(n).matches()) {
            throw new AjustesException("nombres", "Ingresa tus nombres (solo letras, entre 2 y 100 caracteres).");
        }
        String a = normalizar(apellidos);
        if (!NOMBRE.matcher(a).matches()) {
            throw new AjustesException("apellidos", "Ingresa tus apellidos (solo letras, entre 2 y 100 caracteres).");
        }
        String tel = telefono == null ? "" : telefono.replaceAll("\\s+", "");
        if (!tel.matches("\\d{9}")) {
            throw new AjustesException("telefono", "El teléfono debe tener 9 dígitos.");
        }

        // Se actualiza la persona en todas las librerías donde está registrada
        List<Lector> registros = lectorRepository.findAllByCodigoLector(actual.getCodigoLector());
        for (Lector registro : registros) {
            registro.setNombres(n);
            registro.setApellidos(a);
            registro.setTelefono(tel);
        }
        lectorRepository.saveAll(registros);
        return actual;
    }

    @Transactional
    public void cambiarPassword(Integer lectorId, String actualPassword, String nueva, String confirmar) {
        Lector actual = obtener(lectorId);

        if (actualPassword == null || !actualPassword.equals(actual.getPassword())) {
            throw new AjustesException("passwordActual", "La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < PASSWORD_MIN || nueva.length() > PASSWORD_MAX) {
            throw new AjustesException("passwordNueva",
                    "La nueva contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.");
        }
        if (nueva.contains(" ")) {
            throw new AjustesException("passwordNueva", "La contraseña no puede tener espacios.");
        }
        if (nueva.equals(actual.getPassword())) {
            throw new AjustesException("passwordNueva", "La nueva contraseña debe ser distinta a la actual.");
        }
        if (!nueva.equals(confirmar)) {
            throw new AjustesException("passwordConfirmar", "Las contraseñas no coinciden.");
        }

        List<Lector> registros = lectorRepository.findAllByCodigoLector(actual.getCodigoLector());
        for (Lector registro : registros) {
            registro.setPassword(nueva);
        }
        lectorRepository.saveAll(registros);
    }

    // Quita espacios al inicio/final y deja un solo espacio entre palabras
    private String normalizar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }
}