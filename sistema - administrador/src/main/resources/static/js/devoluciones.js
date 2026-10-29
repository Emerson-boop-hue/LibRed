// Guarda una copia de TODAS las opciones de préstamos (para poder filtrarlas por lector).
// Es idempotente: si se llama dos veces sobre el mismo DOM no vuelve a capturar (evita guardar una lista ya filtrada).
function inicializarDevoluciones() {
    const prestamoSelect = document.getElementById("prestamoSelect");
    if (!prestamoSelect || prestamoSelect.dataset.devInit === "1") return;
    window.todasOpcionesPrestamosDev = Array.from(prestamoSelect.options);
    prestamoSelect.dataset.devInit = "1";
}

// El script se carga al final del body (carga normal) o después de insertar el contenido (navegación AJAX),
// así que el DOM ya existe en ambos casos.
inicializarDevoluciones();

// Lee las multas PENDIENTES del préstamo desde los <span> ocultos que renderiza Thymeleaf
function obtenerSancionPrestamo(prestamoId) {
    let total = 0;
    const motivos = [];
    document.querySelectorAll("#multasPendientesData span").forEach(s => {
        if (Number(s.dataset.prestamoid) === Number(prestamoId)) {
            const monto = parseFloat(s.dataset.monto);
            if (!isNaN(monto) && monto > 0) {
                total += monto;
                if (s.dataset.motivo) motivos.push(s.dataset.motivo);
            }
        }
    });
    return { total, motivos };
}

function filtrarPrestamosPorLector(limpiarComprobante = true) {
    const lectorId = document.getElementById("lectorSelect").value;
    const prestamoSelect = document.getElementById("prestamoSelect");
    const badge = document.getElementById("badgeEstadoDevolucion");

    prestamoSelect.innerHTML = '<option value="">-- Seleccionar préstamo activo --</option>';
    if (badge) badge.style.display = "none";

    if (limpiarComprobante) {
        document.getElementById("detalleResumen").style.display = "none";
        document.getElementById("subtituloComprobante").style.display = "block";
        document.getElementById("prestamoIdHidden").value = "";
    }

    if (!lectorId) return;

    if (window.todasOpcionesPrestamosDev) {
        window.todasOpcionesPrestamosDev.forEach(opt => {
            const optLectorId = opt.getAttribute("data-lectorid");
            if (optLectorId === lectorId) {
                prestamoSelect.appendChild(opt.cloneNode(true));
            }
        });
    }
}

function sincronizarPrestamoSeleccionado() {
    const prestamoSelect = document.getElementById("prestamoSelect");
    const prestamoId = prestamoSelect.value;
    document.getElementById("prestamoIdHidden").value = prestamoId;
    actualizarCalculoMora();
}

function actualizarCalculoMora() {
    const prestamoSelect = document.getElementById("prestamoSelect");
    const selectedOption = prestamoSelect.options[prestamoSelect.selectedIndex];
    const badge = document.getElementById("badgeEstadoDevolucion");
    const tarifaInput = document.getElementById("tarifaDiariaInput");

    if (!selectedOption || !selectedOption.value) {
        if (badge) badge.style.display = "none";
        document.getElementById("detalleResumen").style.display = "none";
        document.getElementById("subtituloComprobante").style.display = "block";
        document.getElementById("prestamoIdHidden").value = "";
        return;
    }

    const prestamoId = parseInt(selectedOption.value);
    document.getElementById("prestamoIdHidden").value = prestamoId;

    const lector = selectedOption.getAttribute("data-lector") || "--";
    const codLector = selectedOption.getAttribute("data-codlector") || "--";
    const articulo = selectedOption.getAttribute("data-articulo") || "--";
    const codArticulo = selectedOption.getAttribute("data-codarticulo") || "--";
    const fechaPrestamo = selectedOption.getAttribute("data-fechaprestamo") || "--";
    const fechaLimiteStr = selectedOption.getAttribute("data-fechalimite");

    let tarifa = parseFloat(tarifaInput.value);
    if (isNaN(tarifa) || tarifa < 0) tarifa = 0;

    let diasRetraso = 0;
    let montoMora = 0;

    if (fechaLimiteStr) {
        const fechaLimite = new Date(fechaLimiteStr + "T00:00:00");
        const hoy = new Date();
        hoy.setHours(0, 0, 0, 0);

        const difTiempo = hoy - fechaLimite;
        diasRetraso = Math.floor(difTiempo / (1000 * 60 * 60 * 24));
        if (diasRetraso < 0) diasRetraso = 0;
    }

    montoMora = diasRetraso * tarifa;

    // Actualización del Badge de Retraso en el formulario superior
    if (badge) {
        badge.style.display = "inline-block";
        if (diasRetraso > 0) {
            badge.innerText = `Con retraso: ${diasRetraso} día(s)`;
            badge.style.color = "#dc2626";
            badge.style.backgroundColor = "#fee2e2";
        } else {
            badge.innerText = "Al día sin retraso";
            badge.style.color = "#16a34a";
            badge.style.backgroundColor = "#dcfce7";
        }
    }

    // Multas pendientes previas (daño / pérdida) asociadas a este préstamo
    const sancion = obtenerSancionPrestamo(prestamoId);
    const montoMulta = sancion.total;
    const sancionTexto = montoMulta > 0
        ? "S/. " + montoMulta.toFixed(2) + (sancion.motivos.length ? " (" + sancion.motivos.join(", ") + ")" : "")
        : "NINGUNA";

    const totalPagar = montoMora + montoMulta;

    // Inyección de valores en la tarjeta de comprobante
    document.getElementById("sumLector").innerText = lector;
    document.getElementById("sumCodLector").innerText = codLector;
    document.getElementById("sumArticulo").innerText = articulo;
    document.getElementById("sumCodArticulo").innerText = codArticulo;
    document.getElementById("sumFechaPrestamo").innerText = fechaPrestamo;
    document.getElementById("sumFechaLimite").innerText = fechaLimiteStr || "--";
    document.getElementById("sumDiasExcedidos").innerText = `${diasRetraso} día(s)`;
    document.getElementById("sumMora").innerText = "S/. " + montoMora.toFixed(2);
    document.getElementById("sumSancion").innerText = sancionTexto;
    document.getElementById("sumMontoTotal").innerText = "S/. " + totalPagar.toFixed(2);

    document.getElementById("subtituloComprobante").style.display = "none";
    document.getElementById("detalleResumen").style.display = "flex";
}

function resetearFormularioDevoluciones() {
    document.getElementById("lectorSelect").value = "";
    document.getElementById("prestamoSelect").innerHTML = '<option value="">-- Seleccione un lector primero --</option>';
    document.getElementById("tarifaDiariaInput").value = "0.00";
    document.getElementById("prestamoIdHidden").value = "";

    const badge = document.getElementById("badgeEstadoDevolucion");
    if (badge) badge.style.display = "none";

    document.getElementById("detalleResumen").style.display = "none";
    document.getElementById("subtituloComprobante").style.display = "block";

    document.querySelectorAll(".fila-multa").forEach(f => f.style.backgroundColor = "");
}

function seleccionarPrestamoFila(fila) {
    document.querySelectorAll(".fila-multa").forEach(f => f.style.backgroundColor = "");
    fila.style.backgroundColor = "var(--sidebar-hover-bg)";

    const lectorId = fila.getAttribute("data-lectorid");
    const prestamoId = fila.getAttribute("data-id");

    if (lectorId) {
        const lectorSelect = document.getElementById("lectorSelect");
        lectorSelect.value = lectorId;
        
        filtrarPrestamosPorLector(false);

        const prestamoSelect = document.getElementById("prestamoSelect");
        if (prestamoSelect && prestamoId) {
            prestamoSelect.value = prestamoId;
            sincronizarPrestamoSeleccionado();
        }
    }
}

function enviarDevolucionForm() {
    const prestamoId = document.getElementById("prestamoIdHidden").value;
    if (!prestamoId) {
        alert("Por favor, selecciona un préstamo válido antes de procesar la devolución.");
        return;
    }
    document.getElementById("formDevoluciones").submit();
}