package com.libred.sistema.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Librería de la red y su suscripción. */
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

    // Datos de contacto editables desde Ajustes del administrador (opcionales)
    @Column(name = "telefono_contacto", length = 20)
    private String telefonoContacto;

    @Column(name = "correo_contacto", length = 120)
    private String correoContacto;

    // Perfil público: lo ven los lectores en Red Global (se edita en Ajustes del administrador, todo opcional)
    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "horario_atencion", length = 120)
    private String horarioAtencion;

    // Política de préstamos: máximo de días que un lector puede llevarse un artículo (se edita en Ajustes del
    // administrador)
    @Column(name = "max_dias_prestamo", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 7")
    private Integer maxDiasPrestamo = 7;

    // Máximo de préstamos activos por lector. NULL = sin límite (el admin decide en Ajustes si lo limita o no)
    @Column(name = "max_prestamos_activos", columnDefinition = "INT NULL DEFAULT 3")
    private Integer maxPrestamosActivos = 3;

    // Correo de la cuenta (el que se verificó con el código al registrarse). Sirve para recuperar el acceso y para
    // enviar comprobantes.
    @Column(name = "correo_cuenta", unique = true, length = 120)
    private String correoCuenta;

    @Column(name = "correo_verificado", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean correoVerificado = false;

    // Momento en que el administrador aceptó los términos y condiciones al registrarse
    @Column(name = "terminos_aceptados_en")
    private LocalDateTime terminosAceptadosEn;

    // Renovación de la membresía true = al vencer, LibRed cobra sola el siguiente periodo con el medio guardado (solo
    // tarjeta y PayPal).
    @Column(name = "renovacion_automatica", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean renovacionAutomatica = false;

    // Medio del banco de pruebas con el que se renueva sola (en un sistema real sería el "token" de la tarjeta)
    @Column(name = "medio_renovacion_id")
    private Integer medioRenovacionId;

    // Cobros automáticos rechazados seguidos; al llegar a 3 se desactiva la renovación automática
    @Column(name = "intentos_renovacion", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer intentosRenovacion = 0;

    // No se vuelve a intentar el cobro antes de esta fecha (se reintenta una vez al día)
    @Column(name = "proximo_intento_renovacion")
    private LocalDateTime proximoIntentoRenovacion;

    // Fecha de vencimiento para la que ya se envió el aviso "tu membresía está por vencer"
    @Column(name = "aviso_vencimiento_para")
    private LocalDateTime avisoVencimientoPara;

    // Método que JPA ejecuta automáticamente antes de guardar el registro por primera vez
    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // Constructor vacío (Obligatorio para que JPA funcione)
    public Libreria() {
    }

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

    public String getTelefonoContacto() {
        return telefonoContacto;
    }

    public void setTelefonoContacto(String telefonoContacto) {
        this.telefonoContacto = telefonoContacto;
    }

    public String getCorreoContacto() {
        return correoContacto;
    }

    public void setCorreoContacto(String correoContacto) {
        this.correoContacto = correoContacto;
    }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getHorarioAtencion() { return horarioAtencion; }
    public void setHorarioAtencion(String horarioAtencion) { this.horarioAtencion = horarioAtencion; }

    public Integer getMaxDiasPrestamo() {
        return maxDiasPrestamo == null ? 7 : maxDiasPrestamo;
    }

    public void setMaxDiasPrestamo(Integer maxDiasPrestamo) {
        this.maxDiasPrestamo = maxDiasPrestamo;
    }

    /** Máximo de préstamos activos por lector; null significa que no hay límite. */
    public Integer getMaxPrestamosActivos() {
        return maxPrestamosActivos;
    }

    public void setMaxPrestamosActivos(Integer maxPrestamosActivos) {
        this.maxPrestamosActivos = maxPrestamosActivos;
    }

    public String getCorreoCuenta() { return correoCuenta; }
    public void setCorreoCuenta(String correoCuenta) { this.correoCuenta = correoCuenta; }

    public boolean isCorreoVerificado() { return correoVerificado; }
    public void setCorreoVerificado(boolean correoVerificado) { this.correoVerificado = correoVerificado; }

    public LocalDateTime getTerminosAceptadosEn() { return terminosAceptadosEn; }
    public void setTerminosAceptadosEn(LocalDateTime terminosAceptadosEn) { this.terminosAceptadosEn = terminosAceptadosEn; }

    public boolean isRenovacionAutomatica() { return renovacionAutomatica; }
    public void setRenovacionAutomatica(boolean renovacionAutomatica) { this.renovacionAutomatica = renovacionAutomatica; }

    public Integer getMedioRenovacionId() { return medioRenovacionId; }
    public void setMedioRenovacionId(Integer medioRenovacionId) { this.medioRenovacionId = medioRenovacionId; }

    public int getIntentosRenovacion() { return intentosRenovacion == null ? 0 : intentosRenovacion; }
    public void setIntentosRenovacion(Integer intentosRenovacion) { this.intentosRenovacion = intentosRenovacion; }

    public LocalDateTime getProximoIntentoRenovacion() { return proximoIntentoRenovacion; }
    public void setProximoIntentoRenovacion(LocalDateTime proximoIntentoRenovacion) { this.proximoIntentoRenovacion = proximoIntentoRenovacion; }

    public LocalDateTime getAvisoVencimientoPara() { return avisoVencimientoPara; }
    public void setAvisoVencimientoPara(LocalDateTime avisoVencimientoPara) { this.avisoVencimientoPara = avisoVencimientoPara; }
}