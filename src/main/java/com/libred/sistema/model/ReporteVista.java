package com.libred.sistema.model;

import java.util.List;

/** Un informe del módulo REPORTES ya armado y listo para mostrarse en pantalla, guardarse en PDF o en Excel. */
public final class ReporteVista {

    private ReporteVista() {
    }

    public enum Tipo { TEXTO, DINERO, NUMERO }

    public record Columna(String titulo, Tipo tipo) {
        public boolean derecha() {
            return tipo != Tipo.TEXTO;
        }
    }

    /** Un dato suelto del resumen (etiqueta + valor). */
    public record Dato(String etiqueta, String valor, Tipo tipo) {
        public String texto() {
            return mostrar(tipo, valor);
        }
    }

    /**
     * Una parte del informe: o es un resumen (lista de datos) o es una tabla (columnas + filas). totalFinal = la
     * última fila es un total (se resalta). vacio = mensaje si la tabla no tiene filas.
     */
    public record Seccion(String titulo, List<Dato> resumen, List<Columna> columnas, List<List<String>> filas,
                          boolean totalFinal, String vacio) {
        public boolean esResumen() {
            return resumen != null && !resumen.isEmpty();
        }

        public String texto(int columna, String valor) {
            return mostrar(columnas.get(columna).tipo(), valor);
        }
    }

    /** Datos de la librería que aparecen en el encabezado. */
    public record Cabecera(String nombre, String ruc, String direccion, String contacto) {
    }

    /**
     * desde / hasta en formato yyyy-MM-dd (para los filtros de la pantalla). archivo = nombre base de los archivos.
     */
    public record Documento(String tipo, String titulo, String desde, String hasta, String periodo, String generado,
                            Cabecera cabecera, List<Seccion> secciones, String archivo) {
    }

    /** DINERO se muestra como "S/. 12.50"; lo que no es un número (por ejemplo "—") se deja tal cual. */
    public static String mostrar(Tipo tipo, String valor) {
        if (valor == null) {
            return "";
        }
        if (tipo == Tipo.DINERO && valor.matches("-?\\d+(\\.\\d+)?")) {
            return "S/. " + valor;
        }
        return valor;
    }
}