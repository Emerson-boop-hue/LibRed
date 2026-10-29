package com.libred.sistema.service;

import com.libred.sistema.model.RegistroSuscripcionRequest;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SuscripcionService {

    // Reglas de la contraseña del administrador (máximo 6 según el diseño; el mínimo se puede cambiar aquí)
    private static final int PASSWORD_MIN = 4;
    private static final int PASSWORD_MAX = 6;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LibreriaRepository libreriaRepository;

    public SuscripcionService(LibreriaRepository libreriaRepository) {
        this.libreriaRepository = libreriaRepository;
    }

    // =====================================================================
    // ERROR DE NEGOCIO (lo convierte en JSON el controlador)
    // =====================================================================
    public static class SuscripcionException extends RuntimeException {
        private final String campo;       // campo del formulario al que se refiere el error (puede ser null)
        private final HttpStatus estado;  // 400 = dato inválido, 404 = no existe, 409 = duplicado/ya aplicado

        public SuscripcionException(String campo, String mensaje, HttpStatus estado) {
            super(mensaje);
            this.campo = campo;
            this.estado = estado;
        }

        public String getCampo() {
            return campo;
        }

        public HttpStatus getEstado() {
            return estado;
        }
    }

    // =====================================================================
    // PLANES DISPONIBLES (paso 1 del asistente)
    // =====================================================================
    public List<Map<String, Object>> listarPlanes() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (PlanSuscripcion plan : PlanSuscripcion.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo", plan.name());
            m.put("nombre", plan.getNombre());
            m.put("precio", monto(plan.getPrecio()));
            m.put("periodo", plan.getPeriodo());
            m.put("dias", plan.getDias());
            if (plan == PlanSuscripcion.ANUAL) {
                m.put("ahorro", monto(ahorroAnual()));
                m.put("equivalenteMensual",
                        monto(plan.getPrecio().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)));
            }
            lista.add(m);
        }
        return lista;
    }

    // =====================================================================
    // CÓDIGO DE ADMINISTRADOR AUTOGENERADO (ADM-003, ADM-004, ...)
    // =====================================================================
    public String generarSiguienteCodigoAdmin() {
        long numero = libreriaRepository.count() + 1;
        String codigo = formatoCodigo(numero);
        while (libreriaRepository.existsByCodigoAdmin(codigo)) {
            numero++;
            codigo = formatoCodigo(numero);
        }
        return codigo;
    }

    private String formatoCodigo(long numero) {
        return String.format("ADM-%03d", numero);
    }

    // =====================================================================
    // REGISTRO DE UNA LIBRERÍA NUEVA (paso 3 -> "Finalizar Registro")
    // =====================================================================
    @Transactional
    public Libreria registrarLibreria(RegistroSuscripcionRequest r) {
        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(r.getTipoPlan());
        if (plan == null) {
            throw new SuscripcionException("tipoPlan", "Selecciona un plan válido.", HttpStatus.BAD_REQUEST);
        }

        String nombre = limpiar(r.getNombreComercial());
        if (nombre.length() < 2 || nombre.length() > 150) {
            throw new SuscripcionException("nombreComercial",
                    "El nombre comercial es obligatorio (máximo 150 caracteres).", HttpStatus.BAD_REQUEST);
        }

        String ruc = limpiar(r.getRuc());
        if (!ruc.matches("\\d{11}")) {
            throw new SuscripcionException("ruc", "El RUC debe tener exactamente 11 dígitos.", HttpStatus.BAD_REQUEST);
        }
        if (libreriaRepository.existsByRuc(ruc)) {
            throw new SuscripcionException("ruc", "Este RUC ya está registrado en LibRed.", HttpStatus.CONFLICT);
        }

        String direccion = limpiar(r.getDireccion());
        if (direccion.length() < 5 || direccion.length() > 255) {
            throw new SuscripcionException("direccion",
                    "La dirección es obligatoria (máximo 255 caracteres).", HttpStatus.BAD_REQUEST);
        }

        String password = r.getPassword() == null ? "" : r.getPassword();
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new SuscripcionException("password",
                    "La contraseña debe tener entre " + PASSWORD_MIN + " y " + PASSWORD_MAX + " caracteres.",
                    HttpStatus.BAD_REQUEST);
        }

        Libreria nueva = new Libreria();
        nueva.setNombreComercial(nombre);
        nueva.setRuc(ruc);
        nueva.setDireccion(direccion);
        nueva.setPassword(password);
        nueva.setCodigoAdmin(generarSiguienteCodigoAdmin()); // se vuelve a generar aquí: es el valor definitivo
        nueva.setEstadoSuscripcion("NORMAL");
        nueva.setTipoPlan(plan.name());
        nueva.setFechaVencimiento(LocalDateTime.now().plusDays(plan.getDias()));
        return libreriaRepository.save(nueva);
    }

    // =====================================================================
    // ESTADO DE LA MEMBRESÍA (modal "Ver plan actual")
    // =====================================================================
    public Map<String, Object> obtenerEstado(Integer libreriaId) {
        Libreria l = buscarLibreria(libreriaId);

        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(l.getTipoPlan());
        if (plan == null) {
            plan = PlanSuscripcion.MENSUAL;
        }

        LocalDateTime vencimiento = l.getFechaVencimiento();
        boolean vigente = true;
        Long diasRestantes = null;
        if (vencimiento != null) {
            long minutos = Duration.between(LocalDateTime.now(), vencimiento).toMinutes();
            vigente = minutos >= 0;
            // Se redondea hacia arriba: si faltan 29 días y 20 horas se muestra "30 días"
            diasRestantes = vigente ? (long) Math.ceil(minutos / 1440.0) : 0L;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombreComercial", l.getNombreComercial());
        m.put("codigoAdmin", l.getCodigoAdmin());
        m.put("tipoPlan", plan.name());
        m.put("nombrePlan", plan.getNombre());
        m.put("precio", monto(plan.getPrecio()));
        m.put("periodo", plan.getPeriodo());
        m.put("estado", vigente ? "ACTIVO" : "VENCIDO");
        m.put("vigente", vigente);
        m.put("diasRestantes", diasRestantes);
        m.put("fechaVencimiento", vencimiento == null ? "—" : vencimiento.format(FORMATO_FECHA));
        m.put("puedeMigrar", plan == PlanSuscripcion.MENSUAL);
        m.put("precioAnual", monto(PlanSuscripcion.ANUAL.getPrecio()));
        m.put("ahorroAnual", monto(ahorroAnual()));
        return m;
    }

    // =====================================================================
    // MIGRACIÓN MENSUAL -> ANUAL (es el UPDATE sobre la tabla libreria)
    // =====================================================================
    @Transactional
    public Map<String, Object> migrarAPlanAnual(Integer libreriaId) {
        Libreria l = buscarLibreria(libreriaId);

        if (PlanSuscripcion.ANUAL.name().equalsIgnoreCase(l.getTipoPlan())) {
            throw new SuscripcionException(null, "Tu librería ya cuenta con el Plan Anual.", HttpStatus.CONFLICT);
        }

        // Si todavía le quedan días al plan mensual, el año nuevo se suma a partir de esa fecha (no se pierden días)
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime base = (l.getFechaVencimiento() != null && l.getFechaVencimiento().isAfter(ahora))
                ? l.getFechaVencimiento()
                : ahora;

        l.setTipoPlan(PlanSuscripcion.ANUAL.name());
        l.setFechaVencimiento(base.plusDays(PlanSuscripcion.ANUAL.getDias()));
        l.setEstadoSuscripcion("NORMAL");
        libreriaRepository.save(l);

        return obtenerEstado(libreriaId);
    }

    // =====================================================================
    // UTILIDADES
    // =====================================================================
    private Libreria buscarLibreria(Integer id) {
        return libreriaRepository.findById(id).orElseThrow(
                () -> new SuscripcionException(null, "No se encontró la librería.", HttpStatus.NOT_FOUND));
    }

    private BigDecimal ahorroAnual() {
        return PlanSuscripcion.MENSUAL.getPrecio().multiply(BigDecimal.valueOf(12))
                .subtract(PlanSuscripcion.ANUAL.getPrecio());
    }

    private String monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String limpiar(String texto) {
        return texto == null ? "" : texto.trim();
    }
}