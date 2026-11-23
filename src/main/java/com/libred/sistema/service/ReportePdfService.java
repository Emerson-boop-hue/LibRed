package com.libred.sistema.service;

import com.libred.sistema.model.ReporteVista.Columna;
import com.libred.sistema.model.ReporteVista.Dato;
import com.libred.sistema.model.ReporteVista.Documento;
import com.libred.sistema.model.ReporteVista.Seccion;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Convierte un informe de REPORTES en un PDF (A4 vertical) con el logo de LibRed y los datos de la librería. */
@Service
public class ReportePdfService {

    private static final Color GRAFITO = new Color(0x33, 0x41, 0x55);
    private static final Color GRAFITO_OSCURO = new Color(0x1e, 0x29, 0x3b);
    private static final Color FILA_ALTERNA = new Color(0xf1, 0xf5, 0xf9);
    private static final Color BORDE = new Color(0xcb, 0xd5, 0xe1);

    private static final Font F_NOMBRE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, GRAFITO_OSCURO);
    private static final Font F_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, GRAFITO_OSCURO);
    private static final Font F_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, GRAFITO);
    private static final Font F_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font F_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font F_CELDA_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
    private static final Font F_DATO_VALOR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, GRAFITO_OSCURO);
    private static final Font F_DATO_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    public byte[] generar(Documento doc, byte[] logo) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document pdf = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter escritor = PdfWriter.getInstance(pdf, salida);
            escritor.setPageEvent(pie(doc));
            pdf.open();

            pdf.add(encabezado(doc, logo));
            pdf.add(espacio(8));
            Paragraph titulo = new Paragraph(doc.titulo(), F_TITULO);
            pdf.add(titulo);
            pdf.add(new Paragraph(doc.periodo() + "  ·  " + doc.generado(), F_TEXTO));
            pdf.add(espacio(6));

            for (Seccion s : doc.secciones()) {
                Paragraph t = new Paragraph(s.titulo(), F_SECCION);
                t.setSpacingBefore(10);
                t.setSpacingAfter(4);
                pdf.add(t);
                if (s.esResumen()) {
                    pdf.add(resumen(s.resumen()));
                } else if (s.filas() == null || s.filas().isEmpty()) {
                    pdf.add(new Paragraph(s.vacio() == null ? "Sin datos." : s.vacio(), F_TEXTO));
                } else {
                    pdf.add(tabla(s));
                }
            }
            pdf.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF.", e);
        }
    }

    /** Pie de cada página: librería y fecha de generación a la izquierda, "Página N" a la derecha. */
    private PdfPageEventHelper pie(Documento doc) {
        return new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document pdf) {
                PdfContentByte cb = writer.getDirectContent();
                float y = pdf.bottom() - 16;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                        new Phrase(doc.cabecera().nombre() + " · " + doc.generado(), F_DATO_ETIQUETA), pdf.left(), y, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Página " + writer.getPageNumber(), F_DATO_ETIQUETA), pdf.right(), y, 0);
            }
        };
    }

    private PdfPTable encabezado(Documento doc, byte[] logo) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{1.2f, 2.8f});
        t.setWidthPercentage(100);

        PdfPCell izq = new PdfPCell();
        izq.setBorder(Rectangle.NO_BORDER);
        if (logo != null) {
            Image img = Image.getInstance(logo);
            img.scaleToFit(110, 50);
            izq.addElement(img);
        } else {
            izq.addElement(new Paragraph("LibRed", F_NOMBRE));
        }
        t.addCell(izq);

        PdfPCell der = new PdfPCell();
        der.setBorder(Rectangle.NO_BORDER);
        der.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph nombre = new Paragraph(doc.cabecera().nombre(), F_NOMBRE);
        nombre.setAlignment(Element.ALIGN_RIGHT);
        der.addElement(nombre);
        for (String linea : new String[]{doc.cabecera().ruc(), doc.cabecera().direccion(), doc.cabecera().contacto()}) {
            if (linea != null && !linea.isBlank()) {
                Paragraph p = new Paragraph(linea, F_TEXTO);
                p.setAlignment(Element.ALIGN_RIGHT);
                der.addElement(p);
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

    private PdfPTable resumen(List<Dato> datos) {
        int cols = Math.min(datos.size(), 3);
        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        for (Dato d : datos) {
            PdfPCell c = new PdfPCell();
            c.setBorderColor(BORDE);
            c.setPadding(7);
            c.setBackgroundColor(FILA_ALTERNA);
            c.addElement(new Paragraph(d.texto(), F_DATO_VALOR));
            c.addElement(new Paragraph(d.etiqueta(), F_DATO_ETIQUETA));
            t.addCell(c);
        }
        // rellena la última fila con celdas vacías SIN borde ni fondo
        int resto = datos.size() % cols;
        if (resto != 0) {
            for (int i = resto; i < cols; i++) {
                PdfPCell vacia = new PdfPCell(new Phrase(" "));
                vacia.setBorder(Rectangle.NO_BORDER);
                t.addCell(vacia);
            }
        }
        return t;
    }

    private PdfPTable tabla(Seccion s) throws Exception {
        List<Columna> columnas = s.columnas();
        PdfPTable t = new PdfPTable(columnas.size());
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setWidths(anchos(s));
        for (Columna c : columnas) {
            PdfPCell h = new PdfPCell(new Phrase(c.titulo(), F_CABECERA));
            h.setBackgroundColor(GRAFITO);
            h.setBorderColor(GRAFITO);
            h.setPadding(5);
            h.setHorizontalAlignment(c.derecha() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(h);
        }
        int n = s.filas().size();
        for (int i = 0; i < n; i++) {
            boolean total = s.totalFinal() && i == n - 1;
            List<String> fila = s.filas().get(i);
            for (int j = 0; j < columnas.size(); j++) {
                String valor = j < fila.size() ? s.texto(j, fila.get(j)) : "";
                PdfPCell c = new PdfPCell(new Phrase(valor, total ? F_CELDA_NEGRITA : F_CELDA));
                c.setBorderColor(BORDE);
                c.setPadding(4);
                c.setHorizontalAlignment(columnas.get(j).derecha() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                if (total) {
                    c.setBackgroundColor(new Color(0xe2, 0xe8, 0xf0));
                } else if (i % 2 == 1) {
                    c.setBackgroundColor(FILA_ALTERNA);
                }
                t.addCell(c);
            }
        }
        return t;
    }

    /**
     * Ancho de cada columna según lo más largo que lleva (con tope), para que los textos largos no se partan tanto.
     */
    private float[] anchos(Seccion s) {
        int n = s.columnas().size();
        float[] w = new float[n];
        for (int j = 0; j < n; j++) {
            int max = s.columnas().get(j).titulo().length();
            for (List<String> fila : s.filas()) {
                if (j < fila.size() && fila.get(j) != null) {
                    max = Math.max(max, s.texto(j, fila.get(j)).length());
                }
            }
            w[j] = Math.max(6, Math.min(max, 34)) + 3;
        }
        return w;
    }

    private Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(alto);
        return p;
    }
}