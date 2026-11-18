package com.libred.sistema.service;

import com.libred.sistema.util.TelefonoUtil;
import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import com.libred.sistema.util.PasswordUtil;

/** Validación de credenciales de administradores y lectores. */
@Service
public class AuthService {

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;

    public AuthService(LectorRepository lectorRepository, LibreriaRepository libreriaRepository) {
        this.lectorRepository = lectorRepository;
        this.libreriaRepository = libreriaRepository;
    }

    // Librerías para llenar la lista desplegable "Librería" del login del lector (ordenadas por nombre)
    public List<Libreria> listarLibrerias() {
        return libreriaRepository.findAll(Sort.by("nombreComercial"));
    }

    /**
     * Login del lector (Ej: LEC-1001) en la librería elegida. El lector puede entrar a CUALQUIER librería de la red.
     */
    @Transactional
    public Optional<Lector> autenticarLector(String codigo, String password, Integer libreriaId) {
        if (libreriaId == null || !libreriaRepository.existsById(libreriaId)) {
            return Optional.empty();
        }

        List<Lector> registros = lectorRepository.findAllByCodigoLector(codigo);
        if (registros.isEmpty()) {
            return Optional.empty();
        }

        Optional<Lector> enLibreria = registros.stream()
                .filter(l -> libreriaId.equals(l.getLibreriaId()))
                .findFirst();
        if (enLibreria.isPresent()) {
            return enLibreria.filter(l -> contrasenaCorrecta(l, password));
        }

        Optional<Lector> origen = registros.stream()
                .filter(l -> contrasenaCorrecta(l, password))
                .findFirst();
        if (origen.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(crearRegistro(origen.get(), libreriaId));
    }

    /** Resultado de cambiar de librería desde Red Global. */
    public record CambioSede(Lector lector, boolean creado, String libreriaNombre) {}

    /** Pasa al lector (que ya inició sesión) a OTRA librería de la red sin volver a pedirle la contraseña. */
    @Transactional
    public CambioSede cambiarSede(Integer lectorIdActual, Integer libreriaId) {
        Lector actual = lectorRepository.findById(lectorIdActual)
                .orElseThrow(() -> new AjustesException(null, "Tu sesión ya no es válida. Inicia sesión de nuevo."));
        Libreria destino = libreriaId == null ? null : libreriaRepository.findById(libreriaId).orElse(null);
        if (destino == null) {
            throw new AjustesException(null, "La librería que elegiste ya no existe en la red.");
        }

        Optional<Lector> yaRegistrado = lectorRepository.findAllByCodigoLector(actual.getCodigoLector()).stream()
                .filter(l -> libreriaId.equals(l.getLibreriaId()))
                .findFirst();
        if (yaRegistrado.isPresent()) {
            return new CambioSede(yaRegistrado.get(), false, destino.getNombreComercial());
        }
        return new CambioSede(crearRegistro(actual, libreriaId), true, destino.getNombreComercial());
    }

    // Registro nuevo del lector en otra librería, copiando sus datos
    private Lector crearRegistro(Lector base, Integer libreriaId) {
        Lector nuevo = new Lector();
        nuevo.setCodigoLector(base.getCodigoLector());
        nuevo.setNombres(base.getNombres());
        nuevo.setApellidos(base.getApellidos());
        nuevo.setTelefono(TelefonoUtil.normalizar(base.getTelefono()));
        nuevo.setPassword(base.getPassword());
        nuevo.setDebeCambiarPassword(base.isDebeCambiarPassword()); // si entró con contraseña temporal, el aviso lo sigue
        nuevo.setEstadoCuenta("HABILITADO"); // empieza limpio: los bloqueos de otras librerías no lo afectan
        nuevo.setLibreriaId(libreriaId);
        return lectorRepository.save(nuevo);
    }

    // Valida la contraseña; si la cuenta aún la tenía en texto plano, la deja guardada como hash
    private boolean contrasenaCorrecta(Lector lector, String password) {
        if (!PasswordUtil.coincide(password, lector.getPassword())) {
            return false;
        }
        if (!PasswordUtil.esHash(lector.getPassword())) {
            lector.setPassword(PasswordUtil.hash(password));
            lectorRepository.save(lector);
        }
        return true;
    }

    // Lógica para validar el login del Administrador (Ej: ADM-001).
    public Optional<Libreria> autenticarAdmin(String codigo, String password) {
        return libreriaRepository.findByCodigoAdmin(codigo)
                .filter(admin -> PasswordUtil.coincide(password, admin.getPassword()))
                .map(admin -> {
                    if (!PasswordUtil.esHash(admin.getPassword())) {
                        admin.setPassword(PasswordUtil.hash(password));
                        libreriaRepository.save(admin);
                    }
                    return admin;
                });
    }
}