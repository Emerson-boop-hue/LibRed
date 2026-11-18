package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.MedioPagoPrueba;
import com.libred.sistema.model.Pago;
import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.model.RegistroDtos.SolicitudPago;
import com.libred.sistema.model.RegistroDtos.SolicitudRenovar;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MedioPagoPruebaRepository;
import com.libred.sistema.repository.PagoRepository;
import com.libred.sistema.util.PlantillasCorreo;
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

/** Suscripción de las librerías: plan, estado y renovación. */
@Service
public class SuscripcionService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LibreriaRepository libreriaRepository;
    private final PagoRepository pagoRepository;
    private final MedioPagoPruebaRepository medioRepository;
    private final PagoSimuladoService pagoService;
    private final CorreoSistemaService correoService;

    public SuscripcionService(LibreriaRepository libreriaRepository, PagoRepository pagoRepository,
                              MedioPagoPruebaRepository medioRepository, PagoSimuladoService pagoService,
                              CorreoSistemaService correoService) {
        this.libreriaRepository = libreriaRepository;
        this.pagoRepository = pagoRepository;
        this.medioRepository = medioRepository;
        this.pagoService = pagoService;
        this.correoService = correoService;
    }

    // ERROR DE NEGOCIO (lo convierte en JSON el controlador)
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

    // PLANES DISPONIBLES (paso 1 del asistente)
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

    // ESTADO DE LA MEMBRESÍA (modal "Ver plan actual")
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
        // Datos extra para el modal (solo lectura)
        m.put("diasPlan", plan.getDias());
        m.put("inicioPeriodo", vencimiento == null ? "—" : vencimiento.minusDays(plan.getDias()).format(FORMATO_FECHA));
        m.put("miembroDesde", l.getFechaRegistro() == null ? "—" : l.getFechaRegistro().format(FORMATO_FECHA));
        m.put("totalAnualMensual", monto(PlanSuscripcion.MENSUAL.getPrecio().multiply(BigDecimal.valueOf(12))));
        // Cómo paga y cómo se renueva
        Pago ultimo = pagoRepository.findFirstByLibreriaIdAndEstadoOrderByIdDesc(libreriaId, "APROBADO").orElse(null);
        m.put("metodoPago", ultimo == null || ultimo.getReferencia() == null ? "Sin pagos registrados" : ultimo.getReferencia());
        m.put("ultimoPago", ultimo == null ? null : ultimo.getFecha().format(FORMATO_FECHA));

        MedioPagoPrueba medio = l.getMedioRenovacionId() == null ? null
                : medioRepository.findById(l.getMedioRenovacionId()).orElse(null);
        String renovacion = l.isRenovacionAutomatica() ? "AUTOMATICA" : (medio != null ? "CANCELADA" : "MANUAL");
        m.put("renovacion", renovacion);
        m.put("medioRenovacion", medio == null ? null : PagoSimuladoService.describir(medio));
        m.put("puedeReactivar", "CANCELADA".equals(renovacion) && vigente && medioUsable(medio));
        m.put("cobroFallido", l.isRenovacionAutomatica() && l.getIntentosRenovacion() > 0);
        m.put("proximoCobro", monto(plan.getPrecio()));
        m.put("usdMensual", monto(pagoService.aDolares(PlanSuscripcion.MENSUAL.getPrecio())));
        m.put("usdAnual", monto(pagoService.aDolares(PlanSuscripcion.ANUAL.getPrecio())));
        return m;
    }

    private boolean medioUsable(MedioPagoPrueba medio) {
        if (medio == null || MedioPagoPrueba.YAPE.equals(medio.getTipo()) || !"ACTIVO".equals(medio.getEstado())) {
            return false;
        }
        return !medio.esTarjeta() || (medio.getVenceMes() != null && medio.getVenceAnio() != null
                && !java.time.YearMonth.of(medio.getVenceAnio(), medio.getVenceMes()).isBefore(java.time.YearMonth.now()));
    }

    // RENOVAR O PASAR AL PLAN ANUAL (el administrador paga desde "Ver plan actual")
    @Transactional(noRollbackFor = SuscripcionException.class)
    public Map<String, Object> pagar(Integer libreriaId, SolicitudRenovar s) {
        Libreria l = libreriaRepository.findById(libreriaId).orElseThrow(
                () -> new SuscripcionException(null, "No se encontró la librería.", HttpStatus.NOT_FOUND));

        PlanSuscripcion actual = PlanSuscripcion.desdeCodigo(l.getTipoPlan());
        if (actual == null) {
            actual = PlanSuscripcion.MENSUAL;
        }
        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(s.plan());
        if (plan == null) {
            throw new SuscripcionException("plan", "Selecciona un plan válido.", HttpStatus.BAD_REQUEST);
        }
        if (actual == PlanSuscripcion.ANUAL && plan == PlanSuscripcion.MENSUAL) {
            throw new SuscripcionException("plan", "Tu librería ya cuenta con el Plan Anual.", HttpStatus.CONFLICT);
        }

        SolicitudPago pagoSolicitud = new SolicitudPago(null, plan.name(), s.metodo(), s.numero(), s.titular(), s.vence(),
                s.cvv(), s.celular(), s.codigo(), s.correoPaypal(), s.clavePaypal());
        boolean mejora = actual == PlanSuscripcion.MENSUAL && plan == PlanSuscripcion.ANUAL;
        Pago pago = pagoService.cobrar(PagoSimuladoService.Pagador.deLibreria(l, "MANUAL"), plan, pagoSolicitud);

        // Si todavía le quedan días, el nuevo periodo se suma al final del actual (no se pierden días)
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime base = (l.getFechaVencimiento() != null && l.getFechaVencimiento().isAfter(ahora))
                ? l.getFechaVencimiento() : ahora;
        l.setTipoPlan(plan.name());
        l.setFechaVencimiento(base.plusDays(plan.getDias()));
        l.setEstadoSuscripcion("NORMAL");
        l.setIntentosRenovacion(0);
        l.setProximoIntentoRenovacion(null);

        // Tarjeta y PayPal pueden renovarse solos (si el administrador lo deja marcado); Yape nunca
        MedioPagoPrueba medio = pago.getMedioId() == null ? null : medioRepository.findById(pago.getMedioId()).orElse(null);
        if (medio != null && !MedioPagoPrueba.YAPE.equals(medio.getTipo())) {
            boolean auto = s.automatica() == null || s.automatica();
            l.setMedioRenovacionId(medio.getId());
            l.setRenovacionAutomatica(auto);
        }
        libreriaRepository.save(l);

        enviarComprobante(l, plan, pago, false);

        Map<String, Object> r = obtenerEstado(libreriaId);
        r.put("ok", true);
        r.put("mejora", mejora);
        r.put("operacion", pago.getNumeroOperacion());
        return r;
    }

    @Transactional
    public Map<String, Object> cancelarRenovacion(Integer libreriaId) {
        Libreria l = buscarLibreria(libreriaId);
        if (!l.isRenovacionAutomatica()) {
            throw new SuscripcionException(null, "Tu membresía no tiene renovación automática.", HttpStatus.CONFLICT);
        }
        l.setRenovacionAutomatica(false);
        l.setIntentosRenovacion(0);
        l.setProximoIntentoRenovacion(null);
        libreriaRepository.save(l);
        return obtenerEstado(libreriaId);
    }

    @Transactional
    public Map<String, Object> reactivarRenovacion(Integer libreriaId) {
        Libreria l = buscarLibreria(libreriaId);
        MedioPagoPrueba medio = l.getMedioRenovacionId() == null ? null
                : medioRepository.findById(l.getMedioRenovacionId()).orElse(null);
        if (l.getFechaVencimiento() != null && l.getFechaVencimiento().isBefore(LocalDateTime.now())) {
            throw new SuscripcionException(null, "Tu membresía ya venció. Renuévala con un método de pago.", HttpStatus.CONFLICT);
        }
        if (!medioUsable(medio)) {
            throw new SuscripcionException(null,
                    "No podemos reactivarla con tu método anterior. Paga con una tarjeta o PayPal para volver a activarla.",
                    HttpStatus.CONFLICT);
        }
        l.setRenovacionAutomatica(true);
        l.setIntentosRenovacion(0);
        l.setProximoIntentoRenovacion(null);
        libreriaRepository.save(l);
        return obtenerEstado(libreriaId);
    }

    /** Yape: genera el código de aprobación para pagar desde "Ver plan actual". */
    public Map<String, Object> solicitarCodigoYape(String celular) {
        return pagoService.emitirCodigoYape(celular);
    }

    /** Comprobante por correo (solo si la librería tiene correo de cuenta verificado). */
    void enviarComprobante(Libreria l, PlanSuscripcion plan, Pago pago, boolean automatica) {
        if (l.getCorreoCuenta() == null) {
            return;
        }
        String montoTexto = "PEN".equals(pago.getMoneda())
                ? "S/ " + monto(pago.getMonto())
                : "S/ " + monto(pago.getMonto()) + " (US$ " + monto(pago.getMontoCobrado()) + ")";
        correoService.enviarEnSegundoPlano(l.getCorreoCuenta(),
                (automatica ? "Renovamos tu membresía · " : "Comprobante de pago · ") + l.getCodigoAdmin(),
                PlantillasCorreo.comprobanteRenovacion(l.getNombreComercial(), plan.getNombre(), montoTexto,
                        pago.getReferencia(), pago.getNumeroOperacion(), pago.getFecha().format(FORMATO_FECHA_HORA),
                        l.getFechaVencimiento().format(FORMATO_FECHA), automatica));
    }

    private Libreria buscarLibreria(Integer id) {
        return libreriaRepository.findById(id).orElseThrow(
                () -> new SuscripcionException(null, "No se encontró la librería.", HttpStatus.NOT_FOUND));
    }

    private BigDecimal ahorroAnual() {
        return PlanSuscripcion.MENSUAL.getPrecio().multiply(BigDecimal.valueOf(12))
                .subtract(PlanSuscripcion.ANUAL.getPrecio());
    }

    String monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}