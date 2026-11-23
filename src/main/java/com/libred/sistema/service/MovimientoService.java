package com.libred.sistema.service;

import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.MovimientoVista.FilaDevolucion;
import com.libred.sistema.model.MovimientoVista.FilaMulta;
import com.libred.sistema.model.MovimientoVista.FilaPrestamo;
import com.libred.sistema.model.MovimientoVista.Pantalla;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Módulo MOVIMIENTOS del administrador (solo lectura): el historial de préstamos, devoluciones y multas de SU
 * librería, del más reciente al más antiguo.
 */
@Service
public class MovimientoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;

    public MovimientoService(PrestamoRepository prestamoRepository,
                             DevolucionRepository devolucionRepository,
                             MultaRepository multaRepository) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
    }

    @Transactional(readOnly = true)
    public Pantalla armarPantalla(Integer libreriaId) {
        LocalDate hoy = LocalDate.now();

        List<FilaPrestamo> prestamos = new ArrayList<>();
        List<Prestamo> listaPrestamos = new ArrayList<>(prestamoRepository.findByLibreriaId(libreriaId));
        listaPrestamos.sort(Comparator.comparing(Prestamo::getFechaPrestamo,
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (Prestamo p : listaPrestamos) {
            prestamos.add(aFilaPrestamo(p, hoy));
        }

        List<FilaDevolucion> devoluciones = new ArrayList<>();
        List<Devolucion> listaDevoluciones = new ArrayList<>(devolucionRepository.findByLibreriaId(libreriaId));
        listaDevoluciones.sort(Comparator.comparing(Devolucion::getFechaDevolucion,
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (Devolucion d : listaDevoluciones) {
            devoluciones.add(aFilaDevolucion(d));
        }

        List<Multa> listaMultas = new ArrayList<>();
        listaMultas.addAll(multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PENDIENTE"));
        listaMultas.addAll(multaRepository.findByLibreriaIdAndEstadoPago(libreriaId.longValue(), "PAGADO"));
        listaMultas.sort(Comparator.comparing(Multa::getFechaEmision,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<FilaMulta> multas = new ArrayList<>();
        for (Multa m : listaMultas) {
            multas.add(aFilaMulta(m));
        }

        return new Pantalla(prestamos, devoluciones, multas);
    }

    private FilaPrestamo aFilaPrestamo(Prestamo p, LocalDate hoy) {
        String estado;
        String texto;
        if ("CANCELADO".equals(p.getEstadoPrestamo())) {
            estado = "CANCELADO";
            texto = "Cancelado";
        } else if ("POR_ENTREGAR".equals(p.getEstadoPrestamo())) {
            estado = "POR_ENTREGAR";
            texto = "Por entregar";
        } else if ("DEVUELTO".equals(p.getEstadoPrestamo())) {
            estado = "DEVUELTO";
            texto = "Devuelto";
        } else if (p.getFechaDevolucionLimite() != null && p.getFechaDevolucionLimite().isBefore(hoy)) {
            estado = "VENCIDO";
            texto = "Vencido";
        } else {
            estado = "ACTIVO";
            texto = "Activo";
        }
        String origen = "LECTOR".equals(p.getOrigen()) ? "Lector" : "Mostrador";
        return new FilaPrestamo(
                p.getId(),
                texto(p.getFechaPrestamo(), FECHA_HORA), dia(p.getFechaPrestamo()),
                nombre(p.getLector()), codigo(p.getLector()),
                titulo(p.getRecurso()), codigo(p.getRecurso()),
                p.getFechaDevolucionLimite() == null ? "—" : p.getFechaDevolucionLimite().format(FECHA),
                texto(p.getFechaDevolucionReal(), FECHA),
                dinero(p.getMontoServicio()), origen, estado, texto);
    }

    private FilaDevolucion aFilaDevolucion(Devolucion d) {
        Prestamo p = d.getPrestamo();
        return new FilaDevolucion(
                d.getId(),
                texto(d.getFechaDevolucion(), FECHA_HORA), dia(d.getFechaDevolucion()),
                p == null ? "—" : nombre(p.getLector()), p == null ? "" : codigo(p.getLector()),
                p == null ? "—" : titulo(p.getRecurso()), p == null ? "" : codigo(p.getRecurso()),
                d.getDiasRetraso() == null ? 0 : d.getDiasRetraso(),
                dinero(d.getMontoMora()), dinero(d.getMontoMulta()), dinero(d.getMontoTotal()));
    }

    private FilaMulta aFilaMulta(Multa m) {
        boolean pagada = "PAGADO".equals(m.getEstadoPago());
        Prestamo p = m.getPrestamo();
        return new FilaMulta(
                m.getId(),
                texto(m.getFechaEmision(), FECHA_HORA), dia(m.getFechaEmision()),
                nombre(m.getLector()), codigo(m.getLector()),
                p == null ? "—" : titulo(p.getRecurso()), p == null ? "" : codigo(p.getRecurso()),
                m.getMotivo(), dinero(m.getMonto()),
                pagada ? "PAGADA" : "PENDIENTE", pagada ? "Pagada" : "Pendiente",
                texto(m.getFechaPago(), FECHA));
    }

    private static String texto(LocalDateTime fecha, DateTimeFormatter formato) {
        return fecha == null ? "—" : fecha.format(formato);
    }

    private static String dia(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.format(DIA);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor.setScale(2, java.math.RoundingMode.HALF_UP);
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