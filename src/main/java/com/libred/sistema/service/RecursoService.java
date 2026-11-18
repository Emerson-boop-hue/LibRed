package com.libred.sistema.service;

import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Reglas de negocio del inventario. */
@Service
public class RecursoService {

    private final RecursoRepository recursoRepository;
    private final PoliticaPrestamoService politicaService;
    private final ImagenService imagenService;
    private final PrestamoRepository prestamoRepository;
    private final PortadaService portadaService;

    public RecursoService(RecursoRepository recursoRepository, PoliticaPrestamoService politicaService,
                          ImagenService imagenService, PrestamoRepository prestamoRepository,
                          PortadaService portadaService) {
        this.recursoRepository = recursoRepository;
        this.politicaService = politicaService;
        this.imagenService = imagenService;
        this.prestamoRepository = prestamoRepository;
        this.portadaService = portadaService;
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

    // Listar el catálogo de una librería específica
    public List<Recurso> listarPorLibreria(Integer libreriaId) {
        return recursoRepository.findByLibreriaId(libreriaId);
    }

    // Filtrar recursos por texto (keyword) y categoría para la búsqueda en vivo
    public List<Recurso> filtrarRecursos(Integer libreriaId, String keyword, String categoria) {
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        if (categoria != null && categoria.trim().isEmpty()) {
            categoria = null;
        }
        return recursoRepository.filtrarRecursos(libreriaId, keyword, categoria);
    }

    // Igual que el anterior y, además, filtra por estado del stock ("CRITICO" o "AGOTADO"; null = todos)
    public List<Recurso> filtrarRecursos(Integer libreriaId, String keyword, String categoria, String estado) {
        List<Recurso> lista = filtrarRecursos(libreriaId, keyword, categoria);
        if (estado == null || estado.isBlank()) {
            return lista;
        }
        return lista.stream().filter(r -> estado.equalsIgnoreCase(r.getEstado())).toList();
    }

    // Generar el código secuencial automático según la categoría seleccionada
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

    private static String limpiarTitulo(String titulo) {
        return titulo == null ? "" : titulo.trim().replaceAll("\\s+", " ");
    }

    /**
     * Mensaje de error si OTRO artículo de la librería ya tiene este título (sin importar mayúsculas, tildes ni espacios
     * sobrantes); null si está libre. Al modificar (id != null) solo se revisa si el título cambió, para no bloquear la
     * edición de artículos antiguos con título repetido.
     */
    public String tituloRepetido(Integer libreriaId, Integer id, String titulo) {
        String t = limpiarTitulo(titulo);
        if (t.isEmpty()) {
            return null;
        }
        if (id != null) {
            Recurso actual = recursoRepository.findById(id).orElse(null);
            if (actual != null && t.equalsIgnoreCase(limpiarTitulo(actual.getTitulo()))) {
                return null;
            }
        }
        boolean repetido = recursoRepository.findByLibreriaIdAndTituloIgnoreCase(libreriaId, t).stream()
                .anyMatch(r -> !r.getId().equals(id));
        return repetido ? "Ya existe un artículo con ese título en tu librería." : null;
    }

    // Guardar o actualizar un artículo (sin tocar la imagen)
    public void guardar(Integer libreriaId, Recurso recurso) throws Exception {
        guardar(libreriaId, recurso, null, false);
    }

    // Guardar o actualizar un artículo con validación de título único, stock, estado e imagen imagenArchivo: imagen
    // nueva ya recortada por el navegador (null = no se cambia) quitarImagen: true = borrar la imagen que tenía el
    // artículo
    public void guardar(Integer libreriaId, Recurso recurso, MultipartFile imagenArchivo, boolean quitarImagen) throws Exception {
        // Si se está modificando un artículo existente, debe ser de esta librería
        Recurso existente = null;
        if (recurso.getId() != null) {
            existente = recursoDeLibreria(libreriaId, recurso.getId());
        }

        // Un artículo NUEVO (o que cambia de categoría) debe usar una categoría activa (se elige en Ajustes).
        boolean cambiaCategoria = existente == null
                || !existente.getTipoRecurso().equalsIgnoreCase(recurso.getTipoRecurso());
        if (cambiaCategoria && !politicaService.categoriaActiva(libreriaId, recurso.getTipoRecurso())) {
            throw new Exception("La categoría seleccionada no está activa en tu librería. Actívala en Ajustes.");
        }

        // Título único dentro de la librería, al crear Y al modificar (en otra librería sí puede repetirse)
        String mensajeTitulo = tituloRepetido(libreriaId, recurso.getId(), recurso.getTitulo());
        if (mensajeTitulo != null) {
            throw new Exception(mensajeTitulo);
        }
        recurso.setTitulo(limpiarTitulo(recurso.getTitulo()));
        if (recurso.getId() == null) {
            // Autogenerar código secuencial al crear nuevo recurso
            recurso.setCodigo(generarCodigoSugerido(libreriaId, recurso.getTipoRecurso()));
        }

        // Stock: el disponible nunca puede ser mayor al total (la regla definitiva está aquí; el navegador solo avisa
        // antes)
        validarStock(existente, recurso);

        // Imagen (se procesa DESPUÉS de las validaciones, para no dejar archivos sueltos si algo falla)
        String nombreAnterior = (existente != null) ? existente.getImagen() : null;
        String nuevaImagen = null;
        String imagenFinal = nombreAnterior;
        if (imagenArchivo != null && !imagenArchivo.isEmpty()) {
            nuevaImagen = imagenService.guardarArticulo(imagenArchivo);
            imagenFinal = nuevaImagen;
        } else if (quitarImagen) {
            imagenFinal = null;
        }
        recurso.setImagen(imagenFinal);

        // Cálculo automático de estado según el stock disponible
        if (recurso.getStockDisponible() <= 0) {
            recurso.setEstado("AGOTADO");
        } else if (recurso.getStockDisponible() <= 2) {
            recurso.setEstado("CRITICO");
        } else {
            recurso.setEstado("NORMAL");
        }

        try {
            recursoRepository.save(recurso);
        } catch (RuntimeException e) {
            if (nuevaImagen != null) {
                imagenService.eliminar(nuevaImagen); // no dejar la imagen nueva huérfana
            }
            throw e;
        }

        // Si se reemplazó o quitó la imagen, se borra el archivo anterior
        if (nombreAnterior != null && !nombreAnterior.equals(imagenFinal)) {
            imagenService.eliminar(nombreAnterior);
        }
    }

    // Reglas del stock:
    private void validarStock(Recurso existente, Recurso recurso) throws Exception {
        Integer total = recurso.getStockTotal();
        if (total == null || total < 0) {
            throw new Exception("El stock total debe ser un número igual o mayor a 0.");
        }

        if (existente == null) {
            recurso.setStockDisponible(total);
            return;
        }

        Integer disponible = recurso.getStockDisponible();
        if (disponible == null) {
            // Sin dato: se mueve la misma cantidad que cambió el total
            int ajustado = existente.getStockDisponible() + (total - existente.getStockTotal());
            disponible = Math.max(0, Math.min(total, ajustado));
            recurso.setStockDisponible(disponible);
        }
        if (disponible < 0) {
            throw new Exception("El stock disponible no puede ser negativo.");
        }
        if (disponible > total) {
            throw new Exception("El stock disponible no puede ser mayor al stock total.");
        }

        long prestados = prestamoRepository.countByRecursoIdAndEstadoPrestamoIn(existente.getId(), List.of("ACTIVO", "VENCIDO", "POR_ENTREGAR"));
        if (total < prestados) {
            throw new Exception("No puedes dejar el stock total en " + total + ": hay " + prestados
                    + " unidades prestadas en este momento.");
        }
    }

    // Eliminar un artículo (útil para el botón Papelera) - solo si es de la librería indicada
    public void eliminar(Integer libreriaId, Integer id) throws Exception {
        Recurso existente = recursoDeLibreria(libreriaId, id);
        String imagen = existente.getImagen();
        recursoRepository.deleteById(id);
        imagenService.eliminar(imagen); // la imagen se borra solo si el artículo se eliminó de verdad
    }

    // Ids de los artículos de la librería que todavía no tienen imagen
    public List<Integer> idsSinPortada(Integer libreriaId) {
        return recursoRepository.findByLibreriaId(libreriaId).stream()
                .filter(r -> r.getImagen() == null || r.getImagen().isBlank())
                .map(Recurso::getId)
                .toList();
    }

    // Pone una portada automática SOLO si el artículo no tiene imagen (nunca pisa una imagen existente).
    public String completarPortada(Integer libreriaId, Integer id) throws Exception {
        Recurso recurso = recursoDeLibreria(libreriaId, id);
        if (recurso.getImagen() != null && !recurso.getImagen().isBlank()) {
            return null;
        }
        var portada = portadaService.buscar(recurso.getTitulo(), recurso.getAutorOCreador(), recurso.getTipoRecurso());
        if (portada.isEmpty()) {
            return null;
        }
        String nombre = imagenService.guardarBytes(portada.get().datos());
        recurso.setImagen(nombre);
        try {
            recursoRepository.save(recurso);
        } catch (RuntimeException e) {
            imagenService.eliminar(nombre);
            throw e;
        }
        return recurso.getImagenUrl();
    }
}