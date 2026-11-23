package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.RegistroDtos.SolicitudRegistroLector;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import com.libred.sistema.util.TelefonoUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import com.libred.sistema.util.PasswordUtil;

/** Acceso de los lectores que no depende del administrador: */
@Service
public class LectorAccesoService {

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'’-]{1,99}$");
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 20;

    // Tope de registros por equipo y hora (protege contra altas masivas)
    private static final int REGISTROS_POR_HORA = 10;

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final LectorService lectorService;
    private final VerificacionCodigoService codigos;
    private final SmsService sms;
    private final Map<String, Deque<Instant>> registrosPorEquipo = new ConcurrentHashMap<>();

    public LectorAccesoService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository,
                               LectorService lectorService, VerificacionCodigoService codigos, SmsService sms) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.lectorService = lectorService;
        this.codigos = codigos;
        this.sms = sms;
    }

    public Map<String, Object> registrar(SolicitudRegistroLector s, String equipo) {
        String nombres = normalizar(s.nombres());
        if (!NOMBRE.matcher(nombres).matches()) {
            throw error("nombres", "Ingresa tus nombres (solo letras, entre 2 y 100 caracteres).", HttpStatus.BAD_REQUEST);
        }
        String apellidos = normalizar(s.apellidos());
        if (!NOMBRE.matcher(apellidos).matches()) {
            throw error("apellidos", "Ingresa tus apellidos (solo letras, entre 2 y 100 caracteres).", HttpStatus.BAD_REQUEST);
        }
        String celular = celularValido(s.celular(), "celular");
        Libreria libreria = s.libreriaId() == null ? null : libreriaRepository.findById(s.libreriaId()).orElse(null);
        if (libreria == null) {
            throw error("libreriaId", "Elige la librería en la que te registras.", HttpStatus.BAD_REQUEST);
        }
        validarPassword(s.password(), "password");

        // Nombre completo y celular repetidos dentro de la librería elegida; celular de otra librería = misma persona (conserva su código)
        Map<String, String> repetidos = lectorService.duplicados(libreria.getId(), null, nombres, apellidos, celular);
        if (!repetidos.isEmpty()) {
            Map<String, String> porCampo = new LinkedHashMap<>();
            if (repetidos.containsKey(LectorService.DUP_NOMBRE)) {
                porCampo.put("nombre", "Ya existe un lector con esos nombres y apellidos en esta librería.");
            }
            if (repetidos.containsKey(LectorService.DUP_TELEFONO)) {
                porCampo.put("celular", "Este celular ya tiene una cuenta en esta librería. Inicia sesión o recupera tu acceso.");
            } else if (repetidos.containsKey(LectorService.DUP_TELEFONO_RED)) {
                // La persona ya tiene código: entra con él y elige esta librería (el sistema la registra ahí con el mismo código)
                porCampo.put("celular", "Este celular ya tiene una cuenta. Inicia sesión con tu código y elige esta librería, o recupera tu acceso.");
            }
            throw new DuplicadosException(porCampo);
        }
        controlarTope(equipo);

        Lector lector = new Lector();
        lector.setNombres(nombres);
        lector.setApellidos(apellidos);
        lector.setTelefono(celular);
        lector.setPassword(s.password());
        lectorService.guardar(libreria.getId(), lector); // genera el código LEC y lo deja HABILITADO

        String texto = "LibRed: ¡Bienvenido, " + nombres.split(" ")[0] + "! Tu código de lector es " + lector.getCodigoLector()
                + ". Ingresa a " + libreria.getNombreComercial() + " con él y tu contraseña.";
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("codigoLector", lector.getCodigoLector());
        r.put("libreriaId", libreria.getId());
        r.put("libreria", libreria.getNombreComercial());
        sms.enviar(celular, texto, null).ifPresent(m -> r.put("sms", m));
        return r;
    }

    private void controlarTope(String equipo) {
        Instant limite = Instant.now().minus(Duration.ofHours(1));
        Deque<Instant> marcas = registrosPorEquipo.computeIfAbsent(equipo == null ? "?" : equipo, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst().isBefore(limite)) {
                marcas.pollFirst();
            }
            if (marcas.size() >= REGISTROS_POR_HORA) {
                throw error(null, "Se hicieron demasiados registros desde este equipo. Intenta de nuevo más tarde.", HttpStatus.TOO_MANY_REQUESTS);
            }
            marcas.addLast(Instant.now());
        }
    }

    /** Código por SMS. La respuesta es la misma exista o no el celular. */
    public Map<String, Object> enviarCodigo(String celularCrudo) {
        String celular = celularValido(celularCrudo, "celular");
        String codigo = codigos.generar(celular, VerificacionCodigoService.RECUPERACION_LECTOR, VerificacionCodigoService.SMS);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("espera", VerificacionCodigoService.SMS.esperaSegundos());
        if (!lectorRepository.findAllByTelefono(celular).isEmpty()) {
            String texto = "LibRed: tu código para recuperar el acceso es " + codigo + ". Vence en "
                    + VerificacionCodigoService.SMS.vigenciaMinutos() + " minutos. No lo compartas con nadie.";
            sms.enviar(celular, texto, codigo).ifPresent(m -> r.put("sms", m));
        }
        return r;
    }

    /** Comprueba el código (sin consumirlo) y dice qué cuentas tienen ese celular. */
    public Map<String, Object> verificar(String celularCrudo, String codigo) {
        String celular = celularValido(celularCrudo, "celular");
        codigos.verificar(celular, VerificacionCodigoService.RECUPERACION_LECTOR, codigo, VerificacionCodigoService.SMS, "codigo");

        List<Map<String, Object>> cuentas = cuentasDelCelular(celular);
        if (cuentas.isEmpty()) {
            throw error("codigo", "El código venció o no existe. Solicita uno nuevo.", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("cuentas", cuentas);
        return r;
    }

    /** Contraseña nueva para la cuenta elegida (si el celular solo tiene una, no hace falta elegir). */
    @Transactional(noRollbackFor = SuscripcionException.class)
    public Map<String, Object> restablecer(String celularCrudo, String codigo, String codigoLector, String password) {
        String celular = celularValido(celularCrudo, "celular");
        validarPassword(password, "password");

        // La cuenta se decide ANTES de consumir el código, para no gastarlo si falta elegir
        Set<String> codigosDelCelular = new LinkedHashSet<>();
        for (Lector l : lectorRepository.findAllByTelefono(celular)) {
            codigosDelCelular.add(l.getCodigoLector());
        }
        if (codigosDelCelular.isEmpty()) {
            throw error("codigo", "El código venció o no existe. Solicita uno nuevo.", HttpStatus.BAD_REQUEST);
        }
        String elegido = codigoLector == null ? "" : codigoLector.trim();
        if (elegido.isEmpty() && codigosDelCelular.size() == 1) {
            elegido = codigosDelCelular.iterator().next();
        }
        if (elegido.isEmpty() || !codigosDelCelular.contains(elegido)) {
            throw error("cuenta", "Elige la cuenta que quieres recuperar.", HttpStatus.BAD_REQUEST);
        }

        codigos.validar(celular, VerificacionCodigoService.RECUPERACION_LECTOR, codigo, VerificacionCodigoService.SMS, "codigo");

        String hash = PasswordUtil.hash(password);
        List<Lector> registros = lectorRepository.findAllByCodigoLector(elegido);
        for (Lector registro : registros) {
            registro.setPassword(hash);
            registro.setDebeCambiarPassword(false);
        }
        lectorRepository.saveAll(registros);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("codigoLector", elegido);
        return r;
    }

    private List<Map<String, Object>> cuentasDelCelular(String celular) {
        Map<String, Map<String, Object>> porCodigo = new LinkedHashMap<>();
        for (Lector l : lectorRepository.findAllByTelefono(celular)) {
            Map<String, Object> cuenta = porCodigo.computeIfAbsent(l.getCodigoLector(), k -> {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("codigoLector", k);
                c.put("nombre", l.getNombres() + " " + l.getApellidos());
                c.put("librerias", new ArrayList<String>());
                return c;
            });
            @SuppressWarnings("unchecked")
            List<String> lista = (List<String>) cuenta.get("librerias");
            libreriaRepository.findById(l.getLibreriaId()).ifPresent(lib -> lista.add(lib.getNombreComercial()));
        }
        return new ArrayList<>(porCodigo.values());
    }

    private String celularValido(String crudo, String campo) {
        String digitos = TelefonoUtil.normalizar(crudo);
        if (digitos == null || !CELULAR.matcher(digitos).matches()) {
            throw error(campo, "Ingresa un celular de 9 dígitos que empiece con 9.", HttpStatus.BAD_REQUEST);
        }
        return digitos;
    }

    private void validarPassword(String password, String campo) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw error(campo, "La contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.", HttpStatus.BAD_REQUEST);
        }
        if (password.matches(".*\\s.*")) {
            throw error(campo, "La contraseña no puede tener espacios.", HttpStatus.BAD_REQUEST);
        }
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }

    /** Varios campos repetidos a la vez: la página los marca todos (el primero va también como "campo"/"error"). */
    public static class DuplicadosException extends SuscripcionException {
        private final Map<String, String> errores;

        public DuplicadosException(Map<String, String> errores) {
            super(errores.keySet().iterator().next(), errores.values().iterator().next(), HttpStatus.CONFLICT);
            this.errores = errores;
        }

        public Map<String, String> getErrores() {
            return errores;
        }
    }

    private static SuscripcionException error(String campo, String mensaje, HttpStatus estado) {
        return new SuscripcionException(campo, mensaje, estado);
    }
}