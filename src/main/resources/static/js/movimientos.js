var MV_ESTADOS = {
    prestamos: [["", "Todos los estados"], ["POR_ENTREGAR", "Por entregar"], ["ACTIVO", "Activos"], ["VENCIDO", "Vencidos"], ["DEVUELTO", "Devueltos"], ["CANCELADO", "Cancelados"]],
    devoluciones: null,
    multas: [["", "Todas"], ["PENDIENTE", "Pendientes"], ["PAGADA", "Pagadas"]]
};
var MV_TABS = ["prestamos", "devoluciones", "multas"];
var MV_NUMEROS = { prestamos: "numPrestamos", devoluciones: "numDevoluciones", multas: "numMultas" };

function mvEl(id) { return document.getElementById(id); }
function mvNormalizar(t) { return (t || "").toString().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, ""); }
function mvMoneda(n) { return "S/. " + n.toFixed(2); }

function mvPestanaActiva() {
    var t = document.querySelector(".mv-tab.activa");
    return t ? t.dataset.tab : "prestamos";
}

function iniciarMovimientos() {
    if (!mvEl("mvBuscar")) return;
    registrarEventosMovimientos();
    prepararEstado(mvPestanaActiva());
    aplicarFiltrosMovimientos();
}

// Rellena el desplegable de estado según la pestaña (Devoluciones no tiene estado)
function prepararEstado(tab) {
    var select = mvEl("mvEstado");
    var opciones = MV_ESTADOS[tab];
    select.innerHTML = "";
    if (!opciones) { select.hidden = true; return; }
    opciones.forEach(function (o) {
        var op = document.createElement("option");
        op.value = o[0]; op.textContent = o[1];
        select.appendChild(op);
    });
    select.hidden = false;
}

function cambiarPestana(tab) {
    document.querySelectorAll(".mv-tab").forEach(function (t) { t.classList.toggle("activa", t.dataset.tab === tab); });
    document.querySelectorAll(".mv-tabla-caja").forEach(function (p) { p.hidden = p.dataset.panel !== tab; });
    prepararEstado(tab);
    aplicarFiltrosMovimientos();
}

function filaCoincide(fila, texto, desde, hasta) {
    if (texto) {
        var base = mvNormalizar([fila.dataset.lector, fila.dataset.cod, fila.dataset.titulo, fila.dataset.codigo].join(" "));
        if (base.indexOf(texto) === -1) return false;
    }
    var dia = fila.dataset.dia || "";
    if (desde && (!dia || dia < desde)) return false;
    if (hasta && (!dia || dia > hasta)) return false;
    return true;
}

function aplicarFiltrosMovimientos() {
    var texto = mvNormalizar(mvEl("mvBuscar").value).trim();
    var desde = mvEl("mvDesde").value;
    var hasta = mvEl("mvHasta").value;
    var activa = mvPestanaActiva();
    var estado = mvEl("mvEstado").hidden ? "" : mvEl("mvEstado").value;

    MV_TABS.forEach(function (tab) {
        var panel = document.querySelector('.mv-tabla-caja[data-panel="' + tab + '"]');
        var filas = panel.querySelectorAll(".mv-fila");
        var conteo = 0;      // coincide con buscador + fechas (lo que muestra la pestaña)
        var visibles = 0;    // además coincide con el estado (solo en la pestaña activa)
        var suma = 0, sumaPendiente = 0;

        filas.forEach(function (fila) {
            var ok = filaCoincide(fila, texto, desde, hasta);
            if (ok) conteo++;
            if (tab === activa) {
                var mostrar = ok && (!estado || fila.dataset.estado === estado);
                fila.hidden = !mostrar;
                if (mostrar) {
                    visibles++;
                    var monto = parseFloat(fila.dataset.monto) || 0;
                    suma += monto;
                    if (fila.dataset.estado === "PENDIENTE") sumaPendiente += monto;
                }
            }
        });

        mvEl(MV_NUMEROS[tab]).textContent = conteo;

        if (tab === activa) {
            var sin = panel.querySelector(".mv-sin-resultados");
            if (sin) sin.hidden = !(filas.length > 0 && visibles === 0);
            mvEl("mvResumen").innerHTML = resumenDe(tab, visibles, suma, sumaPendiente);
        }
    });

    var hayFiltros = texto !== "" || desde !== "" || hasta !== "" || estado !== "";
    mvEl("mvLimpiar").classList.toggle("inactivo", !hayFiltros);
}

function resumenDe(tab, n, suma, pendiente) {
    if (tab === "prestamos") {
        return "<strong>" + n + "</strong> " + (n === 1 ? "préstamo" : "préstamos") + " · <strong>" + mvMoneda(suma) + "</strong> en servicio";
    }
    if (tab === "devoluciones") {
        return "<strong>" + n + "</strong> " + (n === 1 ? "devolución" : "devoluciones") + " · <strong>" + mvMoneda(suma) + "</strong> cobrados";
    }
    return "<strong>" + n + "</strong> " + (n === 1 ? "multa" : "multas") + " · <strong>" + mvMoneda(suma) + "</strong> emitidos"
        + (pendiente > 0 ? " (" + mvMoneda(pendiente) + " pendientes)" : "");
}

function limpiarFiltrosMovimientos() {
    mvEl("mvBuscar").value = "";
    mvEl("mvDesde").value = "";
    mvEl("mvHasta").value = "";
    mvEl("mvEstado").value = "";
}

// EVENTOS (una sola vez)
function registrarEventosMovimientos() {
    if (window.__movimientosListeners) return;
    window.__movimientosListeners = true;

    document.addEventListener("input", function (e) {
        if (e.target && e.target.id === "mvBuscar") aplicarFiltrosMovimientos();
    });
    document.addEventListener("change", function (e) {
        if (e.target && (e.target.id === "mvDesde" || e.target.id === "mvHasta" || e.target.id === "mvEstado")) aplicarFiltrosMovimientos();
    });
    document.addEventListener("click", function (e) {
        if (!e.target || !e.target.closest) return;
        var tab = e.target.closest(".mv-tab");
        if (tab) { cambiarPestana(tab.dataset.tab); return; }
        if (e.target.closest("#mvLimpiar")) { limpiarFiltrosMovimientos(); aplicarFiltrosMovimientos(); }
    });
}

iniciarMovimientos();