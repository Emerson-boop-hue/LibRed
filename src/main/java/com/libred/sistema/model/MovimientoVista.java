package com.libred.sistema.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de solo lectura para el módulo MOVIMIENTOS del administrador: préstamos, devoluciones y multas de SU
 * librería, ya listos para mostrarse.
 */
public final class MovimientoVista {

    private MovimientoVista() {
    }

    /** fecha = para mostrar (dd/MM/yyyy HH:mm); dia = para filtrar (yyyy-MM-dd). */
    public record FilaPrestamo(Integer id, String fecha, String dia,
                               String lector, String codLector,
                               String titulo, String codigo,
                               String limite, String devuelto,
                               BigDecimal servicio, String origen,
                               String estado, String estadoTexto) {
    }

    public record FilaDevolucion(Integer id, String fecha, String dia,
                                 String lector, String codLector,
                                 String titulo, String codigo,
                                 int diasRetraso, BigDecimal mora,
                                 BigDecimal multa, BigDecimal total) {
    }

    public record FilaMulta(Long id, String fecha, String dia,
                            String lector, String codLector,
                            String titulo, String codigo,
                            String motivo, BigDecimal monto,
                            String estado, String estadoTexto, String fechaPago) {
    }

    public record Pantalla(List<FilaPrestamo> prestamos,
                           List<FilaDevolucion> devoluciones,
                           List<FilaMulta> multas) {
    }
}