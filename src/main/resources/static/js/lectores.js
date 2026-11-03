document.addEventListener("DOMContentLoaded", () => {
    inicializarLectores();
});

if (document.readyState === "complete" || document.readyState === "interactive") {
    inicializarLectores();
}

function inicializarLectores() {
    const inputBuscar = document.getElementById("inputBuscarLector");

    function ejecutarFiltroLector() {
        const keyword = inputBuscar ? inputBuscar.value.trim() : "";
        const url = `/admin/lectores?keyword=${encodeURIComponent(keyword)}`;

        fetch(url, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        })
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, "text/html");
            const nuevaTabla = doc.getElementById("tablaLectoresContenedor");
            
            if (nuevaTabla) {
                document.getElementById("tablaLectoresContenedor").replaceWith(nuevaTabla);
                window.history.pushState({}, "", url);
            }
        })
        .catch(err => console.error("Error al filtrar lectores:", err));
    }

    if (inputBuscar) {
        inputBuscar.removeEventListener("input", window.searchLectorHandler);
        window.searchLectorHandler = () => {
            clearTimeout(window.searchLectorTimer);
            window.searchLectorTimer = setTimeout(ejecutarFiltroLector, 300); 
        };
        inputBuscar.addEventListener("input", window.searchLectorHandler);
    }

    // MASCARA Y VALIDACIÓN ESTRICTA PARA EL TELÉFONO (Solo números, bloques de 3 en 3)
    const inputTelefono = document.getElementById("modalLectorTelefono");
    if (inputTelefono) {
        inputTelefono.addEventListener("input", function (e) {
            // Extraer solo dígitos numéricos (elimina letras y símbolos al instante)
            let valor = this.value.replace(/\D/g, "");
            
            // Limitar estrictamente a 9 dígitos
            if (valor.length > 9) {
                valor = valor.substring(0, 9);
            }

            // Aplicar formato en bloques de 3 en 3 (Ej: 987 654 321)
            let valorFormateado = "";
            if (valor.length > 6) {
                valorFormateado = `${valor.substring(0, 3)} ${valor.substring(3, 6)} ${valor.substring(6, 9)}`;
            } else if (valor.length > 3) {
                valorFormateado = `${valor.substring(0, 3)} ${valor.substring(3, 6)}`;
            } else {
                valorFormateado = valor;
            }

            this.value = valorFormateado;
        });
    }
}

// Funciones globales para modales
window.abrirModalCrearLector = function() {
    const modal = document.getElementById("modalLector");
    if (modal) {
        document.getElementById("modalLectorId").value = "";
        document.getElementById("modalLectorTituloLabel").innerText = "Registrar Nuevo Lector";
        document.getElementById("modalLectorNombres").value = "";
        document.getElementById("modalLectorApellidos").value = "";
        document.getElementById("modalLectorTelefono").value = "";
        configurarPasswordLector(true);

        cargarCodigoSugeridoLector();
        modal.style.display = "flex";
    }
};

window.abrirModalEditarLector = function(id, codigo, nombres, apellidos, telefono) {
    const modal = document.getElementById("modalLector");
    if (modal) {
        document.getElementById("modalLectorId").value = id;
        document.getElementById("modalLectorTituloLabel").innerText = "Modificar Lector";
        document.getElementById("modalLectorCodigo").value = codigo;
        document.getElementById("modalLectorNombres").value = nombres;
        document.getElementById("modalLectorApellidos").value = apellidos;
        
        // Formatear el teléfono existente si lo tuviera guardado en la BD
        let telLimpio = telefono ? telefono.replace(/\D/g, "") : "";
        if (telLimpio.length === 9) {
            document.getElementById("modalLectorTelefono").value = `${telLimpio.substring(0, 3)} ${telLimpio.substring(3, 6)} ${telLimpio.substring(6, 9)}`;
        } else {
            document.getElementById("modalLectorTelefono").value = telefono || "";
        }

        configurarPasswordLector(false);
        modal.style.display = "flex";
    }
};

// Contraseña: se escribe (y se puede ver con el ojito) solo al registrar. Al modificar queda bloqueada con puntos.
function configurarPasswordLector(esNuevo) {
    const input = document.getElementById("modalLectorPassword");
    const ojo = document.getElementById("modalLectorOjo");
    const ayuda = document.getElementById("modalLectorPasswordAyuda");
    if (!input) return;
    input.type = "password";
    document.getElementById("modalLectorOjoIcono").className = "fa-regular fa-eye";
    if (esNuevo) {
        input.disabled = false;
        input.required = true;
        input.value = "";
        input.style.opacity = "";
        ojo.style.display = "";
        ayuda.textContent = "Sin espacios. El lector podrá cambiarla luego desde Ajustes.";
    } else {
        input.disabled = true; // un campo deshabilitado no se envía: la contraseña existente no se toca
        input.required = false;
        input.value = "••••••••";
        input.style.opacity = "0.6";
        ojo.style.display = "none";
        ayuda.textContent = "Por seguridad, la contraseña no se puede ver ni modificar. El lector la cambia desde Ajustes.";
    }
}

window.alternarPasswordLector = function() {
    const input = document.getElementById("modalLectorPassword");
    if (!input || input.disabled) return;
    const mostrar = input.type === "password";
    input.type = mostrar ? "text" : "password";
    document.getElementById("modalLectorOjoIcono").className = mostrar ? "fa-regular fa-eye-slash" : "fa-regular fa-eye";
};

window.cerrarModalLector = function() {
    const modal = document.getElementById("modalLector");
    if (modal) {
        modal.style.display = "none";
    }
};

window.abrirModalEliminarLector = function(id, nombreCompleto, codigo) {
    const modal = document.getElementById("modalEliminarLector");
    if (modal) {
        document.getElementById("eliminarLectorNombre").innerText = nombreCompleto;
        document.getElementById("eliminarLectorCodigo").innerText = codigo;
        document.getElementById("btnConfirmarEliminarLector").href = `/admin/lectores/eliminar/${id}`;
        modal.style.display = "flex";
    }
};

window.cerrarModalEliminarLector = function() {
    const modal = document.getElementById("modalEliminarLector");
    if (modal) {
        modal.style.display = "none";
    }
};

function cargarCodigoSugeridoLector() {
    fetch(`/admin/lectores/codigo-sugerido`)
        .then(response => response.text())
        .then(codigo => {
            const inputCodigo = document.getElementById("modalLectorCodigo");
            if (inputCodigo) {
                inputCodigo.value = codigo;
            }
        })
        .catch(err => console.error("Error al obtener código sugerido de lector:", err));
}