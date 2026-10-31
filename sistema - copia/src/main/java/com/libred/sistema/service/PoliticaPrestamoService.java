package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TarifaCategoria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.TarifaCategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * POLÍTICA DE PRÉSTAMOS de cada librería: máximo de días y tarifa de servicio por día según la categoría.
 * La usan el administrador (Ajustes y Préstamos) y el lector (Préstamos), por eso vive en un servicio aparte.
 */
@Service
public class PoliticaPrestamoService {

    public static final int DIAS_MINIMO = 1;
    public static final int DIAS_MAXIMO_PERMITIDO = 60;
    public static final int ACTIVOS_MAXIMO_PERMITIDO = 50;
    public static final BigDecimal TARIFA_MAXIMA = new BigDecimal("50.00");

    private static final Pattern FORMATO_TARIFA = Pattern.compile("^\\d{1,2}([.,]\\d{1,2})?$");

    private final LibreriaRepository libreriaRepository;
    private final TarifaCategoriaRepository tarifaRepository;

    public PoliticaPrestamoService(LibreriaRepository libreriaRepository, TarifaCategoriaRepository tarifaRepository) {
        this.libreriaRepository = libreriaRepository;
        this.tarifaRepository = tarifaRepository;
    }

    /** Fila de la tabla de tarifas de Ajustes (una por categoría). */
    public static class FilaTarifa {
        private final String clave;      // nombre del enum (sirve de llave en el JSON)
        private final String valor;      // valor guardado en recurso.tipo_recurso
        private final String etiqueta;
        private final String icono;
        private final BigDecimal tarifa;

        public FilaTarifa(CategoriaRecurso c, BigDecimal tarifa) {
            this.clave = c.name();
            this.valor = c.getValor();
            this.etiqueta = c.getEtiqueta();
            this.icono = c.getIcono();
            this.tarifa = tarifa;
        }

        public String getClave() { return clave; }
        public String getValor() { return valor; }
        public String getEtiqueta() { return etiqueta; }
        public String getIcono() { return icono; }
        public BigDecimal getTarifa() { return tarifa; }
    }

    /** Máximo de días de préstamo de la librería (7 si la librería no existe). */
    public int obtenerMaxDias(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId).map(Libreria::getMaxDiasPrestamo).orElse(7);
    }

    /** Máximo de préstamos activos por lector en la librería; null = sin límite. */
    public Integer obtenerMaxActivos(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId).map(Libreria::getMaxPrestamosActivos).orElse(null);
    }

    /** Tarifa por día de una categoría (valor de recurso.tipo_recurso). S/ 0.00 si no se configuró. */
    public BigDecimal tarifaDe(Integer libreriaId, String tipoRecurso) {
        String valor = CategoriaRecurso.desdeValor(tipoRecurso).getValor();
        return tarifaRepository.findByLibreriaIdAndCategoria(libreriaId, valor)
                .map(TarifaCategoria::getTarifaDia)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Todas las tarifas de la librería: valor de categoría -> tarifa (las no configuradas valen 0.00). */
    public Map<String, BigDecimal> tarifasPorValor(Integer libreriaId) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            mapa.put(c.getValor(), BigDecimal.ZERO.setScale(2));
        }
        for (TarifaCategoria t : tarifaRepository.findByLibreriaId(libreriaId)) {
            if (mapa.containsKey(t.getCategoria())) {
                mapa.put(t.getCategoria(), t.getTarifaDia().setScale(2, RoundingMode.HALF_UP));
            }
        }
        return mapa;
    }

    /** Filas para la pantalla de Ajustes. */
    public List<FilaTarifa> filasDeTarifas(Integer libreriaId) {
        Map<String, BigDecimal> mapa = tarifasPorValor(libreriaId);
        List<FilaTarifa> filas = new ArrayList<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            filas.add(new FilaTarifa(c, mapa.get(c.getValor())));
        }
        return filas;
    }

    /**
     * Guarda la política. "datos" trae "maxDias" y una clave "t_NOMBREENUM" por categoría.
     * Se valida todo antes de guardar nada.
     */
    @Transactional
    public void guardar(Integer libreriaId, Map<String, String> datos) {
        Libreria libreria = libreriaRepository.findById(libreriaId)
                .orElseThrow(() -> new AjustesException(null, "No se encontró tu librería."));

        // --- máximo de días ---
        String textoDias = datos.get("maxDias") == null ? "" : datos.get("maxDias").trim();
        int maxDias;
        try {
            maxDias = Integer.parseInt(textoDias);
        } catch (NumberFormatException e) {
            throw new AjustesException("maxDias", "Ingresa el máximo de días como un número entero.");
        }
        if (maxDias < DIAS_MINIMO || maxDias > DIAS_MAXIMO_PERMITIDO) {
            throw new AjustesException("maxDias",
                    "El máximo de días debe estar entre " + DIAS_MINIMO + " y " + DIAS_MAXIMO_PERMITIDO + ".");
        }

        // --- límite de préstamos activos por lector (opcional) ---
        Integer maxActivos = null;
        if ("true".equalsIgnoreCase(datos.get("limitarActivos"))) {
            String textoActivos = datos.get("maxActivos") == null ? "" : datos.get("maxActivos").trim();
            try {
                maxActivos = Integer.parseInt(textoActivos);
            } catch (NumberFormatException e) {
                throw new AjustesException("maxActivos", "Ingresa cuántos préstamos activos puede tener cada lector.");
            }
            if (maxActivos < 1 || maxActivos > ACTIVOS_MAXIMO_PERMITIDO) {
                throw new AjustesException("maxActivos",
                        "El límite de préstamos activos debe estar entre 1 y " + ACTIVOS_MAXIMO_PERMITIDO + ".");
            }
        }

        // --- tarifas ---
        Map<CategoriaRecurso, BigDecimal> nuevas = new LinkedHashMap<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            String clave = "t_" + c.name();
            String texto = datos.get(clave) == null ? "" : datos.get(clave).trim();
            if (texto.isEmpty()) {
                texto = "0";
            }
            if (!FORMATO_TARIFA.matcher(texto).matches()) {
                throw new AjustesException(clave,
                        "La tarifa de " + c.getEtiqueta() + " debe ser un monto válido (ej. 1.50).");
            }
            BigDecimal tarifa = new BigDecimal(texto.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
            if (tarifa.compareTo(TARIFA_MAXIMA) > 0) {
                throw new AjustesException(clave,
                        "La tarifa de " + c.getEtiqueta() + " no puede superar S/ " + TARIFA_MAXIMA + " por día.");
            }
            nuevas.put(c, tarifa);
        }

        // --- guardar ---
        libreria.setMaxDiasPrestamo(maxDias);
        libreria.setMaxPrestamosActivos(maxActivos);
        libreriaRepository.save(libreria);

        for (Map.Entry<CategoriaRecurso, BigDecimal> e : nuevas.entrySet()) {
            String valor = e.getKey().getValor();
            TarifaCategoria fila = tarifaRepository.findByLibreriaIdAndCategoria(libreriaId, valor)
                    .orElseGet(() -> new TarifaCategoria(libreriaId, valor, BigDecimal.ZERO));
            fila.setTarifaDia(e.getValue());
            tarifaRepository.save(fila);
        }
    }
}