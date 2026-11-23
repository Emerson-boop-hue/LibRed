package com.libred.sistema.service;

import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.IngresoVista.Fila;
import com.libred.sistema.model.IngresoVista.Pantalla;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Módulo INGRESOS del administrador (solo lectura): el dinero que entró a caja en SU librería. Cada cobro se cuenta
 * UNA sola vez y en el día en que se cobró:
 */
@Service
public class IngresoService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;

    public IngresoService(PrestamoRepository prestamoRepository,
                          DevolucionRepository devolucionRepository,
                          MultaRepository multaRepository) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
    }

    /** Cobro con su fecha real (para ordenar) antes de convertirlo en fila de la vista. */
    private record Cobro(LocalDateTime fecha, Fila fila) {
    }

    @Transactional(readOnly = true)
    public Pantalla armarPantalla(Integer libreriaId) {
        List<Cobro> cobros = new ArrayList<>();

        // Servicio: préstamos ya entregados/cobrados
        for (Prestamo p : prestamoRepository.findByLibreriaId(libreriaId)) {
            if (!p.isServicioCobrado() || p.getFechaCobroServicio() == null || positivo(p.getMontoServicio()) == null) {
                continue;
            }
            Integer dias = p.getDiasPrestamo();
            String detalle = dias == null ? "Servicio de préstamo"
                    : dias + (dias == 1 ? " día" : " días") + " de préstamo";
            cobros.add(cobro(p.getFechaCobroServicio(), "SERVICIO", "Servicio", p, detalle, p.getMontoServicio()));
        }

        // Mora: solo la parte de mora de cada devolución
        for (Devolucion d : devolucionRepository.findByLibreriaId(libreriaId)) {
            if (d.getFechaDevolucion() == null || positivo(d.getMontoMora()) == null) {
                continue;
            }
            int atraso = d.getDiasRetraso() == null ? 0 : d.getDiasRetraso();
            String detalle = atraso + (atraso == 1 ? " día" : " días") + " de retraso";
            cobros.add(cobro(d.getFechaDevolucion(), "MORA", "Mora", d.getPrestamo(), detalle, d.getMontoMora()));
        }

        // Multas: solo las ya pagadas, el día en que se pagaron
        for (Multa m : multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PAGADO")) {
            LocalDateTime fecha = m.getFechaPago() != null ? m.getFechaPago() : m.getFechaEmision();
            if (fecha == null || positivo(m.getMonto()) == null) {
                continue;
            }
            Prestamo p = m.getPrestamo();
            String lector = nombre(m.getLector() != null ? m.getLector() : (p == null ? null : p.getLector()));
            String codLector = codigo(m.getLector() != null ? m.getLector() : (p == null ? null : p.getLector()));
            cobros.add(new Cobro(fecha, new Fila(
                    fecha.format(FECHA_HORA), fecha.format(DIA), "MULTA", "Multa",
                    lector, codLector,
                    p == null ? "—" : titulo(p.getRecurso()), p == null ? "" : codigo(p.getRecurso()),
                    m.getMotivo() == null ? "Multa" : m.getMotivo(), dinero(m.getMonto()))));
        }

        cobros.sort(Comparator.comparing(Cobro::fecha).reversed());
        List<Fila> filas = new ArrayList<>();
        for (Cobro c : cobros) {
            filas.add(c.fila());
        }

        // Por cobrar: pedidos de lectores todavía sin entregar
        List<Prestamo> pendientes = prestamoRepository.findByLibreriaIdAndEstadoPrestamo(libreriaId, "POR_ENTREGAR");
        BigDecimal porCobrar = BigDecimal.ZERO;
        for (Prestamo p : pendientes) {
            if (p.getMontoServicio() != null) {
                porCobrar = porCobrar.add(p.getMontoServicio());
            }
        }
        return new Pantalla(filas, dinero(porCobrar), pendientes.size());
    }

    private Cobro cobro(LocalDateTime fecha, String concepto, String texto, Prestamo p, String detalle, BigDecimal monto) {
        Lector lector = p == null ? null : p.getLector();
        Recurso recurso = p == null ? null : p.getRecurso();
        return new Cobro(fecha, new Fila(
                fecha.format(FECHA_HORA), fecha.format(DIA), concepto, texto,
                nombre(lector), codigo(lector), titulo(recurso), codigo(recurso),
                detalle, dinero(monto)));
    }

    /** null si no hay un monto mayor a cero (lo gratuito no es un ingreso). */
    private static BigDecimal positivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0 ? valor : null;
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String nombre(Lector l) {
        return l == null ? "Sin lector" : (l.getNombres() + " " + l.getApellidos()).trim();
    }

    private static String codigo(Lector l) {
        return l == null || l.getCodigoLector() == null ? "" : l.getCodigoLector();
    }

    private static String titulo(Recurso r) {
        return r == null ? "Sin artículo" : r.getTitulo();
    }

    private static String codigo(Recurso r) {
        return r == null || r.getCodigo() == null ? "" : r.getCodigo();
    }
}