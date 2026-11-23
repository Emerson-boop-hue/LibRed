package com.libred.sistema.service;

import com.libred.sistema.model.TicketSoporte;
import com.libred.sistema.repository.TicketSoporteRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conecta "Ayuda y Soporte" con un correo de Gmail: */
@Service
public class CorreoSoporteService {

    private static final Logger log = LoggerFactory.getLogger(CorreoSoporteService.class);

    private static final Pattern ETIQUETA = Pattern.compile("\\[LibRed #(\\d{1,9})]");
    private static final Pattern ES_RESPUESTA = Pattern.compile("^\\s*(re|rv|res)\\s*:.*", Pattern.CASE_INSENSITIVE);
    /** Cabecera de la cita que añade Gmail: "El jue, 12 nov 2026 a la(s) 7:35, Nombre <correo> escribió:" */
    private static final Pattern CABECERA_CITA = Pattern.compile("^(El|On)\\s.*\\d{4}.*", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RESPUESTA = 1000;
    private static final int DIAS_REVISION = 3;

    @Value("${libred.soporte.correo:}")
    private String correo;
    @Value("${libred.soporte.clave:}")
    private String clave;
    @Value("${libred.soporte.imap-host:imap.gmail.com}")
    private String imapHost;

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TicketSoporteRepository ticketRepository;

    public CorreoSoporteService(ObjectProvider<JavaMailSender> mailSender, TicketSoporteRepository ticketRepository) {
        this.mailSender = mailSender;
        this.ticketRepository = ticketRepository;
    }

    private boolean habilitado() {
        return correo != null && !correo.isBlank() && clave != null && !clave.isBlank();
    }

    /**
     * Avisa por correo de una solicitud nueva. Se hace en segundo plano: si falla, la solicitud ya quedó guardada.
     */
    public void avisarSolicitud(Integer id, String libreria, String codigoAdmin, String categoriaTexto,
                                String asunto, String mensaje) {
        if (!habilitado()) {
            log.info("Soporte por correo desactivado (libred.soporte.clave vacía): la solicitud #{} solo se guarda en la base.", id);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return;
        }
        String asuntoCorreo = String.format("[LibRed #%04d] %s", id, asunto.replaceAll("[\\r\\n]+", " "));
        String texto = "Librería: " + libreria + " (" + codigoAdmin + ")\n"
                + "Tipo: " + categoriaTexto + "\n\n"
                + mensaje;
        CompletableFuture.runAsync(() -> {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(correo);
                m.setTo(correo);
                m.setSubject(asuntoCorreo);
                m.setText(texto);
                sender.send(m);
                log.info("Solicitud #{} enviada por correo.", id);
            } catch (Exception e) {
                log.warn("No se pudo enviar el correo de la solicitud #{}: {}", id, e.getMessage());
            }
        });
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void revisarRespuestas() {
        if (!habilitado()) {
            return;
        }
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "10000");
        p.put("mail.imaps.timeout", "15000");
        // PEEK: leer el cuerpo no cambia el estado de lectura del correo
        p.put("mail.imaps.peek", "true");
        try (Store store = Session.getInstance(p).getStore("imaps")) {
            store.connect(imapHost, 993, correo, clave);
            Folder bandeja = store.getFolder("INBOX");
            bandeja.open(Folder.READ_ONLY);
            try {
                // No se depende de "no leído": Gmail marca como leída tu respuesta si el hilo está abierto. En su lugar se
                // revisan los correos de los últimos días y solo se registra la respuesta de las solicitudes aún abiertas.
                Date desde = Date.from(LocalDateTime.now().minusDays(DIAS_REVISION).atZone(ZoneId.systemDefault()).toInstant());
                SearchTerm filtro = new AndTerm(new ReceivedDateTerm(ComparisonTerm.GE, desde), new SubjectTerm("LibRed"));
                int registradas = 0;
                for (Message m : bandeja.search(filtro)) {
                    try {
                        if (procesar(m)) {
                            registradas++;
                        }
                    } catch (Exception e) {
                        log.warn("No se pudo procesar un correo de soporte: {}", e.getMessage());
                    }
                }
                if (registradas > 0) {
                    log.info("Revisión de soporte: {} respuesta(s) registrada(s).", registradas);
                }
            } finally {
                bandeja.close(false);
            }
        } catch (Exception e) {
            log.warn("No se pudo revisar la bandeja de soporte: {}", e.getMessage());
        }
    }

    /** @return true si registró una respuesta nueva en una solicitud que seguía abierta. */
    private boolean procesar(Message m) throws Exception {
        String asunto = m.getSubject();
        if (asunto == null || !ES_RESPUESTA.matcher(asunto).matches()) {
            return false; // el aviso original (sin "Re:") o cualquier otro correo: se ignora
        }
        Matcher et = ETIQUETA.matcher(asunto);
        if (!et.find()) {
            return false;
        }
        Integer id = Integer.valueOf(et.group(1));
        TicketSoporte t = ticketRepository.findById(id).orElse(null);
        if (t == null || !"ABIERTO".equals(t.getEstado())) {
            return false; // ya tiene respuesta (o no existe): no se vuelve a leer el correo
        }
        if (!vieneDelCorreoDeSoporte(m) || esAnteriorALaSolicitud(m, t)) {
            return false; // una respuesta vieja con el mismo número no pertenece a esta solicitud
        }
        String texto = limpiarRespuesta(textoDe(m));
        if (texto.isBlank()) {
            return false;
        }
        t.setRespuesta(texto.length() > MAX_RESPUESTA ? texto.substring(0, MAX_RESPUESTA) : texto);
        t.setFechaRespuesta(LocalDateTime.now());
        t.setEstado("RESUELTO");
        ticketRepository.save(t);
        log.info("Respuesta registrada para la solicitud #{}.", id);
        return true;
    }

    /** True si el correo se envió antes de crearse la solicitud (p. ej. sobrante de otra solicitud con el mismo número). */
    private boolean esAnteriorALaSolicitud(Message m, TicketSoporte t) throws Exception {
        Date enviado = m.getSentDate() != null ? m.getSentDate() : m.getReceivedDate();
        if (enviado == null || t.getFechaCreacion() == null) {
            return false;
        }
        LocalDateTime cuando = LocalDateTime.ofInstant(enviado.toInstant(), ZoneId.systemDefault());
        return cuando.isBefore(t.getFechaCreacion());
    }

    private boolean vieneDelCorreoDeSoporte(Message m) throws Exception {
        Address[] de = m.getFrom();
        if (de == null) {
            return false;
        }
        for (Address a : de) {
            if (a instanceof InternetAddress ia && ia.getAddress() != null && ia.getAddress().equalsIgnoreCase(correo)) {
                return true;
            }
        }
        return false;
    }

    /** Texto plano del correo (si solo viene en HTML, se le quitan las etiquetas). */
    private String textoDe(Part parte) throws Exception {
        if (parte.isMimeType("text/plain")) {
            return String.valueOf(parte.getContent());
        }
        if (parte.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) parte.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part sub = mp.getBodyPart(i);
                if (sub.isMimeType("text/plain")) {
                    return String.valueOf(sub.getContent());
                }
                if (sub.isMimeType("multipart/*")) {
                    String r = textoDe(sub);
                    if (!r.isBlank()) {
                        return r;
                    }
                }
                if (sub.isMimeType("text/html")) {
                    html = String.valueOf(sub.getContent());
                }
            }
            if (html != null) {
                return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</(div|p)>", "\n").replaceAll("<[^>]+>", "")
                        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
            }
        }
        if (parte.isMimeType("text/html")) {
            return String.valueOf(parte.getContent()).replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        }
        return "";
    }

    /** Se queda solo con lo que escribiste: corta en cuanto empieza el texto citado del mensaje original. */
    static String limpiarRespuesta(String bruto) {
        StringBuilder sb = new StringBuilder();
        for (String linea : bruto.replace("\r", "").split("\n")) {
            String t = linea.trim();
            if (t.startsWith(">")) {
                break;
            }
            boolean cabeceraCita = CABECERA_CITA.matcher(t).matches()
                    && (t.contains("@") || t.endsWith("escribió:") || t.endsWith("wrote:") || t.endsWith("<"));
            if (cabeceraCita || t.startsWith("-----Original") || t.startsWith("________________")) {
                break;
            }
            sb.append(linea).append('\n');
        }
        return sb.toString().trim();
    }
}