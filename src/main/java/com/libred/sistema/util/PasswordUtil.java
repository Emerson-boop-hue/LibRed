package com.libred.sistema.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Contraseñas de lectores y administradores: se guardan como hash PBKDF2-HMAC-SHA256 con sal aleatoria
 * ("pbkdf2$iteraciones$sal$hash").
 */
public final class PasswordUtil {

    private static final String PREFIJO = "pbkdf2$";
    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(String clave) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] derivada = derivar(clave, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIJO + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(derivada);
    }

    public static boolean esHash(String guardado) {
        return guardado != null && guardado.startsWith(PREFIJO);
    }

    /** true si la contraseña escrita corresponde a la guardada (hash o, en cuentas antiguas, texto plano). */
    public static boolean coincide(String clave, String guardado) {
        if (clave == null || guardado == null) {
            return false;
        }
        if (!esHash(guardado)) {
            return MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8), guardado.getBytes(StandardCharsets.UTF_8));
        }
        String[] partes = guardado.split("\\$");
        if (partes.length != 4) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(derivar(clave, sal, iteraciones), esperado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, iteraciones, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo procesar la contraseña.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
