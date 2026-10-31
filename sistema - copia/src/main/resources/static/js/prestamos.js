// Función centralizadora para inicializar los listeners de la vista de préstamos
function inicializarPrestamos() {
    const inputFecha = document.getElementById("inputFechaDevolucion");
    if (inputFecha && !inputFecha.value) {
        const fechaLimite = new Date();
        fechaLimite.setDate(fechaLimite.getDate() + 7);
        inputFecha.value = fechaLimite.toISOString().split("T")[0];
        if (typeof window.actualizarFechaLimiteTicket === "function") {
            window.actualizarFechaLimiteTicket(inputFecha);
        }
    }

    const selectLector = document.getElementById("selectLector");
    const selectRecurso = document.getElementById("selectRecurso");

    if (selectLector) {
        selectLector.removeEventListener("change", window.prestamoLectorHandler);
        window.prestamoLectorHandler = () => window.validarYActualizarTicket();
        selectLector.addEventListener("change", window.prestamoLectorHandler);
    }

    if (selectRecurso) {
        selectRecurso.removeEventListener("change", window.prestamoRecursoHandler);
        window.prestamoRecursoHandler = () => window.validarYActualizarTicket();
        selectRecurso.addEventListener("change", window.prestamoRecursoHandler);
    }
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

window.validarYActualizarTicket = function() {
    const lectorSelect = document.getElementById("selectLector");
    const recursoSelect = document.getElementById("selectRecurso");
    const alerta = document.getElementById("alertaValidacion");
    const btnSubmit = document.getElementById("btnSubmitPrestamo");
    
    document.querySelectorAll('.msg-alert').forEach(el => el.style.display = 'none');
    ocultarAlerta(alerta);

    if (lectorSelect && lectorSelect.value) {
        const lectorOpt = lectorSelect.options[lectorSelect.selectedIndex];
        const estadoLector = lectorOpt.getAttribute("data-estado");
        
        if (estadoLector === "SANCIONADO") {
            mostrarAlerta(alerta, "⛔ Restricción: El lector se encuentra SANCIONADO (multas pendientes o daños en artículos). Debe regularizar su situación antes de realizar un nuevo préstamo.");
            if (btnSubmit) btnSubmit.disabled = true;
            limpiarTicketSoloTextos();
            return;
        }
    }

    if (recursoSelect && recursoSelect.value) {
        const recursoOpt = recursoSelect.options[recursoSelect.selectedIndex];
        const stockDisp = parseInt(recursoOpt.getAttribute("data-stock") || "0");
        
        if (stockDisp <= 0) {
            mostrarAlerta(alerta, "⛔ Restricción de Inventario: El artículo seleccionado se encuentra AGOTADO.");
            if (btnSubmit) btnSubmit.disabled = true;
            limpiarTicketSoloTextos();
            return;
        }
    }

    if (lectorSelect && lectorSelect.value && recursoSelect && recursoSelect.value) {
        const lectorOpt = lectorSelect.options[lectorSelect.selectedIndex];
        const recursoOpt = recursoSelect.options[recursoSelect.selectedIndex];
        
        if (btnSubmit) btnSubmit.disabled = false;
        actualizarTicketResumen(lectorOpt, recursoOpt);
    } else {
        if (btnSubmit) btnSubmit.disabled = true;
        limpiarTicketSoloTextos();
    }
};

window.limpiarFormulario = function() {
    const selectLector = document.getElementById("selectLector");
    const selectRecurso = document.getElementById("selectRecurso");
    if (selectLector) selectLector.value = "";
    if (selectRecurso) selectRecurso.value = "";
    
    const inputFecha = document.getElementById("inputFechaDevolucion");
    if (inputFecha) {
        const fechaLimite = new Date();
        fechaLimite.setDate(fechaLimite.getDate() + 7);
        inputFecha.value = fechaLimite.toISOString().split("T")[0];
        window.actualizarFechaLimiteTicket(inputFecha);
    }

    ocultarAlerta(document.getElementById("alertaValidacion"));
    document.querySelectorAll('.msg-alert').forEach(el => el.style.display = 'none');
    
    const btnSubmit = document.getElementById("btnSubmitPrestamo");
    if (btnSubmit) btnSubmit.disabled = true;
    
    limpiarTicketSoloTextos();
};

function actualizarTicketResumen(lectorOpt, recursoOpt) {
    document.getElementById("resumenLectorNombre").innerText = lectorOpt.getAttribute("data-nombre");
    document.getElementById("resumenLectorCodigo").innerText = lectorOpt.getAttribute("data-codigo");
    document.getElementById("resumenArticuloTitulo").innerText = recursoOpt.getAttribute("data-titulo");
    document.getElementById("resumenArticuloCodigo").innerText = recursoOpt.getAttribute("data-codigo");

    const hoy = new Date();
    document.getElementById("resumenFechaPrestamo").innerText = hoy.toLocaleDateString();
}

function limpiarTicketSoloTextos() {
    const elNombre = document.getElementById("resumenLectorNombre");
    const elCodLec = document.getElementById("resumenLectorCodigo");
    const elArt = document.getElementById("resumenArticuloTitulo");
    const elCodArt = document.getElementById("resumenArticuloCodigo");
    const elFPres = document.getElementById("resumenFechaPrestamo");

    if (elNombre) elNombre.innerText = "--";
    if (elCodLec) elCodLec.innerText = "--";
    if (elArt) elArt.innerText = "--";
    if (elCodArt) elCodArt.innerText = "--";
    if (elFPres) elFPres.innerText = "--";
}

window.actualizarFechaLimiteTicket = function(input) {
    if (input && input.value) {
        const partes = input.value.split("-");
        const fechaFormateada = `${partes[2]}/${partes[1]}/${partes[0]}`;
        const elFlimite = document.getElementById("resumenFechaLimite");
        if (elFlimite) elFlimite.innerText = fechaFormateada;
    }
};

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