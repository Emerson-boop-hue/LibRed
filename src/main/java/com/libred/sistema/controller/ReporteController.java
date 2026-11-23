package com.libred.sistema.controller;

import com.libred.sistema.model.ReporteVista.Documento;
import com.libred.sistema.service.ReporteExcelService;
import com.libred.sistema.service.ReportePdfService;
import com.libred.sistema.service.ReporteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

/** Módulo REPORTES del administrador: vista previa en pantalla y descarga en PDF o Excel. */
@Controller
public class ReporteController {

    private final ReporteService reporteService;
    private final ReportePdfService pdfService;
    private final ReporteExcelService excelService;

    public ReporteController(ReporteService reporteService, ReportePdfService pdfService, ReporteExcelService excelService) {
        this.reporteService = reporteService;
        this.pdfService = pdfService;
        this.excelService = excelService;
    }

    @GetMapping("/admin/reportes")
    public String reportes(@RequestParam(required = false) String tipo,
                           @RequestParam(required = false) String desde,
                           @RequestParam(required = false) String hasta,
                           HttpSession session, Model model) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Documento doc = reporteService.armar(libreriaId, tipo, desde, hasta);
        model.addAttribute("doc", doc);
        model.addAttribute("hoy", java.time.LocalDate.now().toString());
        return "admin/reportes";
    }

    @GetMapping("/admin/reportes/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) String tipo,
                                      @RequestParam(required = false) String desde,
                                      @RequestParam(required = false) String hasta,
                                      HttpSession session) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Documento doc = reporteService.armar(libreriaId, tipo, desde, hasta);
        byte[] bytes = pdfService.generar(doc, reporteService.logoPng());
        return descarga(bytes, doc.archivo() + ".pdf", MediaType.APPLICATION_PDF);
    }

    @GetMapping("/admin/reportes/excel")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) String tipo,
                                        @RequestParam(required = false) String desde,
                                        @RequestParam(required = false) String hasta,
                                        HttpSession session) {
        Integer libreriaId = (Integer) session.getAttribute("libreriaId");
        Documento doc = reporteService.armar(libreriaId, tipo, desde, hasta);
        byte[] bytes = excelService.generar(doc, reporteService.logoPng());
        return descarga(bytes, doc.archivo() + ".xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private ResponseEntity<byte[]> descarga(byte[] bytes, String nombre, MediaType tipo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .contentType(tipo)
                .contentLength(bytes.length)
                .body(bytes);
    }
}