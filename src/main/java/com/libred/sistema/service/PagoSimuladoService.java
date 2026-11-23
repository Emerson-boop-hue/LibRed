package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.MedioPagoPrueba;
import com.libred.sistema.model.Pago;
import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.model.RegistroDtos.SolicitudPago;
import com.libred.sistema.model.RegistroPendiente;
import com.libred.sistema.repository.MedioPagoPruebaRepository;
import com.libred.sistema.repository.PagoRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Pasarela de pago SIMULADA. */
@Service
public class PagoSimuladoService {

    private static final Pattern TITULAR = Pattern.compile("^[\\p{L}][\\p{L} .'’-]{2,99}$");
    private static final int MAX_RECHAZOS = 15;
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // PayPal cobra en dólares: los soles del plan se convierten con este tipo de cambio fijo
    @Value("${libred.pago.tipo-cambio-usd:3.75}")
    private BigDecimal tipoCambio;

    private final MedioPagoPruebaRepository medioRepository;
    private final PagoRepository pagoRepository;
    private final VerificacionCodigoService codigos;
    private final SecureRandom azar = new SecureRandom();

    public PagoSimuladoService(MedioPagoPruebaRepository medioRepository, PagoRepository pagoRepository,
                               VerificacionCodigoService codigos) {
        this.medioRepository = medioRepository;
        this.pagoRepository = pagoRepository;
        this.codigos = codigos;
    }

    // QUIÉN PAGA: un registro nuevo (todavía sin librería) o una librería que renueva / cambia de plan
    public record Pagador(Integer registroId, Integer libreriaId, String correo, String origen) {
        public static Pagador deRegistro(RegistroPendiente pend) {
            return new Pagador(pend.getId(), null, pend.getCorreo(), "REGISTRO");
        }

        public static Pagador deLibreria(Libreria l, String origen) {
            String correo = l.getCorreoCuenta() != null ? l.getCorreoCuenta()
                    : (l.getCorreoContacto() != null ? l.getCorreoContacto() : l.getCodigoAdmin() + "@sin-correo");
            return new Pagador(null, l.getId(), correo, origen);
        }
    }

    public Pago cobrar(RegistroPendiente pend, PlanSuscripcion plan, SolicitudPago s) {
        return cobrar(Pagador.deRegistro(pend), plan, s);
    }

    public Pago cobrar(Pagador pend, PlanSuscripcion plan, SolicitudPago s) {
        String metodo = s.metodo() == null ? "" : s.metodo().trim().toUpperCase();

        long rechazosRecientes = pend.registroId() != null
                ? pagoRepository.countByRegistroIdAndEstadoAndFechaAfter(pend.registroId(), "RECHAZADO", LocalDateTime.now().minusMinutes(15))
                : pagoRepository.countByLibreriaIdAndEstadoAndFechaAfter(pend.libreriaId(), "RECHAZADO", LocalDateTime.now().minusMinutes(15));
        if (rechazosRecientes >= MAX_RECHAZOS) {
            throw new SuscripcionException(null,
                    "Superaste el máximo de intentos de pago. Espera unos minutos e inténtalo de nuevo.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        switch (metodo) {
            case "TARJETA":
                return cobrarTarjeta(pend, plan, s);
            case "YAPE":
                return cobrarYape(pend, plan, s);
            case "PAYPAL":
                return cobrarPaypal(pend, plan, s);
            default:
                throw new SuscripcionException(null, "Elige un método de pago.", HttpStatus.BAD_REQUEST);
        }
    }

    // Tarjeta de crédito / débito
    private Pago cobrarTarjeta(Pagador pend, PlanSuscripcion plan, SolicitudPago s) {
        String numero = digitos(s.numero());
        if (numero.length() != 16 || !luhn(numero)) {
            throw new SuscripcionException("numero", "Número de tarjeta inválido.", HttpStatus.BAD_REQUEST);
        }
        int[] vence = parsearVence(s.vence());
        if (vence == null) {
            throw new SuscripcionException("vence", "Fecha de vencimiento inválida (MM/AA).", HttpStatus.BAD_REQUEST);
        }
        if (YearMonth.of(vence[1], vence[0]).isBefore(YearMonth.now())) {
            throw new SuscripcionException("vence", "La tarjeta está vencida.", HttpStatus.BAD_REQUEST);
        }
        String cvv = s.cvv() == null ? "" : s.cvv().trim();
        if (!cvv.matches("\\d{3,4}")) {
            throw new SuscripcionException("cvv", "El CVV tiene 3 dígitos.", HttpStatus.BAD_REQUEST);
        }
        String titular = s.titular() == null ? "" : s.titular().trim().replaceAll("\\s+", " ");
        if (!TITULAR.matcher(titular).matches()) {
            throw new SuscripcionException("titular", "Ingresa el nombre del titular.", HttpStatus.BAD_REQUEST);
        }

        Optional<MedioPagoPrueba> encontrado = medioRepository.findByIdentificador(numero).filter(MedioPagoPrueba::esTarjeta);
        String tipo = encontrado.map(MedioPagoPrueba::getTipo).orElse(MedioPagoPrueba.TARJETA_CREDITO);
        Pago pago = nuevoPago(pend, plan, tipo, "Tarjeta •••• " + numero.substring(12), "PEN", plan.getPrecio());

        if (encontrado.isEmpty()) {
            throw rechazar(pago, null, "Tu tarjeta fue rechazada. Verifica los datos o usa otro método de pago.");
        }
        MedioPagoPrueba medio = encontrado.get();
        pago.setMedioId(medio.getId());
        pago.setReferencia(nombreMarca(medio.getMarca()) + (medio.getTipo().equals(MedioPagoPrueba.TARJETA_DEBITO) ? " débito" : " crédito")
                + " •••• " + numero.substring(12));

        if (medio.getVenceMes() == null || medio.getVenceAnio() == null
                || medio.getVenceMes() != vence[0] || medio.getVenceAnio() != vence[1]) {
            throw rechazar(pago, "vence", "La fecha de vencimiento no coincide con la de la tarjeta.");
        }
        if (medio.getCvv() == null || !medio.getCvv().equals(cvv)) {
            throw rechazar(pago, "cvv", "El código de seguridad (CVV) es incorrecto.");
        }
        if ("BLOQUEADO".equals(medio.getEstado())) {
            throw rechazar(pago, null, "Tarjeta bloqueada. Comunícate con tu banco.");
        }
        if ("RECHAZADO".equals(medio.getEstado())) {
            throw rechazar(pago, null, "Tu banco rechazó la operación. Intenta con otra tarjeta.");
        }
        if (medio.getSaldo().compareTo(plan.getPrecio()) < 0) {
            throw rechazar(pago, null, MedioPagoPrueba.TARJETA_DEBITO.equals(medio.getTipo())
                    ? "Fondos insuficientes en tu tarjeta de débito."
                    : "Línea de crédito insuficiente.");
        }
        return aprobar(pago, medio, plan.getPrecio());
    }

    private Pago cobrarYape(Pagador pend, PlanSuscripcion plan, SolicitudPago s) {
        String celular = digitos(s.celular());
        if (!celular.matches("9\\d{8}")) {
            throw new SuscripcionException("celular", "Ingresa un celular de 9 dígitos.", HttpStatus.BAD_REQUEST);
        }
        String codigo = s.codigo() == null ? "" : s.codigo().trim();
        if (!codigo.matches("\\d{6}")) {
            throw new SuscripcionException("codigo", "Ingresa el código de aprobación de 6 dígitos.", HttpStatus.BAD_REQUEST);
        }

        Pago pago = nuevoPago(pend, plan, MedioPagoPrueba.YAPE, "Yape " + celular.substring(0, 3) + " ••• " + celular.substring(6), "PEN", plan.getPrecio());
        Optional<MedioPagoPrueba> encontrado = medioRepository.findByIdentificador(celular)
                .filter(m -> MedioPagoPrueba.YAPE.equals(m.getTipo()));
        if (encontrado.isEmpty()) {
            throw rechazar(pago, "celular", "Este número no tiene Yape activo.");
        }
        MedioPagoPrueba medio = encontrado.get();
        pago.setMedioId(medio.getId());
        if (!"ACTIVO".equals(medio.getEstado())) {
            throw rechazar(pago, "celular", "Tu cuenta Yape está suspendida. Comunícate con Yape.");
        }
        if (medio.getSaldo().compareTo(plan.getPrecio()) < 0) {
            throw rechazar(pago, null, "Saldo insuficiente en tu Yape.");
        }
        // El código se comprueba al final: si falla, el intento queda anotado en el propio código (máx. 5)
        codigos.validar(celular, VerificacionCodigoService.YAPE_PROPOSITO, codigo, VerificacionCodigoService.YAPE, "codigo");
        return aprobar(pago, medio, plan.getPrecio());
    }

    /** Solo comprueba el correo y la contraseña de la cuenta PayPal de prueba (pantalla de inicio de sesión). */
    public MedioPagoPrueba validarCuentaPaypal(String correo, String clave) {
        String mail = correo == null ? "" : correo.trim().toLowerCase();
        Optional<MedioPagoPrueba> encontrado = medioRepository.findByTipoAndIdentificador(MedioPagoPrueba.PAYPAL, mail);
        if (encontrado.isEmpty() || clave == null || !clave.equals(encontrado.get().getClave())) {
            throw new SuscripcionException("paypal", "El correo o la contraseña no son correctos.", HttpStatus.UNAUTHORIZED);
        }
        return encontrado.get();
    }

    private Pago cobrarPaypal(Pagador pend, PlanSuscripcion plan, SolicitudPago s) {
        MedioPagoPrueba cuenta = validarCuentaPaypal(s.correoPaypal(), s.clavePaypal());
        // Se vuelve a leer con bloqueo porque se va a descontar el saldo
        MedioPagoPrueba medio = medioRepository.findByIdentificador(cuenta.getIdentificador()).orElse(cuenta);
        BigDecimal enDolares = aDolares(plan.getPrecio());
        Pago pago = nuevoPago(pend, plan, MedioPagoPrueba.PAYPAL, "PayPal " + enmascarar(medio.getIdentificador()), "USD", enDolares);
        pago.setMedioId(medio.getId());
        if (!"ACTIVO".equals(medio.getEstado())) {
            throw rechazar(pago, null, "Tu cuenta de PayPal está limitada. Resuélvelo desde PayPal o usa otro método.");
        }
        if (medio.getSaldo().compareTo(enDolares) < 0) {
            throw rechazar(pago, null, "Fondos insuficientes en tu cuenta de PayPal.");
        }
        return aprobar(pago, medio, enDolares);
    }

    // Yape: código de aprobación (lo "muestra" la app de Yape simulada)
    public Map<String, Object> emitirCodigoYape(String celularCrudo) {
        String celular = digitos(celularCrudo);
        if (!celular.matches("9\\d{8}")) {
            throw new SuscripcionException("celular", "Ingresa un celular de 9 dígitos.", HttpStatus.BAD_REQUEST);
        }
        MedioPagoPrueba cuenta = medioRepository.findByTipoAndIdentificador(MedioPagoPrueba.YAPE, celular).orElse(null);
        if (cuenta == null) {
            throw new SuscripcionException("celular", "Este número no tiene Yape activo.", HttpStatus.PAYMENT_REQUIRED);
        }
        if (!"ACTIVO".equals(cuenta.getEstado())) {
            throw new SuscripcionException("celular", "Tu cuenta Yape está suspendida. Comunícate con Yape.", HttpStatus.PAYMENT_REQUIRED);
        }
        String codigo = codigos.generar(celular, VerificacionCodigoService.YAPE_PROPOSITO, VerificacionCodigoService.YAPE);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("codigo", codigo);
        r.put("espera", VerificacionCodigoService.YAPE.esperaSegundos());
        return r;
    }

    // Renovación automática (solo tarjeta y PayPal; Yape no permite cobros automáticos)
    /** Cobra el siguiente periodo con el medio guardado, sin que el administrador intervenga. */
    public Pago cobrarRenovacion(Libreria l, MedioPagoPrueba medio, PlanSuscripcion plan) {
        Pagador pagador = Pagador.deLibreria(l, "AUTOMATICO");
        boolean paypal = MedioPagoPrueba.PAYPAL.equals(medio.getTipo());
        BigDecimal cobro = paypal ? aDolares(plan.getPrecio()) : plan.getPrecio();
        Pago pago = nuevoPago(pagador, plan, medio.getTipo(), describir(medio), paypal ? "USD" : "PEN", cobro);
        pago.setMedioId(medio.getId());

        String motivo = null;
        if (MedioPagoPrueba.YAPE.equals(medio.getTipo())) {
            motivo = "Yape no permite cobros automáticos.";
        } else if (medio.esTarjeta() && (medio.getVenceMes() == null || medio.getVenceAnio() == null
                || YearMonth.of(medio.getVenceAnio(), medio.getVenceMes()).isBefore(YearMonth.now()))) {
            motivo = "La tarjeta está vencida.";
        } else if ("BLOQUEADO".equals(medio.getEstado())) {
            motivo = paypal ? "La cuenta de PayPal está limitada." : "Tarjeta bloqueada.";
        } else if ("RECHAZADO".equals(medio.getEstado())) {
            motivo = "El banco rechazó la operación.";
        } else if (medio.getSaldo().compareTo(cobro) < 0) {
            motivo = paypal ? "Fondos insuficientes en PayPal."
                    : (MedioPagoPrueba.TARJETA_DEBITO.equals(medio.getTipo()) ? "Fondos insuficientes en la tarjeta de débito." : "Línea de crédito insuficiente.");
        }
        if (motivo != null) {
            pago.setEstado("RECHAZADO");
            pago.setMotivo(motivo);
            return pagoRepository.save(pago);
        }
        return aprobar(pago, medio, cobro);
    }

    /** Texto legible del medio: "Visa crédito •••• 1111", "Yape 999 ••• 222", "PayPal c•••@dominio". */
    public static String describir(MedioPagoPrueba m) {
        String id = m.getIdentificador();
        if (m.esTarjeta()) {
            return nombreMarca(m.getMarca()) + (MedioPagoPrueba.TARJETA_DEBITO.equals(m.getTipo()) ? " débito" : " crédito")
                    + " •••• " + id.substring(id.length() - 4);
        }
        if (MedioPagoPrueba.YAPE.equals(m.getTipo())) {
            return "Yape " + id.substring(0, 3) + " ••• " + id.substring(6);
        }
        return "PayPal " + enmascarar(id);
    }

    public BigDecimal aDolares(BigDecimal soles) {
        return soles.divide(tipoCambio, 2, RoundingMode.HALF_UP);
    }

    private Pago nuevoPago(Pagador pend, PlanSuscripcion plan, String metodo, String referencia,
                           String moneda, BigDecimal montoCobrado) {
        Pago p = new Pago();
        p.setRegistroId(pend.registroId());
        p.setLibreriaId(pend.libreriaId());
        p.setCorreo(pend.correo());
        p.setOrigen(pend.origen());
        p.setPlan(plan.name());
        p.setMonto(plan.getPrecio());
        p.setMontoCobrado(montoCobrado);
        p.setMoneda(moneda);
        p.setMetodo(metodo);
        p.setReferencia(referencia);
        p.setEstado("RECHAZADO");
        p.setFecha(LocalDateTime.now());
        return p;
    }

    /** Anota el rechazo y devuelve el error (el llamador hace "throw"). La transacción no se revierte. */
    private SuscripcionException rechazar(Pago pago, String campo, String mensaje) {
        pago.setEstado("RECHAZADO");
        pago.setMotivo(mensaje.length() > 120 ? mensaje.substring(0, 120) : mensaje);
        pagoRepository.save(pago);
        return new SuscripcionException(campo, mensaje, HttpStatus.PAYMENT_REQUIRED);
    }

    private Pago aprobar(Pago pago, MedioPagoPrueba medio, BigDecimal cobro) {
        medio.setSaldo(medio.getSaldo().subtract(cobro));
        medioRepository.save(medio);

        pago.setEstado("APROBADO");
        pago.setMotivo(null);
        pago.setCodigoAutorizacion(codigoAutorizacion());
        pago = pagoRepository.save(pago);
        pago.setNumeroOperacion(String.format("LR-%08d", pago.getId()));
        return pagoRepository.save(pago);
    }

    private String codigoAutorizacion() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String nombreMarca(String marca) {
        if (marca == null || marca.isBlank()) {
            return "Tarjeta";
        }
        return marca.charAt(0) + marca.substring(1).toLowerCase();
    }

    private static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return correo;
        }
        return correo.charAt(0) + "•••" + correo.substring(arroba);
    }

    static String digitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** MM/AA -> {mes, año completo} o null si el formato no es válido. */
    private static int[] parsearVence(String texto) {
        if (texto == null) {
            return null;
        }
        java.util.regex.Matcher m = Pattern.compile("^(\\d{2})/(\\d{2})$").matcher(texto.trim());
        if (!m.matches()) {
            return null;
        }
        int mes = Integer.parseInt(m.group(1));
        int anio = 2000 + Integer.parseInt(m.group(2));
        return (mes < 1 || mes > 12) ? null : new int[]{mes, anio};
    }

    /** Algoritmo de Luhn: el dígito de control de todo número de tarjeta real. */
    static boolean luhn(String numero) {
        int suma = 0;
        boolean doblar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (doblar) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            suma += d;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }
}