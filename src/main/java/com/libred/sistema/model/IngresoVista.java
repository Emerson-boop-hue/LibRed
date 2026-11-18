package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de solo lectura para el módulo INGRESOS del administrador: todo el dinero cobrado en SU librería, ya listo
 * para mostrarse.
 */
public final class IngresoVista {

    private IngresoVista() {
    }

    /**
     * Un cobro. concepto = SERVICIO | MORA | MULTA. fecha = para mostrar (dd/MM/yyyy HH:mm); dia = para filtrar
     * (yyyy-MM-dd).
     */
    public record Fila(String fecha, String dia, String concepto, String conceptoTexto,
                       String lector, String codLector, String titulo, String codigo,
                       String detalle, BigDecimal monto) {
    }

    /** porCobrar = servicio de los pedidos que aún no se entregaron (dinero que todavía no entra a caja). */
    public record Pantalla(List<Fila> filas, BigDecimal porCobrar, int pedidosPorCobrar) {
    }
}