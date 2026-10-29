package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "libreria")
public class Libreria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "codigo_admin", nullable = false, unique = true, length = 50)
    private String codigoAdmin;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 255)
    private String direccion;

    @Column(name = "estado_suscripcion", length = 50)
    private String estadoSuscripcion = "NORMAL";

    // Plan contratado: MENSUAL o ANUAL (ver enum PlanSuscripcion)
    @Column(name = "tipo_plan", length = 20)
    private String tipoPlan = "MENSUAL";

    // Fecha en la que vence (se renueva) la membresía actual
    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    // Método que JPA ejecuta automáticamente antes de guardar el registro por primera vez
    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // Constructor vacío (Obligatorio para que JPA funcione)
    public Libreria() {
    }

    // --- GETTERS Y SETTERS ---
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getCodigoAdmin() {
        return codigoAdmin;
    }

    public void setCodigoAdmin(String codigoAdmin) {
        this.codigoAdmin = codigoAdmin;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getEstadoSuscripcion() {
        return estadoSuscripcion;
    }

    public void setEstadoSuscripcion(String estadoSuscripcion) {
        this.estadoSuscripcion = estadoSuscripcion;
    }

    public String getTipoPlan() {
        return tipoPlan;
    }

    public void setTipoPlan(String tipoPlan) {
        this.tipoPlan = tipoPlan;
    }

    public LocalDateTime getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDateTime fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    
    
    
}