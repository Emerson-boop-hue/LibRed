// Función centralizadora para inicializar los listeners de la vista de multas
function inicializarMultas() {
    const prestamoSelect = document.getElementById("prestamoSelect");
    if (prestamoSelect) {
        window.todasOpcionesPrestamos = Array.from(prestamoSelect.options);
    }
}

// Auto-ejecución inmediata y segura ante cargas AJAX o F5
if (document.readyState === "complete" || document.readyState === "interactive") {
    setTimeout(inicializarMultas, 100);
} else {
    document.addEventListener("DOMContentLoaded", inicializarMultas);
}

// Fallback por si el layout inyecta el script de forma asíncrona
setTimeout(inicializarMultas, 300);

function filtrarPrestamosPorLector() {
    const lectorId = document.getElementById("lectorSelect").value;
    const prestamoSelect = document.getElementById("prestamoSelect");

    prestamoSelect.innerHTML = '<option value="">-- Seleccionar préstamo activo --</option>';

    if (!lectorId) return;

    if (window.todasOpcionesPrestamos) {
        window.todasOpcionesPrestamos.forEach(opt => {
            const optLectorId = opt.getAttribute("data-lectorid");
            if (optLectorId === lectorId) {
                prestamoSelect.appendChild(opt.cloneNode(true));
            }
        });
    }
}

function cambiarMontoSegunMotivo() {
    const motivo = document.getElementById("tipoDaño").value;
    const montoInput = document.getElementById("montoInput");

    if (motivo === "Daño Físico") {
        montoInput.value = "25.00"; 
    } else if (motivo === "Artículo Perdido") {
        montoInput.value = "80.00"; 
    }
}

function resetearFormularioMultas() {
    const multaIdInput = document.getElementById("multaId");
    if (multaIdInput) multaIdInput.value = "";
    
    document.getElementById("lectorSelect").value = "";
    document.getElementById("prestamoSelect").innerHTML = '<option value="">-- Seleccione un lector primero --</option>';
    document.getElementById("tipoDaño").value = "Daño Físico";
    document.getElementById("montoInput").value = "25.00";

    document.getElementById("detalleResumen").style.display = "none";
    document.getElementById("subtituloComprobante").style.display = "block";

    document.querySelectorAll(".fila-multa").forEach(f => f.style.backgroundColor = "");
}

function seleccionarMulta(fila) {
    document.querySelectorAll(".fila-multa").forEach(f => f.style.backgroundColor = "");
    fila.style.backgroundColor = "var(--sidebar-hover-bg)";

    const id = fila.getAttribute("data-id");
    const lector = fila.getAttribute("data-lector");
    const codLector = fila.getAttribute("data-codlector");
    const articulo = fila.getAttribute("data-articulo");
    const codArticulo = fila.getAttribute("data-codarticulo");
    const fechaLimite = fila.getAttribute("data-fechalimite");
    
    const motivo = fila.getAttribute("data-motivo") || "";
    const monto = parseFloat(fila.getAttribute("data-monto") || 0);

    document.getElementById("sumLector").innerText = lector;
    document.getElementById("sumCodLector").innerText = codLector;
    document.getElementById("sumArticulo").innerText = articulo;
    document.getElementById("sumCodArticulo").innerText = codArticulo;
    document.getElementById("sumFechaLimite").innerText = fechaLimite ? fechaLimite : "No especificada";
    document.getElementById("sumMotivo").innerText = motivo;
    document.getElementById("sumMonto").innerText = "S/. " + monto.toFixed(2);

    const btnPagar = document.getElementById("btnProcesarPago");
    btnPagar.setAttribute("href", "/admin/multas/pagar/" + id);

    document.getElementById("subtituloComprobante").style.display = "none";
    document.getElementById("detalleResumen").style.display = "flex";
}