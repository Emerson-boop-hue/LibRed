package com.libred.sistema.service;

import com.libred.sistema.model.Libreria;
import com.libred.sistema.repository.LibreriaRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Estado de la membresía de una librería: ACTIVA, GRACIA (venció hace pocos días, todo sigue funcionando) o SUSPENDIDA
 * (solo lectura; las devoluciones y la renovación siguen disponibles).
 */
@Service
public class MembresiaService {

    public static final int DIAS_GRACIA = 3;
    public static final int DIAS_AVISO = 30;

    public enum Fase { ACTIVA, GRACIA, SUSPENDIDA }

    /**
     * @param diasRestantes días que faltan para vencer (ACTIVA) o para que termine la gracia (GRACIA); 0 si está suspendida
     * @param aviso         "", "info", "ambar" o "rojo": color de la franja según la urgencia
     */
    public record Estado(Fase fase, long diasRestantes, String aviso) {
        public boolean escrituraBloqueada() { return fase == Fase.SUSPENDIDA; }
        public boolean mostrarFranja() { return !aviso.isEmpty(); }
        public String getFase() { return fase.name(); }
        public long getDiasRestantes() { return diasRestantes; }
        public String getAviso() { return aviso; }
        public boolean isSuspendida() { return fase == Fase.SUSPENDIDA; }
    }

    private final LibreriaRepository libreriaRepository;

    public MembresiaService(LibreriaRepository libreriaRepository) {
        this.libreriaRepository = libreriaRepository;
    }

    public Estado estadoDe(Integer libreriaId) {
        return libreriaRepository.findById(libreriaId).map(this::estadoDe)
                .orElse(new Estado(Fase.ACTIVA, 0, ""));
    }

    public Estado estadoDe(Libreria l) {
        LocalDateTime vence = l.getFechaVencimiento();
        if (vence == null) {
            return new Estado(Fase.ACTIVA, 0, "");
        }
        LocalDateTime ahora = LocalDateTime.now();
        long minutos = Duration.between(ahora, vence).toMinutes();
        if (minutos >= 0) {
            long dias = (long) Math.ceil(minutos / 1440.0);
            String aviso = dias > DIAS_AVISO ? "" : dias <= 3 ? "rojo" : dias <= 7 ? "ambar" : "info";
            return new Estado(Fase.ACTIVA, dias, aviso);
        }
        long minutosGracia = Duration.between(ahora, vence.plusDays(DIAS_GRACIA)).toMinutes();
        if (minutosGracia >= 0) {
            return new Estado(Fase.GRACIA, (long) Math.ceil(minutosGracia / 1440.0), "rojo");
        }
        return new Estado(Fase.SUSPENDIDA, 0, "rojo");
    }
}
