package com.libred.sistema.service;

import com.libred.sistema.model.ReporteVista.Columna;
import com.libred.sistema.model.ReporteVista.Dato;
import com.libred.sistema.model.ReporteVista.Documento;
import com.libred.sistema.model.ReporteVista.Seccion;
import com.libred.sistema.model.ReporteVista.Tipo;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Convierte un informe de REPORTES en un libro de Excel (.xlsx): una hoja por sección de tabla, y una hoja "Resumen"
 * con los datos sueltos.
 */
@Service
public class ReporteExcelService {

    private static final String FORMATO_DINERO = "\"S/.\" #,##0.00";

    public byte[] generar(Documento doc, byte[] logo) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFColor grafito = new XSSFColor(new byte[]{(byte) 0x33, (byte) 0x41, (byte) 0x55}, null);
            XSSFColor claro = new XSSFColor(new byte[]{(byte) 0xe2, (byte) 0xe8, (byte) 0xf0}, null);

            XSSFFont negritaBlanca = libro.createFont();
            negritaBlanca.setBold(true);
            negritaBlanca.setColor(IndexedColors.WHITE.getIndex());
            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);
            XSSFFont grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);

            XSSFCellStyle estCabIzq = estilo(libro, negritaBlanca, grafito, HorizontalAlignment.LEFT, null);
            XSSFCellStyle estCabDer = estilo(libro, negritaBlanca, grafito, HorizontalAlignment.RIGHT, null);
            XSSFCellStyle estTitulo = estilo(libro, grande, null, HorizontalAlignment.LEFT, null);
            XSSFCellStyle estNegrita = estilo(libro, negrita, null, HorizontalAlignment.LEFT, null);
            XSSFCellStyle estTexto = estilo(libro, null, null, HorizontalAlignment.LEFT, null);
            XSSFCellStyle estNumero = estilo(libro, null, null, HorizontalAlignment.RIGHT, "#,##0");
            XSSFCellStyle estDinero = estilo(libro, null, null, HorizontalAlignment.RIGHT, FORMATO_DINERO);
            XSSFCellStyle estTotalTexto = estilo(libro, negrita, claro, HorizontalAlignment.LEFT, null);
            XSSFCellStyle estTotalNumero = estilo(libro, negrita, claro, HorizontalAlignment.RIGHT, "#,##0");
            XSSFCellStyle estTotalDinero = estilo(libro, negrita, claro, HorizontalAlignment.RIGHT, FORMATO_DINERO);

            int logoIdx = logo == null ? -1 : libro.addPicture(logo, Workbook.PICTURE_TYPE_PNG);

            // Hoja con los datos sueltos (si el informe tiene un resumen)
            for (Seccion s : doc.secciones()) {
                if (s.esResumen()) {
                    Sheet hoja = libro.createSheet(nombreHoja(libro, s.titulo()));
                    int fila = encabezado(hoja, libro, doc, logoIdx, estTitulo, estNegrita, estTexto);
                    for (Dato d : s.resumen()) {
                        Row r = hoja.createRow(fila++);
                        r.createCell(0).setCellValue(d.etiqueta());
                        r.getCell(0).setCellStyle(estNegrita);
                        escribir(r, 1, d.valor(), d.tipo(), estTexto, estNumero, estDinero);
                    }
                    hoja.setColumnWidth(0, 40 * 256);
                    hoja.setColumnWidth(1, 18 * 256);
                    ponerLogo(hoja, libro, logoIdx);
                }
            }

            // Una hoja por tabla
            for (Seccion s : doc.secciones()) {
                if (s.esResumen()) {
                    continue;
                }
                Sheet hoja = libro.createSheet(nombreHoja(libro, s.titulo()));
                int fila = encabezado(hoja, libro, doc, logoIdx, estTitulo, estNegrita, estTexto);
                List<Columna> cols = s.columnas();

                Row cab = hoja.createRow(fila++);
                for (int j = 0; j < cols.size(); j++) {
                    cab.createCell(j).setCellValue(cols.get(j).titulo());
                    cab.getCell(j).setCellStyle(cols.get(j).derecha() ? estCabDer : estCabIzq);
                }
                if (s.filas() == null || s.filas().isEmpty()) {
                    hoja.createRow(fila).createCell(0).setCellValue(s.vacio() == null ? "Sin datos." : s.vacio());
                } else {
                    int n = s.filas().size();
                    for (int i = 0; i < n; i++) {
                        boolean total = s.totalFinal() && i == n - 1;
                        Row r = hoja.createRow(fila++);
                        List<String> datos = s.filas().get(i);
                        for (int j = 0; j < cols.size(); j++) {
                            String valor = j < datos.size() ? datos.get(j) : "";
                            if (total) {
                                escribir(r, j, valor, cols.get(j).tipo(), estTotalTexto, estTotalNumero, estTotalDinero);
                            } else {
                                escribir(r, j, valor, cols.get(j).tipo(), estTexto, estNumero, estDinero);
                            }
                        }
                    }
                }
                // anchos según los datos de la tabla (no según el encabezado del documento)
                for (int j = 0; j < cols.size(); j++) {
                    int max = cols.get(j).titulo().length();
                    if (s.filas() != null) {
                        for (List<String> datos : s.filas()) {
                            if (j < datos.size() && datos.get(j) != null) {
                                max = Math.max(max, datos.get(j).length() + (cols.get(j).tipo() == Tipo.DINERO ? 5 : 0));
                            }
                        }
                    }
                    hoja.setColumnWidth(j, Math.max(10, Math.min(max + 3, 55)) * 256);
                }
                ponerLogo(hoja, libro, logoIdx);
            }

            libro.write(salida);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel.", e);
        }
    }

    /** Escribe logo, datos de la librería, título y periodo. Devuelve la primera fila libre. */
    private int encabezado(Sheet hoja, XSSFWorkbook libro, Documento doc, int logoIdx,
                           XSSFCellStyle estTitulo, XSSFCellStyle estNegrita, XSSFCellStyle estTexto) {
        int fila = 0;
        if (logoIdx >= 0) {
            fila = 4;   // las 4 primeras filas quedan libres para el logo
        }
        fila = linea(hoja, fila, doc.cabecera().nombre(), estTitulo);
        fila = linea(hoja, fila, doc.cabecera().ruc(), estTexto);
        fila = linea(hoja, fila, doc.cabecera().direccion(), estTexto);
        fila = linea(hoja, fila, doc.cabecera().contacto(), estTexto);
        fila++;
        fila = linea(hoja, fila, doc.titulo(), estNegrita);
        fila = linea(hoja, fila, doc.periodo() + "  ·  " + doc.generado(), estTexto);
        return fila + 1;
    }

    /** Coloca el logo en A1 con tamaño fijo (unos 180 x 80 px, sin deformarse). */
    private void ponerLogo(Sheet hoja, XSSFWorkbook libro, int logoIdx) {
        if (logoIdx < 0) {
            return;
        }
        final int anchoPx = 180, altoFilas = 4;
        int acumulado = 0, col = 0, dentro = 0;
        while (true) {
            int colPx = Math.max(1, Math.round(hoja.getColumnWidth(col) / 256f * 7 + 5));
            if (acumulado + colPx >= anchoPx) {
                dentro = anchoPx - acumulado;
                break;
            }
            acumulado += colPx;
            col++;
        }
        ClientAnchor ancla = libro.getCreationHelper().createClientAnchor();
        ancla.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
        ancla.setCol1(0);
        ancla.setRow1(0);
        ancla.setDx1(0);
        ancla.setDy1(0);
        ancla.setCol2(col);
        ancla.setDx2(dentro * 9525);       // 1 px = 9525 EMU
        ancla.setRow2(altoFilas);
        ancla.setDy2(0);
        hoja.createDrawingPatriarch().createPicture(ancla, logoIdx);
    }

    private int linea(Sheet hoja, int fila, String texto, XSSFCellStyle estilo) {
        if (texto == null || texto.isBlank()) {
            return fila;
        }
        Row r = hoja.getRow(fila) != null ? hoja.getRow(fila) : hoja.createRow(fila);
        r.createCell(0).setCellValue(texto);
        r.getCell(0).setCellStyle(estilo);
        return fila + 1;
    }

    private void escribir(Row fila, int col, String valor, Tipo tipo,
                          XSSFCellStyle estTexto, XSSFCellStyle estNumero, XSSFCellStyle estDinero) {
        var celda = fila.createCell(col);
        if (tipo != Tipo.TEXTO && valor != null && valor.matches("-?\\d+(\\.\\d+)?")) {
            celda.setCellValue(Double.parseDouble(valor));
            celda.setCellStyle(tipo == Tipo.DINERO ? estDinero : estNumero);
        } else {
            celda.setCellValue(valor == null ? "" : valor);
            celda.setCellStyle(estTexto);
        }
    }

    private XSSFCellStyle estilo(XSSFWorkbook libro, Font fuente, XSSFColor fondo, HorizontalAlignment alineacion, String formato) {
        XSSFCellStyle e = libro.createCellStyle();
        if (fuente != null) {
            e.setFont(fuente);
        }
        if (fondo != null) {
            e.setFillForegroundColor(fondo);
            e.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        e.setAlignment(alineacion);
        if (formato != null) {
            e.setDataFormat(libro.createDataFormat().getFormat(formato));
        }
        e.setBorderBottom(BorderStyle.THIN);
        e.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return e;
    }

    /** Nombres cortos y fijos para las pestañas (los títulos largos se cortarían). */
    private String corto(String titulo) {
        String t = titulo.toLowerCase();
        if (t.startsWith("los 10 artículos")) return "Más prestados";
        if (t.startsWith("artículos sin")) return "Sin movimiento";
        if (t.startsWith("los 10 lectores")) return "Más activos";
        if (t.startsWith("lectores bloqueados")) return "Bloqueados y deuda";
        if (t.startsWith("resumen")) return "Resumen";
        if (t.startsWith("por cobrar")) return "Por cobrar";
        return titulo;
    }

    /** Excel limita el nombre de hoja a 31 caracteres y no admite ciertos símbolos; además no puede repetirse. */
    private String nombreHoja(Workbook libro, String titulo) {
        String base = corto(titulo).replaceAll("[\\\\/?*\\[\\]:]", "-");
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String nombre = base;
        int n = 2;
        while (libro.getSheet(nombre) != null) {
            nombre = base + " " + n++;
        }
        return nombre;
    }
}