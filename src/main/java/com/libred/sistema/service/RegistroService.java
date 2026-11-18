package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.MedioPagoPrueba;
import com.libred.sistema.model.Pago;
import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.model.RegistroDtos.SolicitudFinalizar;
import com.libred.sistema.model.RegistroDtos.SolicitudPago;
import com.libred.sistema.model.RegistroPendiente;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MedioPagoPruebaRepository;
import com.libred.sistema.repository.PagoRepository;
import com.libred.sistema.repository.RegistroPendienteRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import com.libred.sistema.util.PlantillasCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import com.libred.sistema.util.PasswordUtil;

/** Registro de una librería nueva, en este orden: */
@Service
public class RegistroService {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 20;

    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VerificacionCodigoService codigos;
    private final CorreoSistemaService correoService;
    private final PagoSimuladoService pagoService;
    private final SuscripcionService suscripcionService;
    private final RegistroPendienteRepository pendienteRepository;
    private final LibreriaRepository libreriaRepository;
    private final PagoRepository pagoRepository;
    private final MedioPagoPruebaRepository medioRepository;

    public RegistroService(VerificacionCodigoService codigos, CorreoSistemaService correoService,
                           PagoSimuladoService pagoService, SuscripcionService suscripcionService,
                           RegistroPendienteRepository pendienteRepository, LibreriaRepository libreriaRepository,
                           PagoRepository pagoRepository,
                           MedioPagoPruebaRepository medioRepository) {
        this.codigos = codigos;
        this.correoService = correoService;
        this.pagoService = pagoService;
        this.suscripcionService = suscripcionService;
        this.pendienteRepository = pendienteRepository;
        this.libreriaRepository = libreriaRepository;
        this.pagoRepository = pagoRepository;
        this.medioRepository = medioRepository;
    }

    public Map<String, Object> enviarCodigo(String correoCrudo, Boolean acepta) {
        String correo = normalizarCorreo(correoCrudo);
        exigirTerminos(acepta);
        if (libreriaRepository.existsByCorreoCuenta(correo)) {
            throw new SuscripcionException("correo",
                    "Este correo ya tiene una librería registrada. Inicia sesión o recupera tu acceso.", HttpStatus.CONFLICT);
        }

        String codigo = codigos.generar(correo, VerificacionCodigoService.REGISTRO, VerificacionCodigoService.CORREO);
        enviarCodigoPorCorreo(correo, codigo, "REGISTRO");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("correo", correo);
        r.put("espera", VerificacionCodigoService.CORREO.esperaSegundos());
        return r;
    }

    private void enviarCodigoPorCorreo(String correo, String codigo, String motivo) {
        int minutos = VerificacionCodigoService.CORREO.vigenciaMinutos();
        if (!correoService.disponible()) {
            // Sin Gmail configurado (libred.soporte.clave vacía) el sistema sigue siendo probable: el código sale en
            // la consola.
            log.warn("[{}] El envío de correos no está configurado. Código para {}: {}", motivo, correo, codigo);
            return;
        }
        // Espera hasta 15 s a que Gmail confirme (la página muestra la animación de envío). Si tarda más no es un
        // error: el correo sigue en camino.
        CorreoSistemaService.Resultado r = correoService.enviarYEsperar(correo, "Tu código de verificación de LibRed: " + codigo,
                PlantillasCorreo.codigo(
                        "Verifica tu correo",
                        "Usa este código para continuar con tu registro en LibRed.",
                        codigo, minutos,
                        "Si no fuiste tú, puedes ignorar este mensaje."), 15);
        if (r == CorreoSistemaService.Resultado.FALLO) {
            throw new SuscripcionException("correo",
                    "No pudimos enviar el código. Revisa que el correo sea correcto e inténtalo de nuevo.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    @Transactional(noRollbackFor = SuscripcionException.class)
    public Map<String, Object> verificarCodigo(String correoCrudo, String codigo, Boolean acepta) {
        String correo = normalizarCorreo(correoCrudo);
        exigirTerminos(acepta);
        codigos.validar(correo, VerificacionCodigoService.REGISTRO, codigo, VerificacionCodigoService.CORREO, "codigo");

        if (libreriaRepository.existsByCorreoCuenta(correo)) {
            throw new SuscripcionException("correo", "Este correo ya tiene una librería registrada.", HttpStatus.CONFLICT);
        }

        // Si ya había un registro en curso con este correo (por ejemplo, un pago ya aprobado), se retoma
        RegistroPendiente pend = pendienteRepository.findFirstByCorreoAndEstadoNotOrderByIdDesc(correo, "COMPLETADO")
                .orElseGet(() -> {
                    RegistroPendiente nuevo = new RegistroPendiente();
                    nuevo.setToken(UUID.randomUUID().toString());
                    nuevo.setCorreo(correo);
                    return nuevo;
                });
        if (pend.getTerminosAceptadosEn() == null) {
            pend.setTerminosAceptadosEn(LocalDateTime.now());
        }
        pend = pendienteRepository.save(pend);
        return estadoDe(pend);
    }

    public Map<String, Object> estado(String token) {
        return estadoDe(buscar(token));
    }

    private Map<String, Object> estadoDe(RegistroPendiente pend) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("token", pend.getToken());
        r.put("correo", pend.getCorreo());
        r.put("estado", pend.getEstado());
        r.put("plan", pend.getPlan());
        return r;
    }

    /** Solo valida las credenciales de la cuenta PayPal de prueba (pantalla "Inicia sesión"). */
    public Map<String, Object> validarPaypal(String token, String planCodigo, String correo, String clave) {
        buscar(token);
        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(planCodigo);
        if (plan == null) {
            throw new SuscripcionException("plan", "Selecciona un plan válido.", HttpStatus.BAD_REQUEST);
        }
        MedioPagoPrueba cuenta = pagoService.validarCuentaPaypal(correo, clave);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("nombre", cuenta.getTitular());
        r.put("montoUsd", monto(pagoService.aDolares(plan.getPrecio())));
        return r;
    }

    /** Yape: genera el código de aprobación (lo "muestra" la app de Yape simulada). */
    public Map<String, Object> solicitarCodigoYape(String token, String celularCrudo) {
        buscar(token);
        String celular = celularCrudo == null ? "" : celularCrudo.replaceAll("\\D", "");
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

    @Transactional(noRollbackFor = SuscripcionException.class)
    public Map<String, Object> pagar(SolicitudPago s) {
        RegistroPendiente pend = buscarBloqueado(s.token());
        if ("PAGADO".equals(pend.getEstado()) || "COMPLETADO".equals(pend.getEstado())) {
            throw new SuscripcionException(null, "Ya tienes un pago aprobado en este registro.", HttpStatus.CONFLICT);
        }
        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(s.plan());
        if (plan == null) {
            throw new SuscripcionException("plan", "Selecciona un plan válido.", HttpStatus.BAD_REQUEST);
        }

        Pago pago = pagoService.cobrar(pend, plan, s);

        pend.setPlan(plan.name());
        pend.setEstado("PAGADO");
        pend.setPagoId(pago.getId());
        pendienteRepository.save(pend);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("operacion", pago.getNumeroOperacion());
        r.put("autorizacion", pago.getCodigoAutorizacion());
        r.put("metodo", pago.getReferencia());
        r.put("plan", plan.getNombre());
        r.put("monto", monto(pago.getMonto()));
        r.put("montoCobrado", monto(pago.getMontoCobrado()));
        r.put("moneda", pago.getMoneda());
        return r;
    }

    @Transactional
    public Map<String, Object> finalizar(SolicitudFinalizar f) {
        RegistroPendiente pend = buscarBloqueado(f.token());
        if ("COMPLETADO".equals(pend.getEstado())) {
            throw new SuscripcionException(null, "Este registro ya fue completado.", HttpStatus.CONFLICT);
        }
        if (!"PAGADO".equals(pend.getEstado()) || pend.getPagoId() == null) {
            throw new SuscripcionException(null, "Primero completa el pago de tu membresía.", HttpStatus.CONFLICT);
        }
        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(pend.getPlan());
        Pago pago = pagoRepository.findById(pend.getPagoId()).orElse(null);
        if (plan == null || pago == null || !"APROBADO".equals(pago.getEstado())) {
            throw new SuscripcionException(null, "No encontramos tu pago aprobado. Vuelve a iniciar el registro.", HttpStatus.CONFLICT);
        }

        String nombre = limpiar(f.nombreComercial());
        if (nombre.length() < 2 || nombre.length() > 150) {
            throw new SuscripcionException("nombreComercial", "Ingresa el nombre comercial.", HttpStatus.BAD_REQUEST);
        }
        String ruc = limpiar(f.ruc());
        if (!ruc.matches("\\d{11}")) {
            throw new SuscripcionException("ruc", "El RUC debe tener 11 dígitos.", HttpStatus.BAD_REQUEST);
        }
        if (!rucValido(ruc)) {
            throw new SuscripcionException("ruc", "El RUC no es válido. Revisa los dígitos.", HttpStatus.BAD_REQUEST);
        }
        if (libreriaRepository.existsByRuc(ruc)) {
            throw new SuscripcionException("ruc", "Este RUC ya está registrado en LibRed.", HttpStatus.CONFLICT);
        }
        String direccion = limpiar(f.direccion());
        if (direccion.length() < 5 || direccion.length() > 255) {
            throw new SuscripcionException("direccion", "Ingresa la dirección del establecimiento.", HttpStatus.BAD_REQUEST);
        }
        validarPassword(f.password(), "password");

        // Perfil opcional: se puede omitir por completo
        String telefono = f.telefono() == null ? "" : f.telefono().replaceAll("\\s+", "");
        if (!telefono.isEmpty() && !telefono.matches("\\d{7,9}")) {
            throw new SuscripcionException("telefono", "El teléfono debe tener entre 7 y 9 dígitos.", HttpStatus.BAD_REQUEST);
        }
        String correoContacto = limpiar(f.correoContacto()).toLowerCase();
        if (correoContacto.isEmpty()) {
            correoContacto = pend.getCorreo();   // por defecto, el mismo correo de la cuenta
        } else if (correoContacto.length() > 120 || !correoContacto.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new SuscripcionException("correoContacto", "Ingresa un correo válido.", HttpStatus.BAD_REQUEST);
        }
        String descripcion = limpiar(f.descripcion());
        if (descripcion.length() > 300) {
            throw new SuscripcionException("descripcion", "La descripción no puede superar los 300 caracteres.", HttpStatus.BAD_REQUEST);
        }
        String horario = limpiar(f.horario());
        if (horario.length() > 120) {
            throw new SuscripcionException("horario", "El horario no puede superar los 120 caracteres.", HttpStatus.BAD_REQUEST);
        }
        if (libreriaRepository.existsByCorreoCuenta(pend.getCorreo())) {
            throw new SuscripcionException(null, "Este correo ya tiene una librería registrada.", HttpStatus.CONFLICT);
        }

        LocalDateTime ahora = LocalDateTime.now();
        Libreria nueva = new Libreria();
        nueva.setNombreComercial(nombre);
        nueva.setRuc(ruc);
        nueva.setDireccion(direccion);
        nueva.setPassword(PasswordUtil.hash(f.password()));
        nueva.setCodigoAdmin(suscripcionService.generarSiguienteCodigoAdmin());
        nueva.setEstadoSuscripcion("NORMAL");
        nueva.setTipoPlan(plan.name());
        nueva.setFechaVencimiento(ahora.plusDays(plan.getDias()));
        nueva.setTelefonoContacto(telefono.isEmpty() ? null : telefono);
        nueva.setCorreoContacto(correoContacto);
        nueva.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        nueva.setHorarioAtencion(horario.isEmpty() ? null : horario);
        nueva.setCorreoCuenta(pend.getCorreo());
        nueva.setCorreoVerificado(true);
        nueva.setTerminosAceptadosEn(pend.getTerminosAceptadosEn());
        final Libreria l = libreriaRepository.saveAndFlush(nueva);

        // El pago queda asociado a la librería. Con tarjeta o PayPal la membresía se renueva sola (se puede cancelar
        // en "Ver plan actual");
        pago.setLibreriaId(l.getId());
        pagoRepository.save(pago);
        if (pago.getMedioId() != null) {
            medioRepository.findById(pago.getMedioId())
                    .filter(m -> !MedioPagoPrueba.YAPE.equals(m.getTipo()))
                    .ifPresent(m -> {
                        l.setMedioRenovacionId(m.getId());
                        l.setRenovacionAutomatica(true);
                        libreriaRepository.save(l);
                    });
        }

        pend.setEstado("COMPLETADO");
        pend.setLibreriaId(l.getId());
        pendienteRepository.save(pend);

        String montoTexto = "PEN".equals(pago.getMoneda())
                ? "S/ " + monto(pago.getMonto())
                : "S/ " + monto(pago.getMonto()) + " (US$ " + monto(pago.getMontoCobrado()) + ")";
        // El comprobante NO se envía aquí: esta transacción todavía no se confirmó. El controlador lo envía después
        // (enviarComprobante) y espera a que Gmail confirme antes de responder a la página.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(CLAVE_ASUNTO, "Comprobante de pago · " + l.getCodigoAdmin());
        r.put(CLAVE_HTML, PlantillasCorreo.comprobante(l.getNombreComercial(), l.getCodigoAdmin(), plan.getNombre(), montoTexto,
                pago.getReferencia(), pago.getNumeroOperacion(), pago.getFecha().format(FECHA_HORA),
                l.getFechaVencimiento().format(FECHA)));
        r.put("ok", true);
        r.put("codigoAdmin", l.getCodigoAdmin());
        r.put("nombreComercial", l.getNombreComercial());
        r.put("plan", plan.getNombre());
        r.put("vence", l.getFechaVencimiento().format(FECHA));
        r.put("correo", pend.getCorreo());
        r.put("renovacionAutomatica", l.isRenovacionAutomatica());
        return r;
    }

    /** Claves internas (no se envían a la página) con el correo del comprobante preparado por finalizar(). */
    public static final String CLAVE_ASUNTO = "_asunto";
    public static final String CLAVE_HTML = "_html";

    /**
     * Envía el comprobante del registro ya confirmado y espera hasta 15 s a que Gmail lo acepte. Nunca revierte el
     * registro: si falla o tarda más, la librería ya existe y la página lo informa con otro texto.
     */
    public Map<String, Object> enviarComprobante(Map<String, Object> resultado) {
        String asunto = (String) resultado.remove(CLAVE_ASUNTO);
        String html = (String) resultado.remove(CLAVE_HTML);
        CorreoSistemaService.Resultado envio = CorreoSistemaService.Resultado.FALLO;
        if (asunto != null && html != null) {
            envio = correoService.enviarYEsperar((String) resultado.get("correo"), asunto, html, 15);
        }
        resultado.put("comprobanteEnviado", envio == CorreoSistemaService.Resultado.ENVIADO);
        return resultado;
    }

    public static void validarPassword(String password, String campo) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new SuscripcionException(campo,
                    "La contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.", HttpStatus.BAD_REQUEST);
        }
        if (password.contains(" ")) {
            throw new SuscripcionException(campo, "La contraseña no puede tener espacios.", HttpStatus.BAD_REQUEST);
        }
    }

    public static String normalizarCorreo(String correo) {
        String c = correo == null ? "" : correo.trim().toLowerCase();
        if (c.length() > 120 || !CORREO.matcher(c).matches()) {
            throw new SuscripcionException("correo", "Ingresa un correo válido.", HttpStatus.BAD_REQUEST);
        }
        return c;
    }

    private void exigirTerminos(Boolean acepta) {
        if (acepta == null || !acepta) {
            throw new SuscripcionException("terminos", "Debes aceptar los términos y condiciones.", HttpStatus.BAD_REQUEST);
        }
    }

    private RegistroPendiente buscar(String token) {
        return pendienteRepository.findByToken(token == null ? "" : token)
                .filter(p -> !"COMPLETADO".equals(p.getEstado()))
                .orElseThrow(() -> new SuscripcionException(null,
                        "Tu sesión de registro venció. Verifica tu correo nuevamente.", HttpStatus.UNAUTHORIZED));
    }

    private RegistroPendiente buscarBloqueado(String token) {
        return pendienteRepository.buscarParaActualizar(token == null ? "" : token)
                .orElseThrow(() -> new SuscripcionException(null,
                        "Tu sesión de registro venció. Verifica tu correo nuevamente.", HttpStatus.UNAUTHORIZED));
    }

    /** RUC peruano: 11 dígitos, empieza con 10, 15, 16, 17 o 20 y cumple el dígito verificador de la SUNAT. */
    public static boolean rucValido(String ruc) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            return false;
        }
        String prefijo = ruc.substring(0, 2);
        if (!(prefijo.equals("10") || prefijo.equals("15") || prefijo.equals("16") || prefijo.equals("17") || prefijo.equals("20"))) {
            return false;
        }
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += (ruc.charAt(i) - '0') * pesos[i];
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        return digito == (ruc.charAt(10) - '0');
    }

    private String monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}