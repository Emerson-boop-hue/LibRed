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
     *   (con el estado propio que tenga allí: HABILITADO o BLOQUEADO).
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

        return Optional.of(crearRegistro(origen.get(), libreriaId));
    }

    /** Resultado de cambiar de librería desde Red Global. */
    public record CambioSede(Lector lector, boolean creado, String libreriaNombre) {}

    /**
     * Pasa al lector (que ya inició sesión) a OTRA librería de la red sin volver a pedirle la contraseña.
     * - Si ya tiene registro en esa librería: se usa ese (con su estado propio allí).
     * - Si es su primera vez: se le crea automáticamente un registro nuevo, igual que en el login.
     * Se identifica por el código de lector de su sesión actual, así nadie puede entrar a la cuenta de otra persona.
     */
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
        nuevo.setEstadoCuenta("HABILITADO"); // empieza limpio: los bloqueos de otras librerías no lo afectan
        nuevo.setLibreriaId(libreriaId);
        return lectorRepository.save(nuevo);
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