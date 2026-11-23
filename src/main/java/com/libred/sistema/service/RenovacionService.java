package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.MedioPagoPrueba;
import com.libred.sistema.model.Pago;
import com.libred.sistema.model.PlanSuscripcion;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MedioPagoPruebaRepository;
import com.libred.sistema.util.PlantillasCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Tarea programada de membresías (se ejecuta sola cada 10 minutos): */
@Service
public class RenovacionService {

    private static final Logger log = LoggerFactory.getLogger(RenovacionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_INTENTOS = 3;
    private static final int DIAS_AVISO = 3;

    private final LibreriaRepository libreriaRepository;
    private final MedioPagoPruebaRepository medioRepository;
    private final PagoSimuladoService pagoService;
    private final SuscripcionService suscripcionService;
    private final CorreoSistemaService correoService;
    private final TransactionTemplate tx;

    public RenovacionService(LibreriaRepository libreriaRepository, MedioPagoPruebaRepository medioRepository,
                             PagoSimuladoService pagoService, SuscripcionService suscripcionService,
                             CorreoSistemaService correoService, PlatformTransactionManager txManager) {
        this.libreriaRepository = libreriaRepository;
        this.medioRepository = medioRepository;
        this.pagoService = pagoService;
        this.suscripcionService = suscripcionService;
        this.correoService = correoService;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(initialDelay = 45_000, fixedDelay = 600_000)
    public void ejecutar() {
        try {
            renovarVencidas();
            avisarProximasAVencer();
        } catch (Exception e) {
            log.warn("No se pudo completar la tarea de renovaciones: {}", e.getMessage());
        }
    }

    void renovarVencidas() {
        List<Libreria> vencidas = libreriaRepository
                .findByRenovacionAutomaticaTrueAndFechaVencimientoLessThanEqual(LocalDateTime.now());
        for (Libreria l : vencidas) {
            try {
                tx.executeWithoutResult(estado -> renovar(l.getId()));
            } catch (Exception e) {
                log.warn("No se pudo renovar la librería {}: {}", l.getCodigoAdmin(), e.getMessage());
            }
        }
    }

    private void renovar(Integer libreriaId) {
        Libreria l = libreriaRepository.findById(libreriaId).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        if (l == null || !l.isRenovacionAutomatica() || l.getFechaVencimiento() == null
                || l.getFechaVencimiento().isAfter(ahora)) {
            return;
        }
        if (l.getProximoIntentoRenovacion() != null && l.getProximoIntentoRenovacion().isAfter(ahora)) {
            return;   // ya se intentó hoy
        }

        PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(l.getTipoPlan());
        if (plan == null) {
            plan = PlanSuscripcion.MENSUAL;
        }
        MedioPagoPrueba medio = l.getMedioRenovacionId() == null ? null
                : medioRepository.findById(l.getMedioRenovacionId())
                        .flatMap(m -> medioRepository.findByIdentificador(m.getIdentificador()))   // con bloqueo: se descuenta saldo
                        .orElse(null);

        if (medio == null) {
            registrarRechazo(l, plan, "Método de pago", "No encontramos el método de pago guardado.");
            return;
        }

        Pago pago = pagoService.cobrarRenovacion(l, medio, plan);
        if ("APROBADO".equals(pago.getEstado())) {
            // El nuevo periodo continúa donde terminó el anterior (si se atrasó mucho, cuenta desde hoy)
            LocalDateTime base = l.getFechaVencimiento().plusDays(plan.getDias()).isAfter(ahora)
                    ? l.getFechaVencimiento() : ahora;
            l.setFechaVencimiento(base.plusDays(plan.getDias()));
            l.setEstadoSuscripcion("NORMAL");
            l.setIntentosRenovacion(0);
            l.setProximoIntentoRenovacion(null);
            libreriaRepository.save(l);
            suscripcionService.enviarComprobante(l, plan, pago, true);
        } else {
            registrarRechazo(l, plan, pago.getReferencia(), pago.getMotivo());
        }
    }

    private void registrarRechazo(Libreria l, PlanSuscripcion plan, String metodo, String motivo) {
        int intentos = l.getIntentosRenovacion() + 1;
        boolean desactivada = intentos >= MAX_INTENTOS;
        l.setIntentosRenovacion(intentos);
        l.setProximoIntentoRenovacion(LocalDateTime.now().plusDays(1));
        if (desactivada) {
            l.setRenovacionAutomatica(false);
        }
        libreriaRepository.save(l);
        if (l.getCorreoCuenta() != null) {
            correoService.enviarEnSegundoPlano(l.getCorreoCuenta(), "No pudimos renovar tu membresía · " + l.getCodigoAdmin(),
                    PlantillasCorreo.cobroFallido(l.getNombreComercial(), plan.getNombre(), metodo, motivo,
                            l.getFechaVencimiento().format(FECHA), desactivada));
        }
    }

    void avisarProximasAVencer() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Libreria> lista = libreriaRepository
                .findByRenovacionAutomaticaFalseAndFechaVencimientoBetween(ahora, ahora.plusDays(DIAS_AVISO));
        for (Libreria l : lista) {
            if (l.getCorreoCuenta() == null || l.getFechaVencimiento().equals(l.getAvisoVencimientoPara())) {
                continue;
            }
            try {
                PlanSuscripcion plan = PlanSuscripcion.desdeCodigo(l.getTipoPlan());
                int dias = (int) Math.max(1, Math.ceil(Duration.between(ahora, l.getFechaVencimiento()).toMinutes() / 1440.0));
                correoService.enviarEnSegundoPlano(l.getCorreoCuenta(), "Tu membresía vence pronto · " + l.getCodigoAdmin(),
                        PlantillasCorreo.avisoVencimiento(l.getNombreComercial(),
                                plan == null ? "Plan Mensual" : plan.getNombre(),
                                l.getFechaVencimiento().format(FECHA), dias));
                l.setAvisoVencimientoPara(l.getFechaVencimiento());
                libreriaRepository.save(l);
            } catch (Exception e) {
                log.warn("No se pudo avisar el vencimiento de {}: {}", l.getCodigoAdmin(), e.getMessage());
            }
        }
    }
}