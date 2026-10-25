package com.libred.sistema.service;

import com.libred.sistema.model.Lector;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LectorRepository;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.stereotype.Service;
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

    // Lógica para validar el login del Lector (Ej: LEC-1001)
    public boolean autenticarLector(String codigo, String password) {
        Optional<Lector> lector = lectorRepository.findByCodigoLector(codigo);
        if (lector.isPresent()) {
            return lector.get().getPassword().equals(password);
        }
        return false;
    }

    // Lógica para validar el login del Administrador (Ej: ADM-001)
    public boolean autenticarAdmin(String codigo, String password) {
        Optional<Libreria> admin = libreriaRepository.findByCodigoAdmin(codigo);
        if (admin.isPresent()) {
            return admin.get().getPassword().equals(password);
        }
        return false;
    }
}