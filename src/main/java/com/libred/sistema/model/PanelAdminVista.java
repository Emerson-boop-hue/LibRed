package com.libred.sistema.model;

import java.util.List;

/** Datos de solo lectura del PANEL DE CONTROL del administrador, ya listos para mostrarse. */
public final class PanelAdminVista {

    private PanelAdminVista() {
    }

    /** Un indicador de la fila superior. */
    public record Kpi(String titulo, String icono, String valor, String detalle, String tono) {
    }

    /** Una tarjeta de "Requiere tu atención". Si cantidad es 0 se muestra como "Al día" y sin color. */
    public record Atencion(String icono, String texto, long cantidad, String detalle, String url, String tono) {
        public boolean pendiente() {
            return cantidad > 0;
        }
    }

    /** Un día del gráfico: altoPrestamos / altoDevoluciones son porcentajes (0-100) de la columna más alta. */
    public record Dia(String etiqueta, int prestamos, int devoluciones, int altoPrestamos, int altoDevoluciones) {
    }

    /**
     * Un movimiento del historial. tipo = Préstamo, Pedido, Devolución o Multa; filtro = prestamo, pedido, devolucion
     * o multa (para los botones de filtro); quien = lector; detalle = artículo o monto; hora = "09:41".
     */
    public record Actividad(String icono, String tipo, String filtro, String quien, String detalle, String hora, String tono) {
    }

    /** Movimientos de un mismo día ("Hoy", "Ayer", "Mié 05/11"). */
    public record Grupo(String etiqueta, List<Actividad> movimientos) {
    }

    public record Pantalla(List<Kpi> kpis, List<Atencion> atencion, List<Dia> dias,
                           int totalPrestamos7, int totalDevoluciones7, int totalActividad, List<Grupo> actividad) {
    }
}