package com.libred.sistema.service;

import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class RecursoService {

    private final RecursoRepository recursoRepository;

    // Inyección por constructor (La buena práctica que te sugiere VS Code)
    public RecursoService(RecursoRepository recursoRepository) {
        this.recursoRepository = recursoRepository;
    }

    // Verifica que el recurso exista y pertenezca a la librería indicada
    private Recurso recursoDeLibreria(Integer libreriaId, Integer recursoId) throws Exception {
        Recurso existente = recursoRepository.findById(recursoId).orElse(null);
        if (existente == null || existente.getLibreria() == null
                || !libreriaId.equals(existente.getLibreria().getId())) {
            throw new Exception("El artículo no existe o no pertenece a tu librería.");
        }
        return existente;
    }

    // 1. Listar el catálogo de una librería específica
    public List<Recurso> listarPorLibreria(Integer libreriaId) {
        return recursoRepository.findByLibreriaId(libreriaId);
    }

    // 2. Filtrar recursos por texto (keyword) y categoría para la búsqueda en vivo
    public List<Recurso> filtrarRecursos(Integer libreriaId, String keyword, String categoria) {
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (categoria != null && categoria.trim().isEmpty()) {
            categoria = null;
        }
        return recursoRepository.filtrarRecursos(libreriaId, keyword, categoria);
    }

    // 3. Generar el código secuencial automático según la categoría seleccionada
    public String generarCodigoSugerido(Integer libreriaId, String tipoRecurso) {
        String prefijo = "OTR";
        if (tipoRecurso != null) {
            switch (tipoRecurso) {
                case "LIBROS": prefijo = "LIB"; break;
                case "NOVELAS LIGERAS": prefijo = "NOV"; break;
                case "CUENTOS": prefijo = "CUE"; break;
                case "COMICS": prefijo = "COM"; break;
                case "MANGAS": prefijo = "MAN"; break;
                case "REVISTAS": prefijo = "REV"; break;
                default: prefijo = "OTR"; break;
            }
        }

        long correlativo = recursoRepository.countByLibreriaIdAndTipoRecurso(libreriaId, tipoRecurso) + 1;
        String codigoSugerido;

        // Bucle de seguridad: el código no debe repetirse DENTRO de la misma librería
        do {
            codigoSugerido = String.format("%s-%03d", prefijo, correlativo);
            correlativo++;
        } while (recursoRepository.existsByLibreriaIdAndCodigo(libreriaId, codigoSugerido));

        return codigoSugerido;
    }

    // 4. Guardar o actualizar un artículo con validación de título único y cálculo de stock/estado
    public void guardar(Integer libreriaId, Recurso recurso) throws Exception {
        // Si se está modificando un artículo existente, debe ser de esta librería
        if (recurso.getId() != null) {
            recursoDeLibreria(libreriaId, recurso.getId());
        }

        // Validar unicidad del título solo si es un nuevo registro (o si cambió de título)
        if (recurso.getId() == null) {
            if (recursoRepository.existsByLibreriaIdAndTituloIgnoreCase(libreriaId, recurso.getTitulo())) {
                throw new Exception("Ya existe un artículo registrado con el título: " + recurso.getTitulo());
            }
            // Autogenerar código secuencial al crear nuevo recurso
            recurso.setCodigo(generarCodigoSugerido(libreriaId, recurso.getTipoRecurso()));
        }

        // REPLICACIÓN AUTOMÁTICA: El stock ingresado se asigna tanto al total como al disponible para artículos nuevos
        if (recurso.getId() == null || recurso.getStockDisponible() == null) {
            recurso.setStockDisponible(recurso.getStockTotal());
        }

        // Cálculo automático de estado según el stock disponible
        if (recurso.getStockDisponible() <= 0) {
            recurso.setEstado("AGOTADO");
        } else if (recurso.getStockDisponible() <= 2) {
            recurso.setEstado("CRITICO");
        } else {
            recurso.setEstado("NORMAL");
        }
        
        recursoRepository.save(recurso);
    }

    // 5. Buscar un artículo específico (útil para el botón Modificar)
    public Optional<Recurso> buscarPorId(Integer id) {
        return recursoRepository.findById(id);
    }

    // 6. Eliminar un artículo (útil para el botón Papelera) - solo si es de la librería indicada
    public void eliminar(Integer libreriaId, Integer id) throws Exception {
        recursoDeLibreria(libreriaId, id);
        recursoRepository.deleteById(id);
    }
}