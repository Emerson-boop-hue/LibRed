document.addEventListener("DOMContentLoaded", () => {
    inicializarLectores();
});

if (document.readyState === "complete" || document.readyState === "interactive") {
    inicializarLectores();
}

function inicializarLectores() {
    registrarFiltrosLectores();
    actualizarAtajosFecha();

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

/* =====================================================================
   FILTROS DEL MÓDULO: buscador + estado (KPI "bloqueados") + rango de fechas (calendario)
   Se combinan entre sí. Al filtrar se reemplaza solo el bloque #lectoresResultado (resumen + tabla);
   los KPIs siguen mostrando el total de la librería.
   Los eventos son delegados y se registran una sola vez, aunque la página se vuelva a inicializar.
   ===================================================================== */
let peticionLectores = 0;

function registrarFiltrosLectores() {
    if (window.__filtrosLectoresListos) return;
    window.__filtrosLectoresListos = true;

    document.addEventListener("input", (e) => {
        if (e.target && e.target.id === "inputBuscarLector") {
            clearTimeout(window.searchLectorTimer);
            window.searchLectorTimer = setTimeout(aplicarFiltrosLectores, 300);
        }
    });

    document.addEventListener("change", (e) => {
        if (e.target && (e.target.id === "filtroDesde" || e.target.id === "filtroHasta")) {
            ajustarRangoFechas(e.target.id);
            aplicarFiltrosLectores();
        }
    });

    document.addEventListener("click", (e) => {
        if (!e.target || !e.target.closest) return;
        const objetivo = e.target.closest("#kpiTotal, #kpiBloqueados, .chip-fecha, #btnLimpiarFiltros");
        if (!objetivo) return;

        if (objetivo.id === "kpiTotal" || objetivo.id === "btnLimpiarFiltros") {
            limpiarFiltrosLectores();
        } else if (objetivo.id === "kpiBloqueados") {
            marcarKpis(estadoLectores() === "BLOQUEADO" ? "" : "BLOQUEADO");
        } else {
            aplicarAtajoFecha(objetivo.dataset.atajo);
        }
        aplicarFiltrosLectores();
    });
}

// --- Estado (KPI) ---
function estadoLectores() {
    const kpi = document.getElementById("kpiBloqueados");
    return kpi && kpi.classList.contains("activo") ? "BLOQUEADO" : "";
}

function marcarKpis(estado) {
    const total = document.getElementById("kpiTotal");
    const bloqueados = document.getElementById("kpiBloqueados");
    if (total) total.classList.toggle("activo", estado !== "BLOQUEADO");
    if (bloqueados) {
        bloqueados.classList.toggle("activo", estado === "BLOQUEADO");
        const pista = bloqueados.querySelector(".kpi-pista-lec");
        if (pista) {
            pista.textContent = estado === "BLOQUEADO" ? "Filtrando · clic para quitar" : "Clic para ver solo bloqueados";
        }
    }
}

function limpiarFiltrosLectores() {
    const buscar = document.getElementById("inputBuscarLector");
    const desde = document.getElementById("filtroDesde");
    const hasta = document.getElementById("filtroHasta");
    if (buscar) buscar.value = "";
    if (desde) desde.value = "";
    if (hasta) hasta.value = "";
    marcarKpis("");
}

// --- Fechas (formato ISO yyyy-MM-dd; "hoy" lo da el servidor en el atributo max, para usar su mismo reloj) ---
function fechaIso(f) {
    return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, "0")}-${String(f.getDate()).padStart(2, "0")}`;
}

function restarDias(iso, dias) {
    const [a, m, d] = iso.split("-").map(Number);
    const f = new Date(a, m - 1, d);
    f.setDate(f.getDate() - dias);
    return fechaIso(f);
}

function hoyLectores() {
    const desde = document.getElementById("filtroDesde");
    return desde && desde.max ? desde.max : fechaIso(new Date());
}

function rangoDeAtajo(atajo) {
    const hoy = hoyLectores();
    if (atajo === "hoy") return [hoy, hoy];
    if (atajo === "7dias") return [restarDias(hoy, 6), hoy];
    if (atajo === "mes") return [hoy.substring(0, 8) + "01", hoy];
    return ["", ""];
}

function actualizarAtajosFecha() {
    const desde = document.getElementById("filtroDesde");
    const hasta = document.getElementById("filtroHasta");
    const dv = desde ? desde.value : "";
    const hv = hasta ? hasta.value : "";
    document.querySelectorAll(".chip-fecha").forEach((boton) => {
        const [d, h] = rangoDeAtajo(boton.dataset.atajo);
        boton.classList.toggle("activo", dv !== "" && dv === d && hv === h);
    });
}

// Un atajo activo se vuelve a pulsar para quitar el rango
function aplicarAtajoFecha(atajo) {
    const desde = document.getElementById("filtroDesde");
    const hasta = document.getElementById("filtroHasta");
    if (!desde || !hasta) return;
    const [d, h] = rangoDeAtajo(atajo);
    const yaActivo = desde.value === d && hasta.value === h;
    desde.value = yaActivo ? "" : d;
    hasta.value = yaActivo ? "" : h;
}

// Sin fechas futuras y con "Desde" <= "Hasta": se corrige el campo que no se acaba de tocar
function ajustarRangoFechas(cambiado) {
    const desde = document.getElementById("filtroDesde");
    const hasta = document.getElementById("filtroHasta");
    if (!desde || !hasta) return;
    const hoy = hoyLectores();
    if (desde.value && desde.value > hoy) desde.value = hoy;
    if (hasta.value && hasta.value > hoy) hasta.value = hoy;
    if (desde.value && hasta.value && desde.value > hasta.value) {
        if (cambiado === "filtroDesde") hasta.value = desde.value;
        else desde.value = hasta.value;
    }
}

// --- Consulta al servidor y reemplazo del resultado ---
function aplicarFiltrosLectores() {
    const buscar = document.getElementById("inputBuscarLector");
    const desde = document.getElementById("filtroDesde");
    const hasta = document.getElementById("filtroHasta");

    const params = new URLSearchParams();
    const keyword = buscar ? buscar.value.trim() : "";
    const estado = estadoLectores();
    if (keyword) params.set("keyword", keyword);
    if (estado) params.set("estado", estado);
    if (desde && desde.value) params.set("desde", desde.value);
    if (hasta && hasta.value) params.set("hasta", hasta.value);

    const consulta = params.toString();
    const url = "/admin/lectores" + (consulta ? "?" + consulta : "");
    const turno = ++peticionLectores;

    actualizarAtajosFecha();

    fetch(url, { headers: { "X-Requested-With": "XMLHttpRequest" } })
        .then((respuesta) => respuesta.text())
        .then((html) => {
            if (turno !== peticionLectores) return; // llegó una respuesta más nueva: esta se descarta
            const doc = new DOMParser().parseFromString(html, "text/html");
            const nuevo = doc.getElementById("lectoresResultado");
            const actual = document.getElementById("lectoresResultado");
            if (nuevo && actual) {
                actual.replaceWith(nuevo);
                window.history.replaceState({}, "", url);
            }
        })
        .catch((err) => console.error("Error al filtrar lectores:", err));
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