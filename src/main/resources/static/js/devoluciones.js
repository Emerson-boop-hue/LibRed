// Devoluciones: la tabla es el punto de partida: clic en una fila = préstamo seleccionado.

var DIA_MS = 24 * 60 * 60 * 1000;

function dvEl(id) { return document.getElementById(id); }

function dvHoy() {
    var h = new Date();
    h.setHours(0, 0, 0, 0);
    return h;
}

// Días de retraso: positivo = vencido; 0 = vence hoy; negativo = faltan días
function dvDiasRetraso(fechaLimiteStr) {
    if (!fechaLimiteStr) return null;
    var limite = new Date(fechaLimiteStr + "T00:00:00");
    return Math.round((dvHoy() - limite) / DIA_MS);
}

function dvFecha(iso) {
    if (!iso) return "--";
    var p = iso.split("-");
    return p.length === 3 ? p[2] + "/" + p[1] + "/" + p[0] : iso;
}

function dvMoneda(n) { return "S/. " + n.toFixed(2); }

function dvNormalizar(t) {
    return (t || "").toString().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
}

function dvPlural(n, unidad) { return n + " " + unidad + (n === 1 ? "" : "s"); }

function iniciarDevoluciones() {
    var filas = document.querySelectorAll("#tbodyDevoluciones .dv-fila");
    var vencidos = 0;
    filas.forEach(function (fila) {
        var dias = dvDiasRetraso(fila.dataset.fechalimite);
        var estado = fila.querySelector("[data-estado-venc]");
        var fecha = fila.querySelector("[data-fecha-venc]");
        fila.dataset.vencido = (dias !== null && dias > 0) ? "1" : "0";
        if (fila.dataset.vencido === "1") vencidos++;

        if (dias === null) {
            estado.textContent = "--"; estado.className = "dv-estado";
        } else if (dias > 0) {
            estado.textContent = "Vencido hace " + dvPlural(dias, "día"); estado.className = "dv-estado vencido";
        } else if (dias === 0) {
            estado.textContent = "Vence hoy"; estado.className = "dv-estado hoy";
        } else {
            estado.textContent = "Vence en " + dvPlural(-dias, "día"); estado.className = "dv-estado aldia";
        }
        fecha.textContent = dvFecha(fila.dataset.fechalimite);
    });
    dvEl("kpiDevActivosValor").textContent = filas.length;
    dvEl("kpiDevVencidosValor").textContent = vencidos;
    registrarEventosDevoluciones();
    aplicarFiltrosDevoluciones();
}

function dvSoloVencidos() {
    var k = dvEl("kpiDevVencidos");
    return !!k && k.classList.contains("activo");
}

function dvMarcarKpis(soloVencidos) {
    var a = dvEl("kpiDevActivos");
    var v = dvEl("kpiDevVencidos");
    if (a) a.classList.toggle("activo", !soloVencidos);
    if (v) v.classList.toggle("activo", soloVencidos);
}

function dvLimpiarFiltros() {
    var b = dvEl("buscarDevolucion");
    if (b) b.value = "";
    dvMarcarKpis(false);
}

function aplicarFiltrosDevoluciones() {
    var texto = dvNormalizar((dvEl("buscarDevolucion") || {}).value).trim();
    var soloVencidos = dvSoloVencidos();
    var filas = document.querySelectorAll("#tbodyDevoluciones .dv-fila");
    var visibles = 0;

    filas.forEach(function (fila) {
        var coincide = true;
        if (soloVencidos && fila.dataset.vencido !== "1") coincide = false;
        if (coincide && texto) {
            var base = dvNormalizar([fila.dataset.lector, fila.dataset.codlector, fila.dataset.articulo, fila.dataset.codarticulo].join(" "));
            coincide = base.indexOf(texto) !== -1;
        }
        fila.hidden = !coincide;
        if (coincide) visibles++;
    });

    var sinRes = dvEl("filaSinResultadosDev");
    if (sinRes) sinRes.hidden = !(filas.length > 0 && visibles === 0);

    var boton = dvEl("btnLimpiarFiltrosDev");
    if (boton) boton.classList.toggle("inactivo", !(texto !== "" || soloVencidos));
}

// EVENTOS (una sola vez)
function registrarEventosDevoluciones() {
    if (window.__devolucionesListeners) return;
    window.__devolucionesListeners = true;

    document.addEventListener("input", function (e) {
        if (!e.target) return;
        if (e.target.id === "buscarDevolucion") aplicarFiltrosDevoluciones();
        if (e.target.id === "tarifaDiariaInput") actualizarComprobante();
    });

    document.addEventListener("click", function (e) {
        if (!e.target || !e.target.closest) return;

        var kpi = e.target.closest("#kpiDevActivos, #kpiDevVencidos");
        if (kpi) {
            if (kpi.id === "kpiDevActivos") dvLimpiarFiltros();
            else dvMarcarKpis(!dvSoloVencidos());
            aplicarFiltrosDevoluciones();
            return;
        }
        if (e.target.closest("#btnLimpiarFiltrosDev")) {
            dvLimpiarFiltros();
            aplicarFiltrosDevoluciones();
            return;
        }
        var fila = e.target.closest("#tbodyDevoluciones .dv-fila");
        if (fila) { seleccionarPrestamoFila(fila); return; }

        if (e.target.closest("#btnLimpiarDev")) { resetearDevolucion(); return; }
        if (e.target.closest("#btnProcesarDev")) { abrirConfirmacion(); return; }
        if (e.target.closest("#btnCerrarDevOk") || e.target.id === "dvModalOk") { var ok = dvEl("dvModalOk"); if (ok) ok.remove(); return; }
        if (e.target.closest("#btnCancelarConfirmacion") || e.target.id === "dvModalConfirmar") { cerrarConfirmacion(); return; }
        if (e.target.closest("#btnConfirmarDev")) { enviarDevolucion(); return; }
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") cerrarConfirmacion();
    });
}

var DEV = { fila: null, dias: 0, mora: 0, multa: 0, total: 0, tarifa: 0 };

// Multas PENDIENTES del préstamo (daño / pérdida), desde los <span> ocultos que renderiza Thymeleaf
function obtenerMultaPrestamo(prestamoId) {
    var total = 0;
    var motivos = [];
    document.querySelectorAll("#multasPendientesData span").forEach(function (s) {
        if (Number(s.dataset.prestamoid) === Number(prestamoId)) {
            var monto = parseFloat(s.dataset.monto);
            if (!isNaN(monto) && monto > 0) {
                total += monto;
                if (s.dataset.motivo) motivos.push(s.dataset.motivo);
            }
        }
    });
    return { total: total, motivos: motivos };
}

function seleccionarPrestamoFila(fila) {
    document.querySelectorAll("#tbodyDevoluciones .dv-fila.seleccionada").forEach(function (f) { f.classList.remove("seleccionada"); });
    fila.classList.add("seleccionada");
    DEV.fila = fila;
    dvEl("prestamoIdHidden").value = fila.dataset.id;
    actualizarComprobante();
}

function actualizarComprobante() {
    var fila = DEV.fila;
    var boton = dvEl("btnProcesarDev");
    if (!fila) { vaciarComprobante(); if (boton) boton.disabled = true; return; }

    var tarifa = parseFloat(dvEl("tarifaDiariaInput").value);
    if (isNaN(tarifa) || tarifa < 0) tarifa = 0;

    var dias = dvDiasRetraso(fila.dataset.fechalimite);
    if (dias === null || dias < 0) dias = 0;
    var mora = dias * tarifa;
    var multas = obtenerMultaPrestamo(fila.dataset.id);
    var multaTexto = multas.total > 0
        ? dvMoneda(multas.total) + (multas.motivos.length ? " (" + multas.motivos.join(", ") + ")" : "")
        : "Ninguna";

    DEV.dias = dias; DEV.mora = mora; DEV.multa = multas.total; DEV.tarifa = tarifa; DEV.total = mora + multas.total;
    DEV.multaTexto = multaTexto;

    dvEl("sumLector").textContent = fila.dataset.lector;
    dvEl("sumCodLector").textContent = fila.dataset.codlector || "--";
    dvEl("sumArticulo").textContent = fila.dataset.articulo;
    dvEl("sumCodArticulo").textContent = fila.dataset.codarticulo || "--";
    dvEl("sumFechaPrestamo").textContent = dvFecha(fila.dataset.fechaprestamo);
    dvEl("sumFechaLimite").textContent = dvFecha(fila.dataset.fechalimite);
    dvEl("sumDiasExcedidos").textContent = dvPlural(dias, "día");
    dvEl("sumMora").textContent = dvMoneda(mora);
    dvEl("sumMulta").textContent = multaTexto;
    dvEl("sumMontoTotal").textContent = dvMoneda(DEV.total);
    if (boton) boton.disabled = false;
}

function vaciarComprobante() {
    ["sumLector", "sumCodLector", "sumArticulo", "sumCodArticulo", "sumFechaPrestamo", "sumFechaLimite",
     "sumDiasExcedidos", "sumMora", "sumMulta", "sumMontoTotal"].forEach(function (id) { dvEl(id).textContent = "--"; });
}

function resetearDevolucion() {
    document.querySelectorAll("#tbodyDevoluciones .dv-fila.seleccionada").forEach(function (f) { f.classList.remove("seleccionada"); });
    DEV.fila = null;
    dvEl("prestamoIdHidden").value = "";
    dvEl("tarifaDiariaInput").value = "0.00";
    actualizarComprobante();
}

function abrirConfirmacion() {
    if (!DEV.fila) return;
    dvEl("cfLector").textContent = DEV.fila.dataset.lector;
    dvEl("cfArticulo").textContent = DEV.fila.dataset.articulo;
    dvEl("cfDias").textContent = dvPlural(DEV.dias, "día");
    dvEl("cfMora").textContent = dvMoneda(DEV.mora);
    dvEl("cfMulta").textContent = DEV.multaTexto;
    dvEl("cfTotal").textContent = dvMoneda(DEV.total);

    // Retraso con mora diaria en 0: es fácil olvidarla, así que se avisa
    var alerta = dvEl("cfAlerta");
    if (DEV.dias > 0 && DEV.tarifa === 0) {
        alerta.textContent = "Hay " + dvPlural(DEV.dias, "día") + " de retraso y la mora diaria está en S/. 0.00: no se cobrará mora.";
        alerta.hidden = false;
    } else {
        alerta.hidden = true;
    }
    dvEl("dvModalConfirmar").hidden = false;
}

function cerrarConfirmacion() {
    var m = dvEl("dvModalConfirmar");
    if (m) m.hidden = true;
}

function enviarDevolucion() {
    if (!dvEl("prestamoIdHidden").value) return;
    dvEl("btnConfirmarDev").disabled = true; // evita doble envío
    dvEl("formDevoluciones").onsubmit = null;
    dvEl("formDevoluciones").submit();
}

iniciarDevoluciones();