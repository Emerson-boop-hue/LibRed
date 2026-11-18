package com.libred.sistema.service;

import com.libred.sistema.model.CodigoVerificacion;
import com.libred.sistema.repository.CodigoVerificacionRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Códigos de verificación de 6 dígitos: se generan al azar (SecureRandom) en cada solicitud, se guardan solo como
 * huella SHA-256, vencen, permiten pocos intentos y se pueden usar una sola vez.
 */
@Service
public class VerificacionCodigoService {

    /** Reglas de un tipo de código. */
    public record Politica(int esperaSegundos, int maxPorHora, int vigenciaMinutos, int maxIntentos) {}

    /** Códigos que llegan al correo (registro y recuperación). */
    public static final Politica CORREO = new Politica(60, 5, 10, 5);
    /**
     * Códigos que llegan por SMS al celular del lector (recuperación de acceso): más espaciados para no gastar
     * mensajes.
     */
    public static final Politica SMS = new Politica(30, 5, 10, 5);
    /** Código de aprobación de la "app" de Yape. */
    public static final Politica YAPE = new Politica(10, 10, 5, 5);

    public static final String REGISTRO = "REGISTRO";
    public static final String RECUPERACION = "RECUPERACION";
    public static final String RECUPERACION_LECTOR = "RECUPERACION_LECTOR";
    public static final String YAPE_PROPOSITO = "YAPE";

    private final CodigoVerificacionRepository repositorio;
    private final SecureRandom azar = new SecureRandom();

    public VerificacionCodigoService(CodigoVerificacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Crea un código nuevo (los anteriores dejan de servir) y lo devuelve en claro para enviarlo. */
    @Transactional
    public String generar(String destino, String proposito, Politica politica) {
        LocalDateTime ahora = LocalDateTime.now();

        Optional<CodigoVerificacion> ultimo = repositorio.findFirstByDestinoAndPropositoOrderByIdDesc(destino, proposito);
        if (ultimo.isPresent()) {
            LocalDateTime libre = ultimo.get().getFechaCreacion().plusSeconds(politica.esperaSegundos());
            if (libre.isAfter(ahora)) {
                long faltan = Duration.between(ahora, libre).getSeconds() + 1;
                throw new SuscripcionException(null, "Espera " + faltan + " s para pedir otro código.", HttpStatus.TOO_MANY_REQUESTS);
            }
        }
        long ultimaHora = repositorio.countByDestinoAndPropositoAndFechaCreacionAfter(destino, proposito, ahora.minusHours(1));
        if (ultimaHora >= politica.maxPorHora()) {
            throw new SuscripcionException(null, "Pediste demasiados códigos. Intenta nuevamente en una hora.", HttpStatus.TOO_MANY_REQUESTS);
        }

        repositorio.invalidarAnteriores(destino, proposito);

        String codigo = String.format("%06d", azar.nextInt(1_000_000));
        CodigoVerificacion c = new CodigoVerificacion();
        c.setDestino(destino);
        c.setProposito(proposito);
        c.setCodigoHash(huella(destino, proposito, codigo));
        c.setExpiraEn(ahora.plusMinutes(politica.vigenciaMinutos()));
        c.setFechaCreacion(ahora);
        repositorio.save(c);
        return codigo;
    }

    /** Comprueba el código y, si es correcto, lo consume. Si no, lanza el error que corresponde. */
    @Transactional(noRollbackFor = SuscripcionException.class)
    public void validar(String destino, String proposito, String codigo, Politica politica, String campoError) {
        comprobar(destino, proposito, codigo, politica, campoError, true);
    }

    /**
     * Comprueba el código SIN consumirlo (para pasos intermedios, como elegir la cuenta antes de la contraseña
     * nueva).
     */
    @Transactional(noRollbackFor = SuscripcionException.class)
    public void verificar(String destino, String proposito, String codigo, Politica politica, String campoError) {
        comprobar(destino, proposito, codigo, politica, campoError, false);
    }

    private void comprobar(String destino, String proposito, String codigo, Politica politica, String campoError, boolean consumir) {
        if (codigo == null || !codigo.matches("\\d{6}")) {
            throw new SuscripcionException(campoError, "Ingresa el código de 6 dígitos.", HttpStatus.BAD_REQUEST);
        }
        CodigoVerificacion c = repositorio.findFirstByDestinoAndPropositoAndUsadoFalseOrderByIdDesc(destino, proposito)
                .orElseThrow(() -> new SuscripcionException(campoError,
                        "El código venció o no existe. Solicita uno nuevo.", HttpStatus.BAD_REQUEST));

        if (c.getExpiraEn().isBefore(LocalDateTime.now())) {
            c.setUsado(true);
            repositorio.save(c);
            throw new SuscripcionException(campoError, "El código venció. Solicita uno nuevo.", HttpStatus.BAD_REQUEST);
        }

        boolean coincide = MessageDigest.isEqual(
                huella(destino, proposito, codigo).getBytes(StandardCharsets.UTF_8),
                c.getCodigoHash().getBytes(StandardCharsets.UTF_8));
        if (!coincide) {
            c.setIntentos(c.getIntentos() + 1);
            int quedan = politica.maxIntentos() - c.getIntentos();
            if (quedan <= 0) {
                c.setUsado(true);
                repositorio.save(c);
                throw new SuscripcionException(campoError,
                        "Demasiados intentos. Solicita un código nuevo.", HttpStatus.TOO_MANY_REQUESTS);
            }
            repositorio.save(c);
            throw new SuscripcionException(campoError,
                    "Código incorrecto. Te " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos."),
                    HttpStatus.BAD_REQUEST);
        }

        if (consumir) {
            c.setUsado(true);
            repositorio.save(c);
        }
    }

    private static String huella(String destino, String proposito, String codigo) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] bytes = sha.digest((destino + "|" + proposito + "|" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}