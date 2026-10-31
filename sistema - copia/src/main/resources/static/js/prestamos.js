// =====================================================================
// MÓDULO PRÉSTAMOS DEL ADMINISTRADOR
// Valida lector / artículo / plazo y calcula en vivo el comprobante:
// días = fecha límite - hoy, servicio = días x tarifa de la categoría del artículo.
// =====================================================================

// ---------- utilidades de fecha (siempre con la hora LOCAL, no UTC) ----------
function prFechaISO(fecha) {
    const y = fecha.getFullYear();
    const m = String(fecha.getMonth() + 1).padStart(2, "0");
    const d = String(fecha.getDate()).padStart(2, "0");
    return y + "-" + m + "-" + d;
}

function prSumarDias(dias) {
    const f = new Date();
    f.setHours(0, 0, 0, 0);
    f.setDate(f.getDate() + dias);
    return f;
}

function prMoneda(n) {
    return "S/ " + Number(n).toFixed(2);
}

// Días entre hoy y la fecha del input (YYYY-MM-DD)
function prDiasHasta(valor) {
    const p = valor.split("-");
    const limite = new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10));
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return Math.round((limite - hoy) / 86400000);
}

// Función centralizadora para inicializar los listeners de la vista de préstamos
function inicializarPrestamos() {
    const inputFecha = document.getElementById("inputFechaDevolucion");
    if (!inputFecha) return;

    // Se puede ejecutar varias veces (carga normal + navegación AJAX): solo se prepara una vez por pantalla
    if (inputFecha.dataset.listo === "1") return;
    inputFecha.dataset.listo = "1";

    // El calendario solo permite fechas dentro del plazo máximo de la librería
    const maxDias = parseInt(inputFecha.getAttribute("data-max-dias") || "7", 10);
    inputFecha.min = prFechaISO(prSumarDias(1));
    inputFecha.max = prFechaISO(prSumarDias(maxDias));
    inputFecha.value = prFechaISO(prSumarDias(Math.min(7, maxDias)));

    const selectLector = document.getElementById("selectLector");
    const selectRecurso = document.getElementById("selectRecurso");
    if (selectLector) selectLector.addEventListener("change", () => window.validarYActualizarTicket());
    if (selectRecurso) selectRecurso.addEventListener("change", () => window.validarYActualizarTicket());
    inputFecha.addEventListener("input", () => window.validarYActualizarTicket());

    window.validarYActualizarTicket();
}

// Auto-ejecución inmediata y segura ante cargas AJAX o F5
if (document.readyState === "complete" || document.readyState === "interactive") {
    setTimeout(inicializarPrestamos, 100);
} else {
    document.addEventListener("DOMContentLoaded", inicializarPrestamos);
}

// Fallback extra por si el layout AJAX inyecta el script de forma asíncrona
setTimeout(inicializarPrestamos, 300);

// =====================================================================
// FUNCIONES GLOBALES EXPUESTAS A WINDOW
// =====================================================================

window.validarYActualizarTicket = function () {
    const lectorSelect = document.getElementById("selectLector");
    const recursoSelect = document.getElementById("selectRecurso");
    const inputFecha = document.getElementById("inputFechaDevolucion");
    const alerta = document.getElementById("alertaValidacion");
    const btnSubmit = document.getElementById("btnSubmitPrestamo");
    if (!lectorSelect || !recursoSelect) return;

    document.querySelectorAll(".msg-alert").forEach((el) => (el.style.display = "none"));
    ocultarAlerta(alerta);

    const lectorOpt = lectorSelect.value ? lectorSelect.options[lectorSelect.selectedIndex] : null;
    const recursoOpt = recursoSelect.value ? recursoSelect.options[recursoSelect.selectedIndex] : null;

    // El comprobante se va llenando a medida que se eligen los datos
    pintarTicket(lectorOpt, recursoOpt, inputFecha);

    if (lectorOpt && lectorOpt.getAttribute("data-estado") === "SANCIONADO") {
        mostrarAlerta(alerta, "⛔ Restricción: El lector se encuentra SANCIONADO (multas pendientes o daños en artículos). Debe regularizar su situación antes de realizar un nuevo préstamo.");
        btnSubmit.disabled = true;
        return;
    }

    if (recursoOpt && parseInt(recursoOpt.getAttribute("data-stock") || "0", 10) <= 0) {
        mostrarAlerta(alerta, "⛔ Restricción de Inventario: El artículo seleccionado se encuentra AGOTADO.");
        btnSubmit.disabled = true;
        return;
    }

    // Plazo dentro de la política de la librería
    if (inputFecha && inputFecha.value) {
        const maxDias = parseInt(inputFecha.getAttribute("data-max-dias") || "7", 10);
        const dias = prDiasHasta(inputFecha.value);
        if (dias < 1) {
            mostrarAlerta(alerta, "⛔ La fecha límite debe ser posterior a hoy.");
            btnSubmit.disabled = true;
            return;
        }
        if (dias > maxDias) {
            mostrarAlerta(alerta, "⛔ El plazo máximo de préstamo en tu librería es de " + maxDias + (maxDias === 1 ? " día." : " días."));
            btnSubmit.disabled = true;
            return;
        }
    } else {
        btnSubmit.disabled = true;
        return;
    }

    btnSubmit.disabled = !(lectorOpt && recursoOpt);
};

window.limpiarFormulario = function () {
    const selectLector = document.getElementById("selectLector");
    const selectRecurso = document.getElementById("selectRecurso");
    if (selectLector) selectLector.value = "";
    if (selectRecurso) selectRecurso.value = "";

    const inputFecha = document.getElementById("inputFechaDevolucion");
    if (inputFecha) {
        const maxDias = parseInt(inputFecha.getAttribute("data-max-dias") || "7", 10);
        inputFecha.value = prFechaISO(prSumarDias(Math.min(7, maxDias)));
    }

    document.querySelectorAll(".msg-alert").forEach((el) => (el.style.display = "none"));
    window.validarYActualizarTicket();
};

// Se llama desde el atributo onchange del input de fecha
window.actualizarFechaLimiteTicket = function () {
    window.validarYActualizarTicket();
};

function texto(id, valor) {
    const el = document.getElementById(id);
    if (el) {
        el.innerText = valor;
        el.title = valor; // texto completo al pasar el mouse si se recorta
    }
}

function pintarTicket(lectorOpt, recursoOpt, inputFecha) {
    texto("resumenLectorNombre", lectorOpt ? lectorOpt.getAttribute("data-nombre") : "--");
    texto("resumenLectorCodigo", lectorOpt ? lectorOpt.getAttribute("data-codigo") : "--");
    texto("resumenArticuloTitulo", recursoOpt ? recursoOpt.getAttribute("data-titulo") : "--");
    texto("resumenArticuloCodigo", recursoOpt ? recursoOpt.getAttribute("data-codigo") : "--");
    texto("resumenCategoria", recursoOpt ? (recursoOpt.getAttribute("data-categoria") || "--") : "--");
    texto("resumenFechaPrestamo", new Date().toLocaleDateString("es-PE"));

    let dias = null;
    if (inputFecha && inputFecha.value) {
        const p = inputFecha.value.split("-");
        texto("resumenFechaLimite", p[2] + "/" + p[1] + "/" + p[0]);
        dias = prDiasHasta(inputFecha.value);
    } else {
        texto("resumenFechaLimite", "--");
    }

    const diasValidos = dias !== null && dias >= 1;
    texto("resumenDias", diasValidos ? dias + (dias === 1 ? " día" : " días") : "--");

    if (recursoOpt) {
        const tarifa = parseFloat(recursoOpt.getAttribute("data-tarifa") || "0") || 0;
        texto("resumenTarifa", prMoneda(tarifa));
        texto("resumenTotal", diasValidos ? prMoneda(tarifa * dias) : "--");
    } else {
        texto("resumenTarifa", "--");
        texto("resumenTotal", "--");
    }
}

function mostrarAlerta(elemento, mensaje) {
    if (elemento) {
        elemento.innerText = mensaje;
        elemento.style.display = "block";
    }
}

function ocultarAlerta(elemento) {
    if (elemento) {
        elemento.innerText = "";
        elemento.style.display = "none";
    }
}