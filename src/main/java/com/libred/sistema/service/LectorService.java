package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.MultaRepository;
import org.springframework.stereotype.Service;

import com.libred.sistema.util.TelefonoUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.libred.sistema.util.PasswordUtil;

/** Reglas de negocio del módulo Lectores. */
@Service
public class LectorService {

    private final LectorRepository lectorRepository;
    private final MultaRepository multaRepository;

    public LectorService(LectorRepository lectorRepository, MultaRepository multaRepository) {
        this.lectorRepository = lectorRepository;
        this.multaRepository = multaRepository;
    }

    // Método filtrado por librería actual con verificación dinámica de bloqueos (sin más filtros)
    public List<Lector> listarPorLibreria(Integer libreriaId, String keyword) {
        return listarPorLibreria(libreriaId, keyword, null, null, null);
    }

    // Lista los lectores de la librería con los filtros del módulo (todos opcionales y combinables):
    public List<Lector> listarPorLibreria(Integer libreriaId, String keyword, String estado, LocalDate desde, LocalDate hasta) {
        List<Lector> lectores;
        if (keyword != null && !keyword.isBlank()) {
            lectores = lectorRepository.searchByLibreria(libreriaId, keyword.trim());
        } else {
            lectores = lectorRepository.findByLibreriaId(libreriaId);
        }
        sincronizarEstados(lectores);

        return lectores.stream()
                .filter(l -> estado == null || estado.equalsIgnoreCase(l.getEstadoCuenta()))
                .filter(l -> desde == null
                        || (l.getFechaRegistro() != null && !l.getFechaRegistro().toLocalDate().isBefore(desde)))
                .filter(l -> hasta == null
                        || (l.getFechaRegistro() != null && !l.getFechaRegistro().toLocalDate().isAfter(hasta)))
                .sorted(Comparator
                        .comparing(Lector::getFechaRegistro, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(Lector::getId, Comparator.<Integer>reverseOrder()))
                .toList();
    }

    // Método privado para asegurar que el estado en memoria refleje las multas pendientes reales
    private void sincronizarEstados(List<Lector> lectores) {
        for (Lector lector : lectores) {
            boolean tienePendientes = multaRepository.existsByLectorIdAndEstadoPago(lector.getId(), "PENDIENTE");
            String estadoReal = tienePendientes ? "BLOQUEADO" : "HABILITADO";
            
            // Si difiere del estado actual en la BD, lo actualizamos al vuelo
            if (!estadoReal.equals(lector.getEstadoCuenta())) {
                lector.setEstadoCuenta(estadoReal);
                lectorRepository.save(lector);
            }
        }
    }

    /** Claves del resultado de duplicados(): nombre completo ("nombre") y celular ("telefono"). */
    public static final String DUP_NOMBRE = "nombre";
    public static final String DUP_TELEFONO = "telefono";
    /** El celular no está repetido en esta librería, pero ya pertenece a un lector de OTRA librería de la red. */
    public static final String DUP_TELEFONO_RED = "telefono_red";

    /**
     * Duplicados: (1) mismo nombre completo (nombres Y apellidos, sin importar mayúsculas, tildes ni espacios
     * sobrantes) DENTRO de la librería; en otra librería puede ser otra persona, así que no se bloquea. (2) mismo
     * celular dentro de la librería (DUP_TELEFONO). (3) celular que ya pertenece a un lector de otra librería
     * (DUP_TELEFONO_RED): es la misma persona y debe conservar su código, así que entra con su código en vez de
     * recibir uno nuevo. Vacío = todo bien. Al modificar (id != null) solo se revisa lo que cambió, para no bloquear
     * a lectores antiguos.
     */
    public Map<String, String> duplicados(Integer libreriaId, Integer id, String nombres, String apellidos, String telefono) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (libreriaId == null) {
            return errores;
        }
        Lector existente = id == null ? null : lectorRepository.findById(id).orElse(null);

        String n = limpiarEspacios(nombres);
        String a = limpiarEspacios(apellidos);
        if (!n.isEmpty() && !a.isEmpty()) {
            boolean cambio = existente == null
                    || !n.equalsIgnoreCase(limpiarEspacios(existente.getNombres()))
                    || !a.equalsIgnoreCase(limpiarEspacios(existente.getApellidos()));
            if (cambio && lectorRepository.findAllByLibreriaIdAndNombresIgnoreCaseAndApellidosIgnoreCase(libreriaId, n, a)
                    .stream().anyMatch(l -> !l.getId().equals(id))) {
                errores.put(DUP_NOMBRE, "Ya existe un lector con esos nombres y apellidos en esta librería.");
            }
        }

        String cel = TelefonoUtil.normalizar(telefono);
        if (cel != null && cel.matches("\\d{9}")) {
            boolean cambio = existente == null || !cel.equals(TelefonoUtil.normalizar(existente.getTelefono()));
            if (cambio && lectorRepository.findAllByLibreriaIdAndTelefono(libreriaId, cel)
                    .stream().anyMatch(l -> !l.getId().equals(id))) {
                errores.put(DUP_TELEFONO, "Este celular ya está registrado en otro lector de esta librería.");
            } else if (cambio) {
                String codigoPropio = existente == null ? null : existente.getCodigoLector();
                boolean enOtraLibreria = lectorRepository.findAllByTelefono(cel).stream()
                        .anyMatch(l -> !libreriaId.equals(l.getLibreriaId())
                                && (codigoPropio == null || !codigoPropio.equals(l.getCodigoLector())));
                if (enOtraLibreria) {
                    errores.put(DUP_TELEFONO_RED, "Este celular ya pertenece a un lector de la red. "
                            + "Pídele que inicie sesión en tu librería con su código y quedará registrado.");
                }
            }
        }
        return errores;
    }

    private static String limpiarEspacios(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }

    // Guarda (o modifica) un lector SIEMPRE dentro de la librería indicada
    public void guardar(Integer libreriaId, Lector lector) {
        // El celular es obligatorio: es lo que permite al lector recuperar su acceso por SMS
        String celular = TelefonoUtil.normalizar(lector.getTelefono());
        if (celular == null || !celular.matches("\\d{9}")) {
            throw new IllegalArgumentException("El teléfono es obligatorio y debe tener 9 dígitos.");
        }

        // MODIFICAR: solo cambian nombres, apellidos y teléfono.
        if (lector.getId() != null) {
            Lector existente = lectorRepository.findById(lector.getId()).orElse(null);
            if (existente == null || !libreriaId.equals(existente.getLibreriaId())) {
                throw new IllegalArgumentException("El lector no existe o no pertenece a tu librería.");
            }
            Map<String, String> repetidos = duplicados(libreriaId, existente.getId(), lector.getNombres(), lector.getApellidos(), celular);
            if (!repetidos.isEmpty()) {
                throw new IllegalArgumentException(repetidos.values().iterator().next());
            }
            existente.setNombres(lector.getNombres());
            existente.setApellidos(lector.getApellidos());
            existente.setTelefono(TelefonoUtil.normalizar(lector.getTelefono())); // siempre solo dígitos
            lectorRepository.save(existente);
            return;
        }

        Map<String, String> repetidos = duplicados(libreriaId, null, lector.getNombres(), lector.getApellidos(), celular);
        if (!repetidos.isEmpty()) {
            throw new IllegalArgumentException(repetidos.values().iterator().next());
        }

        // REGISTRAR: la contraseña inicial la escribe el administrador (el lector la podrá cambiar en Ajustes)
        String password = lector.getPassword() == null ? "" : lector.getPassword();
        if (password.length() < 6 || password.length() > 20 || password.matches(".*\\s.*")) {
            throw new IllegalArgumentException("La contraseña debe tener entre 6 y 20 caracteres, sin espacios.");
        }

        lector.setPassword(PasswordUtil.hash(password));
        lector.setTelefono(TelefonoUtil.normalizar(lector.getTelefono())); // siempre solo dígitos
        lector.setLibreriaId(libreriaId);
        lector.setCodigoLector(generarSiguienteCodigo());
        lector.setEstadoCuenta("HABILITADO");
        lectorRepository.save(lector);
    }

    public Lector obtenerPorId(Integer id) {
        Lector lector = lectorRepository.findById(id).orElse(null);
        if (lector != null) {
            boolean tienePendientes = multaRepository.existsByLectorIdAndEstadoPago(lector.getId(), "PENDIENTE");
            lector.setEstadoCuenta(tienePendientes ? "BLOQUEADO" : "HABILITADO");
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

    // El código de lector es único en TODA la base (así el login de lectores sigue funcionando con solo el código).
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
    
    public long contarTotalLectoresPorLibreria(Integer libreriaId) {
        return lectorRepository.countByLibreriaId(libreriaId);
    }

    public long contarLectoresBloqueadosPorLibreria(Integer libreriaId) {
        // Aseguramos sincronización antes de contar
        sincronizarEstados(lectorRepository.findByLibreriaId(libreriaId));
        return lectorRepository.countByLibreriaIdAndEstadoCuenta(libreriaId, "BLOQUEADO");
    }
}