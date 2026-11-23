package com.libred.sistema.controller;

import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Prestamo;
import com.libred.sistema.repository.DevolucionRepository;
import com.libred.sistema.repository.LibreriaRepository;
import com.libred.sistema.repository.PrestamoRepository;
import com.libred.sistema.service.ComprobantePdfService;
import com.libred.sistema.service.MembresiaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Descarga de comprobantes en PDF. El comprobante de préstamo se bloquea con la membresía suspendida (administrador y
 * lector); el de devolución siempre se puede descargar, porque devolver sigue permitido.
 */
@RestController
public class ComprobanteController {

    private final PrestamoRepository prestamoRepository;
    private final DevolucionRepository devolucionRepository;
    private final LibreriaRepository libreriaRepository;
    private final MembresiaService membresiaService;
    private final ComprobantePdfService pdfService;

    public ComprobanteController(PrestamoRepository prestamoRepository, DevolucionRepository devolucionRepository,
                                 LibreriaRepository libreriaRepository, MembresiaService membresiaService,
                                 ComprobantePdfService pdfService) {
        this.prestamoRepository = prestamoRepository;
        this.devolucionRepository = devolucionRepository;
        this.libreriaRepository = libreriaRepository;
        this.membresiaService = membresiaService;
        this.pdfService = pdfService;
    }

    @GetMapping("/admin/prestamos/{id}/comprobante.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> prestamoAdmin(@PathVariable Integer id, HttpSession session) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Prestamo p = prestamoRepository.findById(id).orElse(null);
        if (libreriaId == null || p == null || !libreriaId.equals(p.getLibreriaId())) {
            return ResponseEntity.notFound().build();
        }
        return prestamoPdf(p);
    }

    @GetMapping("/admin/devoluciones/{id}/comprobante.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> devolucionAdmin(@PathVariable Integer id, HttpSession session) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Devolucion d = devolucionRepository.findById(id).orElse(null);
        if (libreriaId == null || d == null || !libreriaId.equals(d.getLibreriaId())) {
            return ResponseEntity.notFound().build();
        }
        Libreria lib = libreriaRepository.findById(libreriaId).orElse(null);
        return pdf(pdfService.devolucion(d, lib), "comprobante-devolucion-" + d.getId());
    }

    @GetMapping("/lector/prestamos/{id}/comprobante.pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> prestamoLector(@PathVariable Integer id, HttpSession session) {
        Prestamo p = prestamoRepository.findById(id).orElse(null);
        if (!(session.getAttribute("lectorId") instanceof Integer lectorId) || p == null
                || p.getLector() == null || !lectorId.equals(p.getLector().getId())) {
            return ResponseEntity.notFound().build();
        }
        return prestamoPdf(p);
    }

    private ResponseEntity<byte[]> prestamoPdf(Prestamo p) {
        if (membresiaService.estadoDe(p.getLibreriaId()).escrituraBloqueada()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Libreria lib = libreriaRepository.findById(p.getLibreriaId()).orElse(null);
        return pdf(pdfService.prestamo(p, lib), "comprobante-prestamo-" + p.getId());
    }

    private ResponseEntity<byte[]> pdf(byte[] contenido, String nombre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre + ".pdf", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }
}