package com.libred.sistema.model;

import java.util.List;

/** Datos dinámicos de la pantalla Ayuda y Soporte del administrador: sus solicitudes de soporte. */
public final class AyudaAdminVista {

    private AyudaAdminVista() {
    }

    /** Una solicitud de soporte ya lista para mostrarse. estado: ABIERTO / RESUELTO. */
    public record Ticket(String numero, String categoria, String asunto, String mensaje, String estado, String estadoTexto,
                         String fecha, String respuesta, String fechaRespuesta) {
        public boolean tieneRespuesta() {
            return respuesta != null && !respuesta.isBlank();
        }
    }

    /**
     * abiertas = solicitudes que todavía no tienen respuesta. codigoAdmin se usa en el mensaje que se prepara para el
     * chat de WhatsApp.
     */
    public record Pantalla(String nombre, String codigoAdmin, List<Ticket> tickets, long abiertas) {
    }
}