package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TarjetaPago;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.TarjetaPagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ajustes del administrador: datos de la librería, contraseña y método de pago (tarjeta).
 */
@Service
public class AjustesAdminService {

    // Mismas reglas de contraseña que el registro de la librería (SuscripcionService)
    private static final int PASSWORD_MIN = 4;
    private static final int PASSWORD_MAX = 6;

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TITULAR = Pattern.compile("^[\\p{L}][\\p{L} .'’-]{2,99}$");
    private static final Set<String> MARCAS = Set.of("VISA", "MASTERCARD", "TARJETA");

    private final LibreriaRepository libreriaRepository;
    private final TarjetaPagoRepository tarjetaPagoRepository;

    public AjustesAdminService(LibreriaRepository libreriaRepository, TarjetaPagoRepository tarjetaPagoRepository) {
        this.libreriaRepository = libreriaRepository;
        this.tarjetaPagoRepository = tarjetaPagoRepository;
    }

    public Libreria obtenerLibreria(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId)
                .orElseThrow(() -> new AjustesException(null, "No se encontró la librería."));
    }

    public Optional<TarjetaPago> obtenerTarjeta(Integer libreriaId) {
        return tarjetaPagoRepository.findByLibreriaId(libreriaId);
    }

    // ------------------------------------------------------------------
    // DATOS DE LA LIBRERÍA
    // ------------------------------------------------------------------
    @Transactional
    public Libreria actualizarDatos(Integer libreriaId, String nombreComercial, String ruc, String direccion,
                                    String telefono, String correo) {
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

        l.setNombreComercial(nombre);
        l.setRuc(rucLimpio);
        l.setDireccion(dir);
        l.setTelefonoContacto(tel.isEmpty() ? null : tel);
        l.setCorreoContacto(mail.isEmpty() ? null : mail);
        return libreriaRepository.save(l);
    }

    // ------------------------------------------------------------------
    // CONTRASEÑA DEL ADMINISTRADOR
    // ------------------------------------------------------------------
    @Transactional
    public void cambiarPassword(Integer libreriaId, String actualPassword, String nueva, String confirmar) {
        Libreria l = obtenerLibreria(libreriaId);

        if (actualPassword == null || !actualPassword.equals(l.getPassword())) {
            throw new AjustesException("passwordActual", "La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < PASSWORD_MIN || nueva.length() > PASSWORD_MAX) {
            throw new AjustesException("passwordNueva",
                    "La nueva contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.");
        }
        if (nueva.contains(" ")) {
            throw new AjustesException("passwordNueva", "La contraseña no puede tener espacios.");
        }
        if (nueva.equals(l.getPassword())) {
            throw new AjustesException("passwordNueva", "La nueva contraseña debe ser distinta a la actual.");
        }
        if (!nueva.equals(confirmar)) {
            throw new AjustesException("passwordConfirmar", "Las contraseñas no coinciden.");
        }

        l.setPassword(nueva);
        libreriaRepository.save(l);
    }

    // ------------------------------------------------------------------
    // MÉTODO DE PAGO (TARJETA): se crea o se reemplaza la tarjeta de la librería
    // El navegador solo envía marca, últimos 4 dígitos, titular y vencimiento.
    // ------------------------------------------------------------------
    @Transactional
    public TarjetaPago guardarTarjeta(Integer libreriaId, String titular, String marca, String ultimosDigitos,
                                      String venceMes, String venceAnio) {
        obtenerLibreria(libreriaId);

        String tit = limpiar(titular).replaceAll("\\s+", " ");
        if (!TITULAR.matcher(tit).matches()) {
            throw new AjustesException("titular", "Ingresa el nombre del titular tal como aparece en la tarjeta.");
        }

        String marcaLimpia = limpiar(marca).toUpperCase();
        if (!MARCAS.contains(marcaLimpia)) {
            marcaLimpia = "TARJETA";
        }

        String ultimos = limpiar(ultimosDigitos);
        if (!ultimos.matches("\\d{4}")) {
            throw new AjustesException("numero", "Número de tarjeta inválido.");
        }

        int mes;
        int anio;
        try {
            mes = Integer.parseInt(limpiar(venceMes));
            anio = Integer.parseInt(limpiar(venceAnio));
        } catch (NumberFormatException e) {
            throw new AjustesException("vence", "Fecha de vencimiento inválida (MM/AA).");
        }
        if (mes < 1 || mes > 12 || anio < 2000 || anio > 2100) {
            throw new AjustesException("vence", "Fecha de vencimiento inválida (MM/AA).");
        }
        if (YearMonth.of(anio, mes).isBefore(YearMonth.now())) {
            throw new AjustesException("vence", "La tarjeta está vencida.");
        }

        TarjetaPago tarjeta = tarjetaPagoRepository.findByLibreriaId(libreriaId).orElseGet(TarjetaPago::new);
        tarjeta.setLibreriaId(libreriaId);
        tarjeta.setTitular(tit.toUpperCase());
        tarjeta.setMarca(marcaLimpia);
        tarjeta.setUltimosDigitos(ultimos);
        tarjeta.setVenceMes(mes);
        tarjeta.setVenceAnio(anio);
        return tarjetaPagoRepository.save(tarjeta);
    }

    @Transactional
    public void quitarTarjeta(Integer libreriaId) {
        tarjetaPagoRepository.findByLibreriaId(libreriaId).ifPresent(tarjetaPagoRepository::delete);
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}