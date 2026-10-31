package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LectorService {

    private final LectorRepository lectorRepository;
    private final MultaRepository multaRepository;

    public LectorService(LectorRepository lectorRepository, MultaRepository multaRepository) {
        this.lectorRepository = lectorRepository;
        this.multaRepository = multaRepository;
    }

    public List<Lector> listarTodos(String keyword) {
        List<Lector> lectores;
        if (keyword != null && !keyword.isBlank()) {
            lectores = lectorRepository.search(keyword.trim());
        } else {
            lectores = lectorRepository.findAll();
        }
        sincronizarEstados(lectores);
        return lectores;
    }

    // Método filtrado por librería actual con verificación dinámica de sanciones
    public List<Lector> listarPorLibreria(Integer libreriaId, String keyword) {
        List<Lector> lectores;
        if (keyword != null && !keyword.isBlank()) {
            lectores = lectorRepository.searchByLibreria(libreriaId, keyword.trim());
        } else {
            lectores = lectorRepository.findByLibreriaId(libreriaId);
        }
        sincronizarEstados(lectores);
        return lectores;
    }

    // Método privado para asegurar que el estado en memoria refleje las multas pendientes reales
    private void sincronizarEstados(List<Lector> lectores) {
        for (Lector lector : lectores) {
            boolean tienePendientes = multaRepository.existsByLectorIdAndEstadoPago(lector.getId(), "PENDIENTE");
            String estadoReal = tienePendientes ? "SANCIONADO" : "HABILITADO";
            
            // Si difiere del estado actual en la BD, lo actualizamos al vuelo
            if (!estadoReal.equals(lector.getEstadoCuenta())) {
                lector.setEstadoCuenta(estadoReal);
                lectorRepository.save(lector);
            }
        }
    }

    // Guarda (o modifica) un lector SIEMPRE dentro de la librería indicada
    public void guardar(Integer libreriaId, Lector lector) {
        // Si se está modificando un lector existente, debe ser de esta librería
        if (lector.getId() != null) {
            Lector existente = lectorRepository.findById(lector.getId()).orElse(null);
            if (existente == null || !libreriaId.equals(existente.getLibreriaId())) {
                throw new IllegalArgumentException("El lector no existe o no pertenece a tu librería.");
            }
            // El código nunca se cambia al modificar (así no se puede suplantar el código de otra persona)
            lector.setCodigoLector(existente.getCodigoLector());
        }

        lector.setLibreriaId(libreriaId);

        if (lector.getId() == null || lector.getCodigoLector() == null || lector.getCodigoLector().isEmpty()) {
            lector.setCodigoLector(generarSiguienteCodigo());
        }
        
        if (lector.getPassword() == null || lector.getPassword().isEmpty()) {
            lector.setPassword(lector.getTelefono() != null ? lector.getTelefono().replaceAll("\\s+", "") : "123456");
        }

        lectorRepository.save(lector);
    }

    public Lector obtenerPorId(Integer id) {
        Lector lector = lectorRepository.findById(id).orElse(null);
        if (lector != null) {
            boolean tienePendientes = multaRepository.existsByLectorIdAndEstadoPago(lector.getId(), "PENDIENTE");
            lector.setEstadoCuenta(tienePendientes ? "SANCIONADO" : "HABILITADO");
        }
        return lector;
    }

    // Elimina un lector solo si pertenece a la librería indicada
    public void eliminar(Integer libreriaId, Integer id) {
        Lector existente = lectorRepository.findById(id).orElse(null);
        if (existente == null || !libreriaId.equals(existente.getLibreriaId())) {
            throw new IllegalArgumentException("El lector no existe o no pertenece a tu librería.");
        }
        lectorRepository.deleteById(id);
    }

    // El código identifica a la PERSONA en toda la red (puede aparecer en varias librerías, una fila por librería).
    // Por eso un código nuevo continúa desde el último existente, sin importar la librería,
    // y se verifica que no lo tenga ya otra persona en ninguna librería.
    public String generarSiguienteCodigo() {
        int numero = 1000;
        String ultimoCodigo = lectorRepository.findLastCodigo();
        if (ultimoCodigo != null && ultimoCodigo.contains("-")) {
            try {
                numero = Integer.parseInt(ultimoCodigo.split("-")[1]);
            } catch (NumberFormatException e) {
                numero = 1000;
            }
        }

        String codigo;
        do {
            numero++;
            codigo = "LEC-" + numero;
        } while (lectorRepository.existsByCodigoLector(codigo));

        return codigo;
    }
    
    public long contarTotalLectores() {
        return lectorRepository.count();
    }

    public long contarLectoresSancionados() {
        return lectorRepository.countByEstadoCuenta("SANCIONADO");
    }

    public long contarTotalLectoresPorLibreria(Integer libreriaId) {
        return lectorRepository.countByLibreriaId(libreriaId);
    }

    public long contarLectoresSancionadosPorLibreria(Integer libreriaId) {
        // Aseguramos sincronización antes de contar
        sincronizarEstados(lectorRepository.findByLibreriaId(libreriaId));
        return lectorRepository.countByLibreriaIdAndEstadoCuenta(libreriaId, "SANCIONADO");
    }
}