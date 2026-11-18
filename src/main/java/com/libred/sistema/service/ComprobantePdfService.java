package com.libred.sistema.service;

import com.libred.sistema.model.CategoriaRecurso;
import com.libred.sistema.model.Devolucion;
import com.libred.sistema.model.Libreria;
import com.libred.sistema.model.Prestamo;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Genera los comprobantes en PDF (una hoja A5) de un préstamo y de una devolución. */
@Service
public class ComprobantePdfService {

    private static final Color GRAFITO = new Color(0x33, 0x41, 0x55);
    private static final Color GRAFITO_OSCURO = new Color(0x1e, 0x29, 0x3b);
    private static final Color BORDE = new Color(0xcb, 0xd5, 0xe1);
    private static final Color FONDO = new Color(0xf1, 0xf5, 0xf9);

    private static final Font F_NOMBRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, GRAFITO_OSCURO);
    private static final Font F_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, GRAFITO_OSCURO);
    private static final Font F_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Font F_VALOR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK);
    private static final Font F_TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, GRAFITO_OSCURO);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReporteService reporteService;

    public ComprobantePdfService(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    public byte[] prestamo(Prestamo p, Libreria lib) {
        boolean porEntregar = "POR_ENTREGAR".equalsIgnoreCase(p.getEstadoPrestamo());
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Lector", nombreLector(p)});
        filas.add(new String[]{"Código de lector", p.getLector() == null ? "" : texto(p.getLector().getCodigoLector())});
        filas.add(new String[]{"Artículo", p.getRecurso() == null ? "" : texto(p.getRecurso().getTitulo())});
        filas.add(new String[]{"Código de artículo", p.getRecurso() == null ? "" : texto(p.getRecurso().getCodigo())});
        if (p.getRecurso() != null) {
            filas.add(new String[]{"Categoría", CategoriaRecurso.desdeValor(p.getRecurso().getTipoRecurso()).getEtiqueta()});
        }
        filas.add(new String[]{porEntregar ? "Pedido el" : "Fecha de préstamo", hora(p.getFechaPrestamo())});
        filas.add(new String[]{"Último día de devolución",
                porEntregar ? "Se define al entregar (" + p.getDiasPrestamo() + " días desde la entrega)" : fecha(p.getFechaDevolucionLimite())});
        filas.add(new String[]{"Días de préstamo", String.valueOf(p.getDiasPrestamo())});
        filas.add(new String[]{"Tarifa por día", dinero(p.getTarifaDia())});
        filas.add(new String[]{"Estado del servicio", porEntregar ? "Por entregar (pendiente de cobro)" : "Cobrado"});
        return armar(lib, "Comprobante de préstamo", "N.º " + p.getId(), filas,
                "Servicio de préstamo", dinero(p.getMontoServicio()));
    }

    public byte[] devolucion(Devolucion d, Libreria lib) {
        Prestamo p = d.getPrestamo();
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"Lector", nombreLector(p)});
        filas.add(new String[]{"Código de lector", p.getLector() == null ? "" : texto(p.getLector().getCodigoLector())});
        filas.add(new String[]{"Artículo", p.getRecurso() == null ? "" : texto(p.getRecurso().getTitulo())});
        filas.add(new String[]{"Código de artículo", p.getRecurso() == null ? "" : texto(p.getRecurso().getCodigo())});
        filas.add(new String[]{"Fecha de préstamo", hora(p.getFechaPrestamo())});
        filas.add(new String[]{"Último día de devolución", fecha(p.getFechaDevolucionLimite())});
        filas.add(new String[]{"Fecha de devolución", hora(d.getFechaDevolucion())});
        filas.add(new String[]{"Días de retraso", String.valueOf(d.getDiasRetraso() == null ? 0 : d.getDiasRetraso())});
        filas.add(new String[]{"Mora", dinero(d.getMontoMora())});
        filas.add(new String[]{"Multa pendiente liquidada", dinero(d.getMontoMulta())});
        return armar(lib, "Comprobante de devolución", "N.º " + d.getId(), filas,
                "Total cobrado", dinero(d.getMontoTotal()));
    }

    private byte[] armar(Libreria lib, String titulo, String numero, List<String[]> filas, String etiquetaTotal, String total) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document pdf = new Document(PageSize.A5, 30, 30, 30, 30);
            PdfWriter.getInstance(pdf, salida);
            pdf.open();

            pdf.add(encabezado(lib));
            Paragraph t = new Paragraph(titulo, F_TITULO);
            t.setSpacingBefore(14);
            pdf.add(t);
            pdf.add(new Paragraph(numero + "  ·  Emitido el " + LocalDateTime.now().format(FECHA_HORA), F_TEXTO));

            PdfPTable tabla = new PdfPTable(new float[]{1.5f, 2f});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(12);
            for (String[] f : filas) {
                tabla.addCell(celda(f[0], F_ETIQUETA, Element.ALIGN_LEFT, false));
                tabla.addCell(celda(f[1], F_VALOR, Element.ALIGN_RIGHT, false));
            }
            PdfPCell et = celda(etiquetaTotal, F_TOTAL, Element.ALIGN_LEFT, true);
            PdfPCell vl = celda(total, F_TOTAL, Element.ALIGN_RIGHT, true);
            tabla.addCell(et);
            tabla.addCell(vl);
            pdf.add(tabla);

            Paragraph pie = new Paragraph("Comprobante interno generado por LibRed. Sin validez tributaria.", F_TEXTO);
            pie.setSpacingBefore(18);
            pie.setAlignment(Element.ALIGN_CENTER);
            pdf.add(pie);
            pdf.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el comprobante.", e);
        }
    }

    private PdfPTable encabezado(Libreria lib) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 2.4f});
        t.setWidthPercentage(100);

        PdfPCell izq = new PdfPCell();
        izq.setBorder(Rectangle.NO_BORDER);
        byte[] logo = reporteService.logoPng();
        if (logo != null) {
            Image img = Image.getInstance(logo);
            img.scaleToFit(90, 42);
            izq.addElement(img);
        } else {
            izq.addElement(new Paragraph("LibRed", F_NOMBRE));
        }
        t.addCell(izq);

        PdfPCell der = new PdfPCell();
        der.setBorder(Rectangle.NO_BORDER);
        Paragraph nombre = new Paragraph(lib == null ? "Librería" : texto(lib.getNombreComercial()), F_NOMBRE);
        nombre.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(nombre);
        if (lib != null) {
            String[] lineas = {
                    lib.getRuc() == null || lib.getRuc().isBlank() ? "" : "RUC " + lib.getRuc(),
                    texto(lib.getDireccion()),
                    lib.getTelefonoContacto() == null || lib.getTelefonoContacto().isBlank() ? "" : "Tel. " + lib.getTelefonoContacto()};
            for (String l : lineas) {
                if (!l.isBlank()) {
                    Paragraph p = new Paragraph(l, F_TEXTO);
                    p.setAlignment(Element.ALIGN_RIGHT);
                    der.addElement(p);
                }
            }
        }
        t.addCell(der);

        PdfPCell linea = new PdfPCell(new Phrase(" "));
        linea.setColspan(2);
        linea.setBorder(Rectangle.BOTTOM);
        linea.setBorderColor(GRAFITO);
        linea.setBorderWidth(1.5f);
        linea.setFixedHeight(6);
        t.addCell(linea);
        return t;
    }

    private PdfPCell celda(String texto, Font fuente, int alineacion, boolean total) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(6);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BORDE);
        if (total) {
            c.setBackgroundColor(FONDO);
            c.setBorderColor(GRAFITO);
        }
        return c;
    }

    private String nombreLector(Prestamo p) {
        return p.getLector() == null ? "" : texto(p.getLector().getNombres()) + " " + texto(p.getLector().getApellidos());
    }

    private String texto(String s) {
        return s == null ? "" : s;
    }

    private String fecha(LocalDate f) {
        return f == null ? "—" : f.format(FECHA);
    }

    private String hora(LocalDateTime f) {
        return f == null ? "—" : f.format(FECHA_HORA);
    }

    private String dinero(BigDecimal m) {
        return "S/ " + (m == null ? "0.00" : m.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }
}