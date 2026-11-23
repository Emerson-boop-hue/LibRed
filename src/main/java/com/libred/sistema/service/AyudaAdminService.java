package com.libred.sistema.service;

import com.libred.sistema.model.AyudaAdminVista.Pantalla;
import com.libred.sistema.model.AyudaAdminVista.Ticket;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.TicketSoporte;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.TicketSoporteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AYUDA Y SOPORTE del administrador: gestiona las solicitudes de soporte que envía. */
@Service
public class AyudaAdminService {

    public static final int ASUNTO_MIN = 4, ASUNTO_MAX = 120, MENSAJE_MIN = 10, MENSAJE_MAX = 1000;
    public static final int MAX_ABIERTAS = 3;

    /** Categorías de una solicitud: clave guardada -> texto que ve el administrador. */
    public static final Map<String, String> CATEGORIAS = new LinkedHashMap<>();

    static {
        CATEGORIAS.put("DUDA", "Tengo una duda");
        CATEGORIAS.put("ERROR", "Algo no funciona");
        CATEGORIAS.put("SUGERENCIA", "Quiero sugerir algo");
        CATEGORIAS.put("PLAN", "Mi plan o mi pago");
    }

    /**
     * Temas del primer paso del asistente: clave -> texto. Solo sirven para orientar la consulta (no se guardan en la
     * base).
     */
    public static final Map<String, String> TEMAS = new LinkedHashMap<>();

    static {
        TEMAS.put("prestamos", "Préstamos");
        TEMAS.put("devoluciones", "Devoluciones");
        TEMAS.put("multas", "Multas y bloqueos");
        TEMAS.put("inventario", "Inventario");
        TEMAS.put("lectores", "Lectores");
        TEMAS.put("plan", "Plan y pagos");
        TEMAS.put("otro", "Otro tema");
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LibreriaRepository libreriaRepository;
    private final TicketSoporteRepository ticketRepository;
    private final CorreoSoporteService correoService;

    public AyudaAdminService(LibreriaRepository libreriaRepository,
                             TicketSoporteRepository ticketRepository,
                             CorreoSoporteService correoService) {
        this.libreriaRepository = libreriaRepository;
        this.ticketRepository = ticketRepository;
        this.correoService = correoService;
    }

    @Transactional(readOnly = true)
    public Pantalla armar(Integer libreriaId) {
        Libreria lib = libreriaRepository.findById(libreriaId).orElse(null);
        String nombre = lib == null ? "Mi librería" : lib.getNombreComercial();
        String codigo = lib == null ? "" : lib.getCodigoAdmin();

        List<Ticket> tickets = new ArrayList<>();
        for (TicketSoporte t : ticketRepository.findByLibreriaIdOrderByIdDesc(libreriaId)) {
            tickets.add(new Ticket(
                    String.format("#%04d", t.getId()),
                    CATEGORIAS.getOrDefault(t.getCategoria(), t.getCategoria()),
                    t.getAsunto(), t.getMensaje(), t.getEstado(), "RESUELTO".equals(t.getEstado()) ? "Resuelta" : "Abierta",
                    t.getFechaCreacion() == null ? "" : t.getFechaCreacion().format(FECHA),
                    t.getRespuesta(),
                    t.getFechaRespuesta() == null ? "" : t.getFechaRespuesta().format(FECHA)));
        }
        return new Pantalla(nombre, codigo, tickets, ticketRepository.countByLibreriaIdAndEstado(libreriaId, "ABIERTO"));
    }

    /**
     * Guarda una solicitud nueva y la avisa por correo. Lanza IllegalArgumentException con el mensaje que se muestra
     * en rojo si algo no cumple.
     */
    @Transactional
    public void crearTicket(Integer libreriaId, String categoria, String tema, String asunto, String mensaje) {
        String cat = categoria == null ? "" : categoria.trim().toUpperCase();
        String as = asunto == null ? "" : asunto.trim();
        String me = mensaje == null ? "" : mensaje.trim();

        if (!CATEGORIAS.containsKey(cat)) {
            throw new IllegalArgumentException("Elige el tipo de solicitud.");
        }
        if (as.length() < ASUNTO_MIN || as.length() > ASUNTO_MAX) {
            throw new IllegalArgumentException("El asunto debe tener entre " + ASUNTO_MIN + " y " + ASUNTO_MAX + " caracteres.");
        }
        if (me.length() < MENSAJE_MIN || me.length() > MENSAJE_MAX) {
            throw new IllegalArgumentException("Cuéntanos tu caso en el mensaje (entre " + MENSAJE_MIN + " y " + MENSAJE_MAX + " caracteres).");
        }
        if (ticketRepository.countByLibreriaIdAndEstado(libreriaId, "ABIERTO") >= MAX_ABIERTAS) {
            throw new IllegalArgumentException("Ya tienes " + MAX_ABIERTAS + " solicitudes sin resolver. Espera su respuesta antes de enviar otra.");
        }

        TicketSoporte t = new TicketSoporte();
        t.setLibreriaId(libreriaId);
        t.setCategoria(cat);
        t.setAsunto(as);
        t.setMensaje(me);
        t.setEstado("ABIERTO");
        ticketRepository.save(t);

        Libreria lib = libreriaRepository.findById(libreriaId).orElse(null);
        correoService.avisarSolicitud(t.getId(),
                lib == null ? "Librería " + libreriaId : lib.getNombreComercial(),
                lib == null ? "" : lib.getCodigoAdmin(),
                CATEGORIAS.get(cat) + (TEMAS.containsKey(tema) ? " · " + TEMAS.get(tema) : ""), as, me);
    }
}