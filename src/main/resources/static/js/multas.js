// Multas: tabla de pendientes con buscador. Clic en una fila = comprobante a la derecha.

var MU = { fila: null };

function muEl(id) { return document.getElementById(id); }
function muMoneda(n) { return "S/. " + (parseFloat(n) || 0).toFixed(2); }
function muNormalizar(t) { return (t || "").toString().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, ""); }

function iniciarMultas() {
    var prestamoSelect = muEl("prestamoSelect");
    if (!prestamoSelect) return;
    // Copia de TODAS las opciones de préstamos (para filtrarlas por lector). Una vez por cada carga del DOM.
    if (prestamoSelect.dataset.muInit !== "1") {
        window.muOpcionesPrestamos = Array.from(prestamoSelect.options);
        prestamoSelect.dataset.muInit = "1";
    }
    MU.fila = null;
    registrarEventosMultas();
    validarFormularioMulta();
    filtrarMultas();
}

function filtrarMultas() {
    var texto = muNormalizar((muEl("buscarMulta") || {}).value).trim();
    var filas = document.querySelectorAll("#tbodyMultas .mu-fila");
    var visibles = 0;
    filas.forEach(function (f) {
        var base = muNormalizar([f.dataset.lector, f.dataset.codlector, f.dataset.articulo, f.dataset.codarticulo, f.dataset.motivo].join(" "));
        var coincide = !texto || base.indexOf(texto) !== -1;
        f.hidden = !coincide;
        if (coincide) visibles++;
    });
    var sin = muEl("filaSinResultadosMulta");
    if (sin) sin.hidden = !(filas.length > 0 && visibles === 0);
    var boton = muEl("btnLimpiarBusquedaMulta");
    if (boton) boton.classList.toggle("inactivo", texto === "");
}

// EVENTOS (una sola vez)
function registrarEventosMultas() {
    if (window.__multasListeners) return;
    window.__multasListeners = true;

    document.addEventListener("input", function (e) {
        if (!e.target) return;
        if (e.target.id === "buscarMulta") filtrarMultas();
        if (e.target.id === "montoInput") validarFormularioMulta();
    });

    document.addEventListener("change", function (e) {
        if (!e.target) return;
        if (e.target.id === "lectorSelect") { filtrarPrestamosPorLector(); validarFormularioMulta(); }
        if (e.target.id === "prestamoSelect") validarFormularioMulta();
    });

    document.addEventListener("click", function (e) {
        if (!e.target || !e.target.closest) return;

        if (e.target.closest("#btnLimpiarBusquedaMulta")) {
            var b = muEl("buscarMulta");
            if (b) b.value = "";
            filtrarMultas();
            return;
        }
        if (e.target.closest("#btnRegistrarMulta")) { abrirModalRegistrar(); return; }

        // Lápiz: selecciona la fila y abre la ventana en modo corrección
        var lapiz = e.target.closest("#tbodyMultas .mu-btn-editar");
        if (lapiz) {
            var filaLapiz = lapiz.closest(".mu-fila");
            seleccionarMulta(filaLapiz);
            abrirModalCorregir(filaLapiz);
            return;
        }
        var fila = e.target.closest("#tbodyMultas .mu-fila");
        if (fila) { seleccionarMulta(fila); return; }

        if (e.target.closest("#btnCancelarForm") || e.target.id === "muModalForm") { cerrarModalForm(); return; }
        if (e.target.closest("#btnCobrarMulta")) { abrirConfirmacionCobro(); return; }
        if (e.target.closest("#btnCancelarCobro") || e.target.id === "muModalCobro") { cerrarConfirmacionCobro(); return; }
        if (e.target.closest("#btnConfirmarCobro")) { confirmarCobro(); return; }
    });

    document.addEventListener("submit", function (e) {
        if (e.target && e.target.id === "formMulta" && !formularioMultaValido()) e.preventDefault();
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { cerrarModalForm(); cerrarConfirmacionCobro(); }
    });
}

function enModoEdicion() {
    return !!muEl("multaId").value;
}

function formularioMultaValido() {
    var monto = parseFloat(muEl("montoInput").value);
    if (isNaN(monto) || monto <= 0) return false;
    if (enModoEdicion()) return true;
    return muEl("lectorSelect").value !== "" && muEl("prestamoSelect").value !== "";
}

// "Guardar" solo se habilita con lector, préstamo y un monto mayor a 0
function validarFormularioMulta() {
    var boton = muEl("btnGuardarMulta");
    if (boton) boton.disabled = !formularioMultaValido();
}

function filtrarPrestamosPorLector() {
    var lectorId = muEl("lectorSelect").value;
    var prestamoSelect = muEl("prestamoSelect");
    prestamoSelect.innerHTML = lectorId
        ? '<option value="">-- Selecciona un préstamo --</option>'
        : '<option value="">-- Selecciona un lector primero --</option>';
    if (!lectorId || !window.muOpcionesPrestamos) return;
    window.muOpcionesPrestamos.forEach(function (opt) {
        if (opt.getAttribute("data-lectorid") === lectorId) prestamoSelect.appendChild(opt.cloneNode(true));
    });
}

// Registrando: se eligen lector y préstamo. Corrigiendo: ya están definidos (los deshabilitados no se envían ni se
// validan)
function ponerModoEdicion(activo, fila) {
    muEl("muEdicion").hidden = !activo;
    muEl("muSeleccion").hidden = activo;
    muEl("lectorSelect").disabled = activo;
    muEl("prestamoSelect").disabled = activo;
    muEl("muTituloForm").textContent = activo ? "Corregir multa" : "Registrar multa";
    muEl("btnGuardarMulta").textContent = activo ? "Guardar cambios" : "Guardar multa";
    if (activo && fila) {
        muEl("muEdicionLector").textContent = fila.dataset.lector + (fila.dataset.codlector ? " (" + fila.dataset.codlector + ")" : "");
        muEl("muEdicionArticulo").textContent = fila.dataset.articulo;
    }
}

function abrirModalRegistrar() {
    muEl("multaId").value = "";
    ponerModoEdicion(false);
    muEl("lectorSelect").value = "";
    filtrarPrestamosPorLector();
    document.querySelectorAll('#tipoDaño option[data-extra]').forEach(function (o) { o.remove(); });
    muEl("tipoDaño").value = "Daño Físico";
    muEl("montoInput").value = "0.00";
    validarFormularioMulta();
    muEl("muModalForm").hidden = false;
}

function abrirModalCorregir(fila) {
    muEl("multaId").value = fila.dataset.id;
    var motivo = fila.dataset.motivo || "";
    var select = muEl("tipoDaño");
    document.querySelectorAll('#tipoDaño option[data-extra]').forEach(function (o) { o.remove(); });
    var existe = Array.prototype.some.call(select.options, function (o) { return o.value === motivo; });
    if (!existe && motivo) {
        var extra = document.createElement("option");
        extra.value = motivo; extra.textContent = motivo; extra.dataset.extra = "1";
        select.appendChild(extra);
    }
    select.value = motivo;
    muEl("montoInput").value = (parseFloat(fila.dataset.monto) || 0).toFixed(2);
    ponerModoEdicion(true, fila);
    validarFormularioMulta();
    muEl("muModalForm").hidden = false;
}

function cerrarModalForm() {
    var m = muEl("muModalForm");
    if (m) m.hidden = true;
}

function seleccionarMulta(fila) {
    document.querySelectorAll("#tbodyMultas .mu-fila.seleccionada").forEach(function (f) { f.classList.remove("seleccionada"); });
    fila.classList.add("seleccionada");
    MU.fila = fila;

    muEl("sumLector").textContent = fila.dataset.lector;
    muEl("sumCodLector").textContent = fila.dataset.codlector || "--";
    muEl("sumArticulo").textContent = fila.dataset.articulo;
    muEl("sumCodArticulo").textContent = fila.dataset.codarticulo || "--";
    muEl("sumMotivo").textContent = fila.dataset.motivo || "--";
    muEl("sumEmision").textContent = fila.dataset.emision || "--";
    muEl("sumMonto").textContent = muMoneda(fila.dataset.monto);

    muEl("muSinSeleccion").hidden = true;
    muEl("muTicket").hidden = false;
    muEl("btnCobrarMulta").disabled = false;
}

function abrirConfirmacionCobro() {
    var fila = MU.fila;
    if (!fila) return;
    muEl("cbLector").textContent = fila.dataset.lector;
    muEl("cbArticulo").textContent = fila.dataset.articulo;
    muEl("cbMotivo").textContent = fila.dataset.motivo || "--";
    muEl("cbMonto").textContent = muMoneda(fila.dataset.monto);

    // ¿Le quedan otras multas pendientes a este lector? Mientras tenga alguna, sigue bloqueado.
    var otras = 0;
    document.querySelectorAll("#tbodyMultas .mu-fila").forEach(function (f) {
        if (f !== fila && f.dataset.lectorid && f.dataset.lectorid === fila.dataset.lectorid) otras++;
    });
    muEl("cbNota").textContent = otras === 0
        ? "Al registrar el pago, " + fila.dataset.lector + " volverá a estar habilitado."
        : fila.dataset.lector + " seguirá bloqueado: le quedan " + otras + (otras === 1 ? " multa pendiente." : " multas pendientes.");
    muEl("muModalCobro").hidden = false;
}

function cerrarConfirmacionCobro() {
    var m = muEl("muModalCobro");
    if (m) m.hidden = true;
}

function confirmarCobro() {
    if (!MU.fila) return;
    muEl("btnConfirmarCobro").disabled = true; // evita doble cobro
    window.location.href = "/admin/multas/pagar/" + MU.fila.dataset.id;
}

iniciarMultas();