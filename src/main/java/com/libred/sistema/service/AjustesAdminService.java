package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.libred.sistema.util.PasswordUtil;

/** Ajustes del administrador: datos de la librería, política de préstamos y contraseña. */
@Service
public class AjustesAdminService {

    // Mismas reglas de contraseña que el registro de la librería (SuscripcionService)
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 20;

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LibreriaRepository libreriaRepository;

    public AjustesAdminService(LibreriaRepository libreriaRepository) {
        this.libreriaRepository = libreriaRepository;
    }

    public Libreria obtenerLibreria(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId)
                .orElseThrow(() -> new AjustesException(null, "No se encontró la librería."));
    }

    /**
     * Qué tan completo está el perfil de la librería en Ajustes: teléfono, correo de contacto, horario y descripción
     * (25 % cada uno). No mira libros ni préstamos.
     */
    public Map<String, Object> completitud(Libreria l) {
        List<String> faltan = new ArrayList<>();
        if (vacio(l.getTelefonoContacto())) faltan.add("Teléfono de contacto");
        if (vacio(l.getCorreoContacto())) faltan.add("Correo de contacto");
        if (vacio(l.getHorarioAtencion())) faltan.add("Horario de atención");
        if (vacio(l.getDescripcion())) faltan.add("Descripción");
        int total = 4;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("porcentaje", (total - faltan.size()) * 100 / total);
        r.put("faltan", faltan);
        r.put("completo", faltan.isEmpty());
        return r;
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    @Transactional
    public Libreria actualizarDatos(Integer libreriaId, String nombreComercial, String ruc, String direccion,
                                    String telefono, String correo,
                                    String descripcion, String horario) {
        Libreria l = obtenerLibreria(libreriaId);

        String nombre = limpiar(nombreComercial);
        if (nombre.length() < 2 || nombre.length() > 150) {
            throw new AjustesException("nombreComercial", "El nombre comercial es obligatorio (máximo 150 caracteres).");
        }

        String rucLimpio = limpiar(ruc);
        if (!rucLimpio.matches("\\d{11}")) {
            throw new AjustesException("ruc", "El RUC debe tener exactamente 11 dígitos.");
        }
        if (!rucLimpio.equals(l.getRuc()) && libreriaRepository.existsByRuc(rucLimpio)) {
            throw new AjustesException("ruc", "Este RUC ya está registrado en otra librería.");
        }

        String dir = limpiar(direccion);
        if (dir.length() < 5 || dir.length() > 255) {
            throw new AjustesException("direccion", "La dirección es obligatoria (entre 5 y 255 caracteres).");
        }

        // Teléfono y correo son opcionales
        String tel = telefono == null ? "" : telefono.replaceAll("\\s+", "");
        if (!tel.isEmpty() && !tel.matches("\\d{7,9}")) {
            throw new AjustesException("telefonoContacto", "El teléfono debe tener entre 7 y 9 dígitos.");
        }
        String mail = limpiar(correo);
        if (!mail.isEmpty() && (mail.length() > 120 || !CORREO.matcher(mail).matches())) {
            throw new AjustesException("correoContacto", "Ingresa un correo válido.");
        }

        // Perfil público (opcional): lo ven los lectores en Red Global
        String desc = limpiar(descripcion);
        if (desc.length() > 300) {
            throw new AjustesException("descripcion", "La descripción no puede superar los 300 caracteres.");
        }
        String hor = limpiar(horario);
        if (hor.length() > 120) {
            throw new AjustesException("horarioAtencion", "El horario no puede superar los 120 caracteres.");
        }

        l.setNombreComercial(nombre);
        l.setDescripcion(desc.isEmpty() ? null : desc);
        l.setHorarioAtencion(hor.isEmpty() ? null : hor);
        l.setRuc(rucLimpio);
        l.setDireccion(dir);
        l.setTelefonoContacto(tel.isEmpty() ? null : tel);
        l.setCorreoContacto(mail.isEmpty() ? null : mail);
        return libreriaRepository.save(l);
    }

    @Transactional
    public void cambiarPassword(Integer libreriaId, String actualPassword, String nueva, String confirmar) {
        Libreria l = obtenerLibreria(libreriaId);

        if (!PasswordUtil.coincide(actualPassword, l.getPassword())) {
            throw new AjustesException("passwordActual", "La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < PASSWORD_MIN || nueva.length() > PASSWORD_MAX) {
            throw new AjustesException("passwordNueva",
                    "La nueva contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.");
        }
        if (nueva.contains(" ")) {
            throw new AjustesException("passwordNueva", "La contraseña no puede tener espacios.");
        }
        if (PasswordUtil.coincide(nueva, l.getPassword())) {
            throw new AjustesException("passwordNueva", "La nueva contraseña debe ser distinta a la actual.");
        }
        if (!nueva.equals(confirmar)) {
            throw new AjustesException("passwordConfirmar", "Las contraseñas no coinciden.");
        }

        l.setPassword(PasswordUtil.hash(nueva));
        libreriaRepository.save(l);
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}