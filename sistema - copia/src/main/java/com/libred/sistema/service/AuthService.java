package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final LectorRepository lectorRepository;
    private final LibreriaRepository libreriaRepository;

    // Inyección por constructor (Buena práctica recomendada, no requiere @Autowired)
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
     *
     * - Si ya está registrado en esa librería: se valida su contraseña y entra con su registro de esa librería
     *   (con el estado propio que tenga allí: HABILITADO o SANCIONADO).
     * - Si es su PRIMERA VEZ en esa librería: se valida su contraseña contra su registro en otra librería y se
     *   le crea automáticamente un registro nuevo (HABILITADO) en la librería elegida, copiando sus datos.
     *   Así aparece solo en la tabla de lectores de esa librería.
     *
     * Devuelve el registro del lector EN ESA LIBRERÍA (para guardarlo en la sesión) o vacío si algo no coincide.
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

        // 1) Ya está registrado en la librería elegida
        Optional<Lector> enLibreria = registros.stream()
                .filter(l -> libreriaId.equals(l.getLibreriaId()))
                .findFirst();
        if (enLibreria.isPresent()) {
            return enLibreria.filter(l -> contrasenaCorrecta(l, password));
        }

        // 2) Primera vez en esa librería: la contraseña debe ser la de su cuenta en otra librería
        Optional<Lector> origen = registros.stream()
                .filter(l -> contrasenaCorrecta(l, password))
                .findFirst();
        if (origen.isEmpty()) {
            return Optional.empty();
        }

        Lector base = origen.get();
        Lector nuevo = new Lector();
        nuevo.setCodigoLector(base.getCodigoLector());
        nuevo.setNombres(base.getNombres());
        nuevo.setApellidos(base.getApellidos());
        nuevo.setTelefono(base.getTelefono());
        nuevo.setPassword(base.getPassword());
        nuevo.setEstadoCuenta("HABILITADO"); // empieza limpio: las sanciones de otras librerías no lo afectan
        nuevo.setLibreriaId(libreriaId);
        return Optional.of(lectorRepository.save(nuevo));
    }

    private boolean contrasenaCorrecta(Lector lector, String password) {
        return password != null && password.equals(lector.getPassword());
    }

    // Lógica para validar el login del Administrador (Ej: ADM-001).
    // Devuelve la librería si el código y la contraseña son correctos (para guardarla en la sesión), o vacío si no.
    public Optional<Libreria> autenticarAdmin(String codigo, String password) {
        return libreriaRepository.findByCodigoAdmin(codigo)
                .filter(admin -> admin.getPassword().equals(password));
    }
}