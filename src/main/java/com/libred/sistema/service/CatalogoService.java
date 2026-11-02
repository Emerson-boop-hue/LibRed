package com.libred.sistema.service;

import com.libred.sistema.model.ArticuloCatalogo;
import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.CatalogoRepository;
import com.libred.sistema.repository.LectorRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lógica del catálogo del lector.
 * El lector ve los artículos de la librería a la que entró al iniciar sesión (su registro de lector pertenece a esa librería).
 */
@Service
public class CatalogoService {

    private final CatalogoRepository catalogoRepository;
    private final LectorRepository lectorRepository;
    private final PoliticaPrestamoService politicaService;

    public CatalogoService(CatalogoRepository catalogoRepository, LectorRepository lectorRepository,
                           PoliticaPrestamoService politicaService) {
        this.catalogoRepository = catalogoRepository;
        this.lectorRepository = lectorRepository;
        this.politicaService = politicaService;
    }

    /** Librería a la que entró el lector (null si su registro ya no existe). */
    public Integer sedeDelLector(Integer lectorId) {
        return lectorRepository.findById(lectorId).map(Lector::getLibreriaId).orElse(null);
    }

    /** Busca artículos de la librería indicada aplicando los filtros (todos opcionales). */
    public List<ArticuloCatalogo> buscar(Integer libreriaId, String texto, String categoria,
                                         String autor, String disponibilidad) {
        if (libreriaId == null) {
            return List.of();
        }

        String filtroTexto = limpiar(texto);
        String filtroAutor = limpiar(autor);
        String filtroCategoria = CategoriaRecurso.esValida(categoria) ? categoria.trim().toUpperCase() : "";
        String filtroDisp = ("DISPONIBLE".equals(disponibilidad) || "AGOTADO".equals(disponibilidad))
                ? disponibilidad : "";

        // Los artículos de categorías que la librería desactivó no se muestran al lector
        var activas = politicaService.categoriasActivas(libreriaId).stream().map(CategoriaRecurso::getValor).toList();

        return catalogoRepository
                .buscarEnCatalogo(libreriaId, filtroTexto, filtroAutor, filtroCategoria, filtroDisp)
                .stream()
                .filter(r -> activas.contains(CategoriaRecurso.desdeValor(r.getTipoRecurso()).getValor()))
                .map(this::aTarjeta)
                .toList();
    }

    /** Cantidad total de artículos que tiene la librería (sin filtros). */
    public long contarArticulos(Integer libreriaId) {
        return libreriaId == null ? 0 : catalogoRepository.countByLibreriaId(libreriaId);
    }

    // Convierte un Recurso de la BD en los datos que necesita la tarjeta
    private ArticuloCatalogo aTarjeta(Recurso r) {
        CategoriaRecurso categoria = CategoriaRecurso.desdeValor(r.getTipoRecurso());
        int disponible = r.getStockDisponible() == null ? 0 : r.getStockDisponible();
        int total = r.getStockTotal() == null ? 0 : r.getStockTotal();

        String nivel;
        String texto;
        if (disponible <= 0) {
            nivel = "agotado";
            texto = "Agotado";
        } else if (disponible <= 2) {
            nivel = "poco";
            texto = "Últimas unidades";
        } else {
            nivel = "ok";
            texto = "Disponible";
        }

        String autor = (r.getAutorOCreador() == null || r.getAutorOCreador().isBlank())
                ? "Autor no registrado" : r.getAutorOCreador();

        return new ArticuloCatalogo(r.getId(), r.getTitulo(), autor, r.getCodigo(),
                categoria.getEtiqueta(), categoria.getIcono(), disponible, total, nivel, texto);
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}