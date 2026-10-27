package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lector")
public class Lector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "libreria_id", nullable = false)
    private Libreria libreria;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(name = "codigo_lector", nullable = false, unique = true, length = 50)
    private String codigoLector;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 20)
    private String dni;

    @Column(name = "estado_cuenta", length = 50)
    private String estadoCuenta = "ACTIVO";

    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    // Campos añadidos para la sección de Ajustes
    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String correo;

    @Column(name = "recibir_notificaciones")
    private Boolean recibirNotificaciones = true;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Libreria getLibreria() { return libreria; }
    public void setLibreria(Libreria libreria) { this.libreria = libreria; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getCodigoLector() { return codigoLector; }
    public void setCodigoLector(String codigoLector) { this.codigoLector = codigoLector; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getEstadoCuenta() { return estadoCuenta; }
    public void setEstadoCuenta(String estadoCuenta) { this.estadoCuenta = estadoCuenta; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public Boolean getRecibirNotificaciones() { return recibirNotificaciones; }
    public void setRecibirNotificaciones(Boolean recibirNotificaciones) { this.recibirNotificaciones = recibirNotificaciones; }
}
