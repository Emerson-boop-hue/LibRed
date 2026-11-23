package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.service.SuscripcionService.SuscripcionException;
import com.libred.sistema.util.PlantillasCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.libred.sistema.util.PasswordUtil;

/**
 * "Recuperar acceso" del administrador: con el correo verificado de su librería recibe un código de 6 dígitos y con
 * él define una contraseña nueva.
 */
@Service
public class RecuperacionAccesoService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionAccesoService.class);

    private final LibreriaRepository libreriaRepository;
    private final VerificacionCodigoService codigos;
    private final CorreoSistemaService correoService;

    public RecuperacionAccesoService(LibreriaRepository libreriaRepository, VerificacionCodigoService codigos,
                                     CorreoSistemaService correoService) {
        this.libreriaRepository = libreriaRepository;
        this.codigos = codigos;
        this.correoService = correoService;
    }

    public Map<String, Object> enviarCodigo(String correoCrudo) {
        String correo = RegistroService.normalizarCorreo(correoCrudo);
        String codigo = codigos.generar(correo, VerificacionCodigoService.RECUPERACION, VerificacionCodigoService.CORREO);

        Optional<Libreria> libreria = libreriaRepository.findByCorreoCuentaAndCorreoVerificadoTrue(correo);
        if (libreria.isPresent()) {
            if (!correoService.disponible()) {
                log.warn("[RECUPERACION] El envío de correos no está configurado. Código para {}: {}", correo, codigo);
            } else {
                // Espera hasta 15 s a que Gmail confirme; un fallo solo se anota (la respuesta es la misma exista o
                // no el correo)
                correoService.enviarYEsperar(correo, "Recupera tu acceso a LibRed",
                        PlantillasCorreo.recuperacion(codigo, libreria.get().getCodigoAdmin(),
                                libreria.get().getNombreComercial(), VerificacionCodigoService.CORREO.vigenciaMinutos()), 15);
            }
        } else {
            // Mismo tiempo de respuesta que cuando sí se envía un correo, para no delatar qué correos están
            // registrados
            try { Thread.sleep(2500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("espera", VerificacionCodigoService.CORREO.esperaSegundos());
        return r;
    }

    @Transactional(noRollbackFor = SuscripcionException.class)
    public Map<String, Object> restablecer(String correoCrudo, String codigo, String password) {
        String correo = RegistroService.normalizarCorreo(correoCrudo);
        // Primero el código (si está mal, no se revela nada más); la contraseña nueva se valida antes de consumirlo
        RegistroService.validarPassword(password, "password");
        codigos.validar(correo, VerificacionCodigoService.RECUPERACION, codigo, VerificacionCodigoService.CORREO, "codigo");

        Libreria l = libreriaRepository.findByCorreoCuentaAndCorreoVerificadoTrue(correo)
                .orElseThrow(() -> new SuscripcionException("codigo",
                        "El código venció o no existe. Solicita uno nuevo.", HttpStatus.BAD_REQUEST));
        l.setPassword(PasswordUtil.hash(password));
        libreriaRepository.save(l);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("codigoAdmin", l.getCodigoAdmin());
        return r;
    }
}