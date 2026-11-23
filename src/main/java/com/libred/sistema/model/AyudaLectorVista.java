package com.libred.sistema.model;

import com.libred.sistema.service.PoliticaPrestamoService.FilaTarifa;

import java.util.List;

/** Datos que muestra la pantalla Ayuda y Soporte del lector: su librería actual y las reglas de préstamo de esa librería. */
public class AyudaLectorVista {

    private final String nombre;
    private final String direccion;
    private final String telefono;
    private final String correo;
    private final String horario;
    private final int maxDias;
    private final Integer maxActivos;     // null = sin límite
    private final List<FilaTarifa> tarifas; // solo las categorías activas de la librería

    public AyudaLectorVista(String nombre, String direccion, String telefono, String correo, String horario,
                            int maxDias, Integer maxActivos, List<FilaTarifa> tarifas) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
        this.correo = correo;
        this.horario = horario;
        this.maxDias = maxDias;
        this.maxActivos = maxActivos;
        this.tarifas = tarifas;
    }

    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getTelefono() { return telefono; }
    public String getCorreo() { return correo; }
    public String getHorario() { return horario; }
    public int getMaxDias() { return maxDias; }
    public Integer getMaxActivos() { return maxActivos; }
    public List<FilaTarifa> getTarifas() { return tarifas; }
    public boolean isLimitado() { return maxActivos != null; }
}