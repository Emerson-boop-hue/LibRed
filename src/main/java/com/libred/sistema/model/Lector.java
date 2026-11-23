package com.libred.sistema.model;
import jakarta.persistence.*;
import com.libred.sistema.util.TelefonoUtil;
import java.time.LocalDateTime;
/**
 * Lector
 * Entidad que representa un lector registrado en la librería, incluyendo información personal, estado de la cuenta y fecha de registro.
 * Contiene detalles sobre el código del lector, nombres, apellidos, teléfono, contraseña y
 * estado de la cuenta.
 */
@Entity
@Table(name = "lector",
        uniqueConstraints = @UniqueConstraint(name = "uk_lector_libreria_codigo", columnNames = {"libreria_id", "codigo_lector"}))
public class Lector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_lector", nullable = false, length = 50)
    private String codigoLector;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private String password;

    @Column(name = "estado_cuenta", length = 50)
    private String estadoCuenta = "HABILITADO";

    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "libreria_id")
    private Integer libreriaId;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = false;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    public Lector() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigoLector() { return codigoLector; }
    public void setCodigoLector(String codigoLector) { this.codigoLector = codigoLector; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getTelefonoFormateado() { return TelefonoUtil.formatear(telefono); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEstadoCuenta() { return estadoCuenta; }
    public void setEstadoCuenta(String estadoCuenta) { this.estadoCuenta = estadoCuenta; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public Integer getLibreriaId() { return libreriaId; }
    public void setLibreriaId(Integer libreriaId) { this.libreriaId = libreriaId; }

    public boolean isDebeCambiarPassword() { return debeCambiarPassword; }
    public void setDebeCambiarPassword(boolean debeCambiarPassword) { this.debeCambiarPassword = debeCambiarPassword; }
}