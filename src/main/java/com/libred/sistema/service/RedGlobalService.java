package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.RedGlobalVista;
import com.libred.sistema.model.RedGlobalVista.CategoriaChip;
import com.libred.sistema.model.RedGlobalVista.TarjetaLibreria;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Red Global: directorio de todas las librerías de LibRed para el lector.
 * Todo sale de la base de datos (artículos, categorías activas, política de préstamos, contacto).
 * No se muestran datos privados: ni RUC, ni código de administrador, ni contraseñas, ni datos de otros lectores.
 */
@Service
public class RedGlobalService {

    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.of("es", "PE"));

    private final LibreriaRepository libreriaRepository;
    private final LectorRepository lectorRepository;
    private final RecursoRepository recursoRepository;
    private final PoliticaPrestamoService politicaService;

    public RedGlobalService(LibreriaRepository libreriaRepository, LectorRepository lectorRepository,
                            RecursoRepository recursoRepository, PoliticaPrestamoService politicaService) {
        this.libreriaRepository = libreriaRepository;
        this.lectorRepository = lectorRepository;
        this.recursoRepository = recursoRepository;
        this.politicaService = politicaService;
    }

    @Transactional(readOnly = true)
    public Optional<RedGlobalVista> armar(Integer lectorId) {
        Optional<Lector> yo = lectorRepository.findById(lectorId);
        if (yo.isEmpty()) {
            return Optional.empty();
        }
        Integer sede = yo.get().getLibreriaId();

        // Librerías donde esta misma persona (mismo código) ya tiene registro
        Set<Integer> miembroEn = lectorRepository.findAllByCodigoLector(yo.get().getCodigoLector()).stream()
                .map(Lector::getLibreriaId)
                .collect(Collectors.toSet());

        List<TarjetaLibreria> tarjetas = new ArrayList<>();
        Map<String, CategoriaChip> categoriasRed = new LinkedHashMap<>();
        long totalArticulos = 0;
        int indice = 0;

        for (Libreria lib : libreriaRepository.findAll(Sort.by("nombreComercial"))) {
            Integer id = lib.getId();

            // Categorías activas y artículos (solo de categorías activas, igual que el catálogo)
            List<CategoriaRecurso> activas = politicaService.categoriasActivas(id);
            Set<String> valoresActivos = activas.stream().map(CategoriaRecurso::getValor).collect(Collectors.toSet());
            List<Recurso> recursos = recursoRepository.findByLibreriaId(id).stream()
                    .filter(r -> valoresActivos.contains(CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor()))
                    .toList();

            // Solo se listan las categorías con al menos un artículo
            Set<String> conArticulos = recursos.stream()
                    .map(r -> CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor())
                    .collect(Collectors.toSet());
            List<CategoriaChip> chips = activas.stream()
                    .filter(c -> conArticulos.contains(c.getValor()))
                    .map(c -> new CategoriaChip(c.getValor(), c.getEtiqueta(), c.getIcono()))
                    .toList();
            chips.forEach(c -> categoriasRed.putIfAbsent(c.getValor(), c));

            // Tarifa más baja entre las categorías que ofrece (para "desde S/ X por día")
            Map<String, BigDecimal> tarifas = politicaService.tarifasPorValor(id);
            BigDecimal desde = chips.stream()
                    .map(c -> tarifas.getOrDefault(c.getValor(), BigDecimal.ZERO))
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);

            totalArticulos += recursos.size();

            tarjetas.add(new TarjetaLibreria(
                    id,
                    lib.getNombreComercial(),
                    iniciales(lib.getNombreComercial()),
                    indice++ % 6,
                    lib.getDireccion() == null ? "" : lib.getDireccion(),
                    lib.getTelefonoContacto(),
                    lib.getCorreoContacto(),
                    lib.getHorarioAtencion(),
                    lib.getDescripcion(),
                    mesAnio(lib.getFechaRegistro()),
                    recursos.size(),
                    politicaService.obtenerMaxDias(id),
                    politicaService.obtenerMaxActivos(id),
                    desde.setScale(2, java.math.RoundingMode.HALF_UP),
                    chips,
                    id.equals(sede),
                    miembroEn.contains(id)));
        }

        // La librería donde estoy va primero; el resto por nombre
        tarjetas.sort(Comparator.comparing((TarjetaLibreria t) -> !t.isSedeActual())
                .thenComparing(t -> t.getNombre().toLowerCase()));

        // Todas las categorías de la red, en el orden habitual del sistema
        List<CategoriaChip> categorias = new ArrayList<>();
        for (CategoriaRecurso c : CategoriaRecurso.values()) {
            if (categoriasRed.containsKey(c.getValor())) {
                categorias.add(categoriasRed.get(c.getValor()));
            }
        }

        return Optional.of(new RedGlobalVista(tarjetas, categorias, totalArticulos));
    }

    // "Librería Central SJL" -> "LC" (primeras letras de las dos primeras palabras significativas)
    private String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "L";
        }
        String[] palabras = nombre.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (!p.isEmpty() && Character.isLetter(p.charAt(0))) {
                sb.append(Character.toUpperCase(p.charAt(0)));
            }
            if (sb.length() == 2) {
                break;
            }
        }
        return sb.length() == 0 ? "L" : sb.toString();
    }

    private String mesAnio(LocalDateTime fecha) {
        return fecha == null ? "" : fecha.format(MES_ANIO);
    }
}