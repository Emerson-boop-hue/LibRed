package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TarifaCategoria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.RecursoRepository;
import com.libred.sistema.repository.TarifaCategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;
/**
 * Servicio para gestionar la política de préstamos de la librería, incluyendo los ajustes de días máximos,
 * préstamos activos por lector y tarifas por categoría de recurso.
 * PoliticaPrestamoService
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
    private final RecursoRepository recursoRepository;

    public PoliticaPrestamoService(LibreriaRepository libreriaRepository,
                                   TarifaCategoriaRepository tarifaRepository,
                                   RecursoRepository recursoRepository) {
        this.libreriaRepository = libreriaRepository;
        this.tarifaRepository = tarifaRepository;
        this.recursoRepository = recursoRepository;
    }

/**
 * Fila de la tabla de tarifas para la pantalla de Ajustes.
 * FilaTarifa
 */
    public static class FilaTarifa {
        private final String clave;     
        private final String valor;      
        private final String etiqueta;
        private final String icono;
        private final BigDecimal tarifa;
        private final boolean activa;
        private final long articulos;   

        public FilaTarifa(CategoriaRecurso c, BigDecimal tarifa, boolean activa, long articulos) {
            this.activa = activa;
            this.articulos = articulos;
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
        public boolean isActiva() { return activa; }
        public long getArticulos() { return articulos; }
    }

    public int obtenerMaxDias(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId).map(Libreria::getMaxDiasPrestamo).orElse(7);
    }

    public Integer obtenerMaxActivos(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId).map(Libreria::getMaxPrestamosActivos).orElse(null);
    }

    private Set<String> valoresInactivos(Integer libreriaId) {
        Set<String> inactivos = new HashSet<>();
        for (TarifaCategoria t : tarifaRepository.findByLibreriaId(libreriaId)) {
            if (!t.isActiva()) {
                inactivos.add(t.getCategoria());
            }
        }
        return inactivos;
    }

    public List<CategoriaRecurso> categoriasActivas(Integer libreriaId) {
        Set<String> inactivos = valoresInactivos(libreriaId);
        List<CategoriaRecurso> activas = new ArrayList<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            if (!inactivos.contains(c.getValor())) {
                activas.add(c);
            }
        }
        return activas;
    }

    public boolean categoriaActiva(Integer libreriaId, String tipoRecurso) {
        return !valoresInactivos(libreriaId).contains(CategoriaRecurso.desdeValor(tipoRecurso).getValor());
    }

    public BigDecimal tarifaDe(Integer libreriaId, String tipoRecurso) {
        String valor = CategoriaRecurso.desdeValor(tipoRecurso).getValor();
        return tarifaRepository.findByLibreriaIdAndCategoria(libreriaId, valor)
                .map(TarifaCategoria::getTarifaDia)
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

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

    public List<FilaTarifa> filasDeTarifas(Integer libreriaId) {
        Map<String, BigDecimal> mapa = tarifasPorValor(libreriaId);
        Set<String> inactivos = valoresInactivos(libreriaId);
        List<FilaTarifa> filas = new ArrayList<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            filas.add(new FilaTarifa(c, mapa.get(c.getValor()), !inactivos.contains(c.getValor()),
                    recursoRepository.countByLibreriaIdAndTipoRecurso(libreriaId, c.getValor())));
        }
        return filas;
    }

    @Transactional
    public void guardar(Integer libreriaId, Map<String, String> datos) {
        Libreria libreria = libreriaRepository.findById(libreriaId)
                .orElseThrow(() -> new AjustesException(null, "No se encontró tu librería."));

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

        Map<CategoriaRecurso, Boolean> activas = new LinkedHashMap<>();
        Map<CategoriaRecurso, BigDecimal> nuevas = new LinkedHashMap<>();
        int totalActivas = 0;
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            boolean activa = !"false".equalsIgnoreCase(datos.get("a_" + c.name()));
            activas.put(c, activa);

            if (!activa) {
                continue;
            }
            totalActivas++;

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
        if (totalActivas == 0) {
            throw new AjustesException(null, "Tu librería debe tener al menos una categoría activa.");
        }

        libreria.setMaxDiasPrestamo(maxDias);
        libreria.setMaxPrestamosActivos(maxActivos);
        libreriaRepository.save(libreria);

        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            String valor = c.getValor();
            TarifaCategoria fila = tarifaRepository.findByLibreriaIdAndCategoria(libreriaId, valor)
                    .orElseGet(() -> new TarifaCategoria(libreriaId, valor, BigDecimal.ZERO));
            fila.setActiva(activas.get(c));
            if (nuevas.containsKey(c)) {          // la tarifa de una categoría desactivada se conserva
                fila.setTarifaDia(nuevas.get(c));
            }
            tarifaRepository.save(fila);
        }
    }
}