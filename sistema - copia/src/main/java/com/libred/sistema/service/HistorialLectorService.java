package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.HistorialLectorVista.FilaMulta;
import com.libred.sistema.model.HistorialLectorVista.FilaPrestamo;
import com.libred.sistema.model.HistorialLectorVista.Pantalla;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Multa;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.MultaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Módulo HISTORIAL del lector (solo lectura): sus préstamos, devoluciones y multas en la librería
 * a la que entró. Cada librería tiene su propio registro del lector, así que el historial nunca mezcla librerías.
 */
@Service
public class HistorialLectorService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final MultaRepository multaRepository;

    public HistorialLectorService(LectorRepository lectorRepository,
                                  LibreriaRepository libreriaRepository,
                                  PrestamoRepository prestamoRepository,
                                  DevolucionRepository devolucionRepository,
                                  MultaRepository multaRepository) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.multaRepository = multaRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Pantalla> armarPantalla(Integer lectorId) {
        Optional<Lector> opt = lectorRepository.findById(lectorId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Lector lector = opt.get();
        LocalDate hoy = LocalDate.now();
        String sede = libreriaRepository.findById(lector.getLibreriaId())
                .map(Libreria::getNombreComercial).orElse("—");

        // Devolución de cada préstamo (si ya fue devuelto)
        Map<Integer, Devolucion> devoluciones = new HashMap<>();
        for (Devolucion d : devolucionRepository.buscarPorLector(lectorId)) {
            devoluciones.put(d.getPrestamo().getId(), d);
        }

        List<FilaPrestamo> filasPrestamo = new ArrayList<>();
        TreeSet<String> categorias = new TreeSet<>();
        int enCurso = 0;
        int devueltos = 0;
        for (Prestamo p : prestamoRepository.findByLectorIdOrderByFechaPrestamoDesc(lectorId)) {
            FilaPrestamo fila = aFilaPrestamo(p, devoluciones.get(p.getId()), hoy);
            filasPrestamo.add(fila);
            categorias.add(fila.getCategoria());
            if ("DEVUELTO".equals(fila.getEstado())) {
                devueltos++;
            } else {
                enCurso++;
            }
        }

        List<FilaMulta> filasMulta = new ArrayList<>();
        int multasPendientes = 0;
        BigDecimal montoPendiente = BigDecimal.ZERO;
        for (Multa m : multaRepository.buscarPorLector(lectorId)) {
            boolean pagada = "PAGADO".equalsIgnoreCase(m.getEstadoPago());
            if (!pagada) {
                multasPendientes++;
                montoPendiente = montoPendiente.add(m.getMonto());
            }
            Prestamo p = m.getPrestamo();
            filasMulta.add(new FilaMulta(m.getId(), p == null ? null : p.getId(),
                    p == null || p.getRecurso() == null ? "—" : p.getRecurso().getTitulo(),
                    m.getMotivo(), m.getMonto(),
                    m.getFechaEmision() == null ? "—" : m.getFechaEmision().format(FECHA),
                    pagada, m.getFechaPago() == null ? "—" : m.getFechaPago().format(FECHA)));
        }

        return Optional.of(new Pantalla(sede, filasPrestamo.size(), enCurso, devueltos, multasPendientes,
                montoPendiente, filasPrestamo, filasMulta, new ArrayList<>(categorias)));
    }

    private FilaPrestamo aFilaPrestamo(Prestamo p, Devolucion dev, LocalDate hoy) {
        Recurso r = p.getRecurso();
        LocalDate limite = p.getFechaDevolucionLimite();
        boolean devuelto = "DEVUELTO".equalsIgnoreCase(p.getEstadoPrestamo());

        String estado;
        String texto;
        String detalle;
        if (devuelto) {
            estado = "DEVUELTO";
            texto = "Devuelto";
            detalle = "";
        } else if (limite != null && limite.isBefore(hoy)) {
            long atraso = ChronoUnit.DAYS.between(limite, hoy);
            estado = "VENCIDO";
            texto = "Vencido";
            detalle = "Venció hace " + atraso + (atraso == 1 ? " día" : " días");
        } else {
            long faltan = limite == null ? 0 : ChronoUnit.DAYS.between(hoy, limite);
            estado = "ACTIVO";
            texto = "Activo";
            detalle = faltan == 0 ? "Vence hoy" : "Vence en " + faltan + (faltan == 1 ? " día" : " días");
        }

        BigDecimal cargos = BigDecimal.ZERO.setScale(2);
        String detalleCargos = devuelto ? "Sin cargos adicionales" : "—";
        if (dev != null) {
            cargos = dev.getMontoTotal() == null ? BigDecimal.ZERO : dev.getMontoTotal();
            cargos = cargos.setScale(2, RoundingMode.HALF_UP);
            int atraso = dev.getDiasRetraso() == null ? 0 : dev.getDiasRetraso();
            if (cargos.signum() > 0 || atraso > 0) {
                detalleCargos = atraso + (atraso == 1 ? " día" : " días") + " de atraso · Mora S/ "
                        + dev.getMontoMora().setScale(2, RoundingMode.HALF_UP).toPlainString() + " · Multa S/ "
                        + dev.getMontoMulta().setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
        }

        String devueltoEl = "—";
        if (p.getFechaDevolucionReal() != null) {
            devueltoEl = p.getFechaDevolucionReal().format(FECHA);
        }

        int dias = p.getDiasPrestamo() != null ? p.getDiasPrestamo()
                : (limite != null && p.getFechaPrestamo() != null
                    ? (int) ChronoUnit.DAYS.between(p.getFechaPrestamo().toLocalDate(), limite) : 0);

        return new FilaPrestamo(p.getId(), r.getTitulo(), r.getCodigo(),
                CategoriaRecurso.desdeValor(r.getTipoRecurso()).getEtiqueta(),
                p.getFechaPrestamo() == null ? "—" : p.getFechaPrestamo().format(FECHA),
                limite == null ? "—" : limite.format(FECHA), devueltoEl, dias, p.getMontoServicio(),
                cargos, detalleCargos, estado, texto, detalle);
    }
}