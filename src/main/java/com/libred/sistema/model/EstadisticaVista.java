package com.libred.sistema.model;

import java.util.List;

/** Datos del módulo ESTADÍSTICAS ya calculados. */
public final class EstadisticaVista {

    private EstadisticaVista() {
    }

    /** Un indicador de la fila superior. tendencia: */
    public record Kpi(String titulo, String icono, String valor, String anterior, String variacion, String tendencia) {
    }

    /**
     * desde / hasta en formato yyyy-MM-dd; periodo = texto legible; periodoAnterior = rango contra el que se compara.
     */
    public record Pantalla(String desde, String hasta, String periodo, String periodoAnterior,
                           List<Kpi> kpis, String datosJson) {
    }
}