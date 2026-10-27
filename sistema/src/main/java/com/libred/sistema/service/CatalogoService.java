package com.libred.sistema.service;

import com.libred.sistema.model.Recurso;
import com.libred.sistema.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;

@Service
public class CatalogoService {
    private final RecursoRepository recursoRepository;

    public CatalogoService(RecursoRepository recursoRepository) {
        this.recursoRepository = recursoRepository;
    }

    public List<Recurso> obtenerCatalogoCompleto() {
        return recursoRepository.findAll();
    }

    public List<Recurso> buscar(String texto, String tipo, String autor, String disponibilidad) {
        String q = normalizar(texto);
        String t = normalizar(tipo);
        String a = normalizar(autor);
        boolean soloDisponibles = "si".equalsIgnoreCase(disponibilidad);
        return recursoRepository.findAll().stream()
            .filter(r -> q.isBlank() || normalizar(r.getTitulo()).contains(q) || normalizar(r.getIsbnOCodigo()).contains(q))
            .filter(r -> t.isBlank() || normalizar(r.getTipoRecurso()).equals(t))
            .filter(r -> a.isBlank() || normalizar(r.getAutorOCreador()).contains(a))
            .filter(r -> !soloDisponibles || (r.getStockDisponible() != null && r.getStockDisponible() > 0))
            .toList();
    }

    public Recurso obtenerPorId(Integer id) {
        return recursoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("El recurso seleccionado no existe."));
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
