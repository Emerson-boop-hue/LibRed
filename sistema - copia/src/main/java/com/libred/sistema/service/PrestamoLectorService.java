package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.model.PrestamoLectorVista.ArticuloVista;
import com.libred.sistema.model.PrestamoLectorVista.CategoriaVista;
import com.libred.sistema.model.PrestamoLectorVista.FilaPrestamo;
import com.libred.sistema.model.PrestamoLectorVista.Pantalla;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Módulo PRÉSTAMOS del lector: arma la pantalla y registra los préstamos que el lector solicita él mismo.
 * El registro real (stock, plazo, tarifa) lo hace PrestamoService, el mismo que usa el administrador,
 * así las reglas son idénticas para ambos.
 */
@Service
public class PrestamoLectorService {

    private static final List<String> ESTADOS_EN_CURSO = List.of("ACTIVO", "VENCIDO");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;
    private final RecursoRepository recursoRepository;
    private final PrestamoRepository prestamoRepository;
    private final PrestamoService prestamoService;
    private final PoliticaPrestamoService politicaService;

    public PrestamoLectorService(LectorRepository lectorRepository,
                                 LibreriaRepository libreriaRepository,
                                 RecursoRepository recursoRepository,
                                 PrestamoRepository prestamoRepository,
                                 PrestamoService prestamoService,
                                 PoliticaPrestamoService politicaService) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
        this.recursoRepository = recursoRepository;
        this.prestamoRepository = prestamoRepository;
        this.prestamoService = prestamoService;
        this.politicaService = politicaService;
    }

    /** Datos de la pantalla Préstamos (vacío si el registro del lector ya no existe). */
    @Transactional(readOnly = true)
    public Optional<Pantalla> armarPantalla(Integer lectorId) {
        Optional<Lector> opt = lectorRepository.findById(lectorId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Lector lector = opt.get();
        Integer libreriaId = lector.getLibreriaId();
        LocalDate hoy = LocalDate.now();

        String sede = libreriaRepository.findById(libreriaId).map(Libreria::getNombreComercial).orElse("—");
        int maxDias = politicaService.obtenerMaxDias(libreriaId);
        Integer maxActivos = politicaService.obtenerMaxActivos(libreriaId); // null = sin límite
        Map<String, BigDecimal> tarifas = politicaService.tarifasPorValor(libreriaId);

        // Artículos con stock, ordenados por título
        List<Recurso> conStock = recursoRepository.findByLibreriaId(libreriaId).stream()
                .filter(r -> r.getStockDisponible() != null && r.getStockDisponible() > 0)
                .sorted(Comparator.comparing(r -> r.getTitulo().toLowerCase()))
                .toList();

        List<ArticuloVista> articulos = new ArrayList<>();
        for (Recurso r : conStock) {
            articulos.add(new ArticuloVista(r.getId(), r.getTitulo(), r.getCodigo(),
                    r.getAutorOCreador() == null || r.getAutorOCreador().isBlank() ? "Autor no registrado" : r.getAutorOCreador(),
                    CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor(), r.getStockDisponible()));
        }

        // Solo se ofrecen las categorías que tienen algo para prestar
        List<CategoriaVista> categorias = new ArrayList<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            long disponibles = articulos.stream().filter(a -> a.getCategoria().equals(c.getValor())).count();
            if (disponibles > 0) {
                categorias.add(new CategoriaVista(c.getValor(), c.getEtiqueta(), c.getIcono(),
                        tarifas.get(c.getValor()), disponibles));
            }
        }

        // Mis préstamos
        List<Prestamo> propios = prestamoRepository.findByLectorIdOrderByFechaPrestamoDesc(lectorId);
        List<FilaPrestamo> filas = new ArrayList<>();
        long activos = 0;
        long vencidos = 0;
        for (Prestamo p : propios) {
            boolean enCurso = ESTADOS_EN_CURSO.contains(p.getEstadoPrestamo());
            LocalDate limite = p.getFechaDevolucionLimite();
            boolean vencido = enCurso && limite != null && limite.isBefore(hoy);
            if (enCurso) {
                activos++;
            }
            if (vencido) {
                vencidos++;
            }
            if (filas.size() < 15) {
                filas.add(aFila(p, enCurso, vencido, hoy));
            }
        }

        String bloqueo = null;
        if ("SANCIONADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            bloqueo = "Tu cuenta está SANCIONADA en esta librería. Regulariza tus multas para volver a solicitar préstamos.";
        } else if (vencidos > 0) {
            bloqueo = "Tienes " + vencidos + (vencidos == 1 ? " préstamo vencido" : " préstamos vencidos")
                    + ". Devuélvelo en la librería para poder solicitar otro.";
        } else if (maxActivos != null && activos >= maxActivos) {
            bloqueo = "Alcanzaste el límite de " + maxActivos
                    + (maxActivos == 1 ? " préstamo activo" : " préstamos activos")
                    + " que permite esta librería. Devuelve alguno para solicitar otro.";
        } else if (articulos.isEmpty()) {
            bloqueo = "Por ahora no hay artículos con stock disponible en esta librería.";
        }

        return Optional.of(new Pantalla(
                lector.getNombres() + " " + lector.getApellidos(), lector.getCodigoLector(), sede,
                maxDias, maxActivos, activos, bloqueo, categorias, articulos, filas));
    }

    private FilaPrestamo aFila(Prestamo p, boolean enCurso, boolean vencido, LocalDate hoy) {
        String estado;
        String texto;
        String detalle;
        LocalDate limite = p.getFechaDevolucionLimite();

        if (!enCurso) {
            estado = "DEVUELTO";
            texto = "Devuelto";
            detalle = p.getFechaDevolucionReal() != null ? "Devuelto el " + p.getFechaDevolucionReal().format(FORMATO_FECHA) : "";
        } else if (vencido) {
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

        Recurso r = p.getRecurso();
        int dias = p.getDiasPrestamo() != null ? p.getDiasPrestamo()
                : (limite != null && p.getFechaPrestamo() != null
                    ? (int) ChronoUnit.DAYS.between(p.getFechaPrestamo().toLocalDate(), limite) : 0);

        return new FilaPrestamo(p.getId(), r.getTitulo(), r.getCodigo(),
                CategoriaRecurso.desdeValor(r.getTipoRecurso()).getEtiqueta(),
                p.getFechaPrestamo() == null ? "—" : p.getFechaPrestamo().format(FORMATO_FECHA),
                limite == null ? "—" : limite.format(FORMATO_FECHA),
                dias, p.getMontoServicio(), estado, texto, detalle);
    }

    /**
     * Registra el préstamo que el lector solicita. Lanza AjustesException (con el campo y el mensaje)
     * si algo no se cumple; la pantalla muestra ese mensaje.
     */
    @Transactional
    public Prestamo solicitar(Integer lectorId, Integer recursoId, Integer dias) {
        Lector lector = lectorRepository.findById(lectorId)
                .orElseThrow(() -> new AjustesException(null, "Tu registro de lector ya no existe. Vuelve a iniciar sesión."));
        Integer libreriaId = lector.getLibreriaId();

        if (recursoId == null) {
            throw new AjustesException("articulo", "Selecciona el artículo que deseas.");
        }
        if (dias == null) {
            throw new AjustesException("dias", "Selecciona por cuántos días lo necesitas.");
        }

        int maxDias = politicaService.obtenerMaxDias(libreriaId);
        if (dias < 1 || dias > maxDias) {
            throw new AjustesException("dias", "Esta librería presta artículos por un máximo de " + maxDias
                    + (maxDias == 1 ? " día." : " días."));
        }

        if ("SANCIONADO".equalsIgnoreCase(lector.getEstadoCuenta())) {
            throw new AjustesException(null, "Tu cuenta está SANCIONADA en esta librería. No puedes solicitar préstamos.");
        }
        if (prestamoRepository.countByLectorIdAndEstadoPrestamoAndFechaDevolucionLimiteBefore(
                lectorId, "ACTIVO", LocalDate.now()) > 0
                || prestamoRepository.countByLectorIdAndEstadoPrestamoIn(lectorId, List.of("VENCIDO")) > 0) {
            throw new AjustesException(null, "Tienes préstamos vencidos. Devuélvelos para poder solicitar otro.");
        }
        Integer maxActivos = politicaService.obtenerMaxActivos(libreriaId);
        if (maxActivos != null
                && prestamoRepository.countByLectorIdAndEstadoPrestamoIn(lectorId, ESTADOS_EN_CURSO) >= maxActivos) {
            throw new AjustesException(null, "Alcanzaste el límite de " + maxActivos
                    + (maxActivos == 1 ? " préstamo activo" : " préstamos activos") + " de esta librería.");
        }
        if (prestamoRepository.existsByLectorIdAndRecursoIdAndEstadoPrestamoIn(lectorId, recursoId, ESTADOS_EN_CURSO)) {
            throw new AjustesException("articulo", "Ya tienes este artículo en préstamo.");
        }

        Prestamo prestamo = new Prestamo();
        prestamo.setLector(lector);
        Recurso referencia = new Recurso();
        referencia.setId(recursoId);
        prestamo.setRecurso(referencia);
        prestamo.setFechaDevolucionLimite(LocalDate.now().plusDays(dias));
        prestamo.setOrigen("LECTOR");

        try {
            // Valida pertenencia a la librería, stock, plazo y calcula la tarifa
            return prestamoService.registrarPrestamo(libreriaId, prestamo);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AjustesException("articulo", e.getMessage());
        }
    }
}