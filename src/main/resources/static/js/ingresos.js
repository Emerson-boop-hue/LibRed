var IG_CONCEPTOS = [["SERVICIO", "Servicio", "c-servicio"], ["MORA", "Mora", "c-mora"], ["MULTA", "Multas", "c-multa"]];

function igEl(id) { return document.getElementById(id); }
function igNormalizar(t) { return (t || "").toString().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, ""); }
function igMoneda(n) { return "S/. " + (n || 0).toFixed(2); }

// fechas (siempre como texto yyyy-mm-dd, sin zonas horarias)
function igIso(d) {
    return d.getFullYear() + "-" + ("0" + (d.getMonth() + 1)).slice(-2) + "-" + ("0" + d.getDate()).slice(-2);
}
function igDesdeIso(iso) {
    var p = iso.split("-");
    return new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10), 12, 0, 0);
}
function igSumarDias(iso, n) {
    var d = igDesdeIso(iso);
    d.setDate(d.getDate() + n);
    return igIso(d);
}
function igHoy() {
    var c = document.querySelector(".dashboard-container");
    return (c && c.dataset.hoy) || igIso(new Date());
}
function igLunes(iso) {
    var d = igDesdeIso(iso);
    var dif = (d.getDay() + 6) % 7;
    d.setDate(d.getDate() - dif);
    return igIso(d);
}
function igEtiquetaDia(iso) { var p = iso.split("-"); return p[2] + "/" + p[1]; }

function iniciarIngresos() {
    if (!igEl("igBuscar")) return;
    registrarEventosIngresos();
    calcularResumen();
    aplicarFiltrosIngresos();
}

// RESUMEN (no depende de los filtros)
function calcularResumen() {
    var hoy = igHoy(), lunes = igLunes(hoy), mes = hoy.slice(0, 8) + "01";
    var h = 0, s = 0, m = 0;
    var hoyConcepto = { SERVICIO: 0, MORA: 0, MULTA: 0 };
    document.querySelectorAll(".ig-fila").forEach(function (f) {
        var dia = f.dataset.dia, monto = parseFloat(f.dataset.monto) || 0;
        if (!dia || dia > hoy) return;
        if (dia === hoy) { h += monto; hoyConcepto[f.dataset.concepto] = (hoyConcepto[f.dataset.concepto] || 0) + monto; }
        if (dia >= lunes) s += monto;
        if (dia >= mes) m += monto;
    });
    igEl("kpiHoy").textContent = igMoneda(h);
    igEl("kpiSemana").textContent = igMoneda(s);
    igEl("kpiMes").textContent = igMoneda(m);
    igEl("kpiHoyDetalle").textContent = "Servicio " + igMoneda(hoyConcepto.SERVICIO)
        + " · Mora " + igMoneda(hoyConcepto.MORA) + " · Multas " + igMoneda(hoyConcepto.MULTA);
}

function aplicarPeriodo(periodo) {
    var hoy = igHoy();
    igEl("igDesde").value = periodo === "hoy" ? hoy : (periodo === "semana" ? igLunes(hoy) : hoy.slice(0, 8) + "01");
    igEl("igHasta").value = hoy;
    aplicarFiltrosIngresos();
}

function filaCoincide(fila, texto, desde, hasta, concepto) {
    if (concepto && fila.dataset.concepto !== concepto) return false;
    if (texto) {
        var base = igNormalizar([fila.dataset.lector, fila.dataset.cod, fila.dataset.titulo, fila.dataset.codigo].join(" "));
        if (base.indexOf(texto) === -1) return false;
    }
    var dia = fila.dataset.dia || "";
    if (desde && (!dia || dia < desde)) return false;
    if (hasta && (!dia || dia > hasta)) return false;
    return true;
}

function aplicarFiltrosIngresos() {
    var texto = igNormalizar(igEl("igBuscar").value).trim();
    var desde = igEl("igDesde").value;
    var hasta = igEl("igHasta").value;
    var concepto = igEl("igConcepto").value;

    var filas = document.querySelectorAll(".ig-fila");
    var visibles = [];
    var total = 0;
    var porConcepto = { SERVICIO: 0, MORA: 0, MULTA: 0 };

    filas.forEach(function (fila) {
        var ok = filaCoincide(fila, texto, desde, hasta, concepto);
        fila.hidden = !ok;
        if (ok) {
            var monto = parseFloat(fila.dataset.monto) || 0;
            visibles.push({ dia: fila.dataset.dia, concepto: fila.dataset.concepto, monto: monto });
            total += monto;
            porConcepto[fila.dataset.concepto] = (porConcepto[fila.dataset.concepto] || 0) + monto;
        }
    });

    var sin = igEl("igSinResultados");
    if (sin) sin.hidden = !(filas.length > 0 && visibles.length === 0);

    igEl("igTotal").textContent = igMoneda(total);
    igEl("igResumen").innerHTML = "<strong>" + visibles.length + "</strong> "
        + (visibles.length === 1 ? "cobro" : "cobros") + " · <strong>" + igMoneda(total) + "</strong>";
    igEl("igLimpiar").classList.toggle("inactivo", !(texto || desde || hasta || concepto));

    dibujarDesglose(porConcepto, total);
    dibujarGrafico(visibles, desde, hasta);
}

function dibujarDesglose(porConcepto, total) {
    var html = "";
    IG_CONCEPTOS.forEach(function (c) {
        var valor = porConcepto[c[0]] || 0;
        var pct = total > 0 ? Math.round(valor * 100 / total) : 0;
        html += '<div><div class="ig-linea-cab"><span>' + c[1] + " · " + pct + '%</span><strong>' + igMoneda(valor)
            + '</strong></div><div class="ig-barra"><i class="' + c[2] + '" style="width:' + pct + '%"></i></div></div>';
    });
    igEl("igDesglose").innerHTML = html;
}

// GRÁFICO DE INGRESOS (sin librerías)
// Muestra el mismo conjunto que las cifras de arriba: el rango elegido o, sin fechas, el historial completo (desde el
// primer cobro hasta hoy). Según el largo del rango se agrupa para que las barras se lean bien:
// hasta 45 días = por día · hasta 200 días = por semana (desde el lunes) · más = por mes.
var IG_MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

function igDiasEntre(a, b) {
    return Math.round((igDesdeIso(b) - igDesdeIso(a)) / 86400000) + 1;
}
function igMesDe(iso) { return iso.slice(0, 7); }
function igSiguienteMes(clave) {
    var p = clave.split("-"), a = parseInt(p[0], 10), m = parseInt(p[1], 10);
    if (m === 12) { a++; m = 1; } else { m++; }
    return a + "-" + ("0" + m).slice(-2);
}
function igEtiquetaMes(clave) {
    var p = clave.split("-");
    return IG_MESES[parseInt(p[1], 10) - 1] + " " + p[0].slice(2);
}
function igEtiquetaFecha(iso, conAnio) {
    var p = iso.split("-");
    return p[2] + "/" + p[1] + (conAnio ? "/" + p[0].slice(2) : "");
}

function dibujarGrafico(visibles, desde, hasta) {
    var hoy = igHoy();
    var fin = hasta || hoy;
    var inicio = desde;
    if (!inicio) {
        // Sin fecha inicial: desde el primer cobro (de lo que se está viendo)
        inicio = fin;
        visibles.forEach(function (v) { if (v.dia && v.dia < inicio) inicio = v.dia; });
    }
    if (inicio > fin) inicio = fin;

    var total = igDiasEntre(inicio, fin);
    var modo = total <= 45 ? "dia" : (total <= 200 ? "semana" : "mes");
    var conAnio = inicio.slice(0, 4) !== fin.slice(0, 4);

    // Barras (periodos) y función que dice a qué barra pertenece cada cobro
    var barras = [], porClave = {}, claveDe;
    if (modo === "dia") {
        for (var d = inicio; d <= fin; d = igSumarDias(d, 1)) {
            barras.push({ clave: d, etiqueta: igEtiquetaDia(d), titulo: igEtiquetaFecha(d, true) });
        }
        claveDe = function (dia) { return dia; };
    } else if (modo === "semana") {
        for (var l = igLunes(inicio); l <= fin; l = igSumarDias(l, 7)) {
            barras.push({ clave: l, etiqueta: igEtiquetaDia(l),
                titulo: "Semana del " + igEtiquetaFecha(l, true) + " al " + igEtiquetaFecha(igSumarDias(l, 6), true) });
        }
        claveDe = igLunes;
    } else {
        for (var m = igMesDe(inicio); m <= igMesDe(fin); m = igSiguienteMes(m)) {
            barras.push({ clave: m, etiqueta: igEtiquetaMes(m), titulo: igEtiquetaMes(m) });
        }
        claveDe = igMesDe;
    }
    barras.forEach(function (b) { b.SERVICIO = 0; b.MORA = 0; b.MULTA = 0; b.total = 0; porClave[b.clave] = b; });

    visibles.forEach(function (v) {
        var b = v.dia ? porClave[claveDe(v.dia)] : null;
        if (!b) return;
        b[v.concepto] = (b[v.concepto] || 0) + v.monto;
        b.total += v.monto;
    });

    var maximo = 0;
    barras.forEach(function (b) { if (b.total > maximo) maximo = b.total; });

    var paso = Math.max(1, Math.ceil(barras.length / 10));
    var html = "";
    barras.forEach(function (b, i) {
        var segmentos = "";
        // Se apila de abajo hacia arriba: servicio, mora, multas
        ["MULTA", "MORA", "SERVICIO"].forEach(function (k) {
            if (b[k] > 0 && maximo > 0) {
                segmentos += '<i class="c-' + k.toLowerCase() + '" style="height:' + (b[k] * 100 / maximo) + '%"></i>';
            }
        });
        var etiqueta = (i % paso === 0 || i === barras.length - 1) ? b.etiqueta : "";
        html += '<div class="ig-col" title="' + b.titulo + " · " + igMoneda(b.total) + '">'
            + '<div class="ig-pila">' + segmentos + "</div>"
            + '<span class="ig-col-etiqueta">' + etiqueta + "</span></div>";
    });
    igEl("igGrafico").innerHTML = html;
    igEl("igTituloGrafico").textContent = "Ingresos por " + (modo === "dia" ? "día" : (modo === "semana" ? "semana" : "mes"));
    igEl("igRangoGrafico").textContent = inicio === fin ? igEtiquetaFecha(inicio, conAnio)
        : igEtiquetaFecha(inicio, conAnio) + " – " + igEtiquetaFecha(fin, conAnio);
}

// EVENTOS (una sola vez)
function registrarEventosIngresos() {
    if (window.__ingresosListeners) return;
    window.__ingresosListeners = true;

    document.addEventListener("input", function (e) {
        if (e.target && e.target.id === "igBuscar") aplicarFiltrosIngresos();
    });
    document.addEventListener("change", function (e) {
        if (e.target && (e.target.id === "igDesde" || e.target.id === "igHasta" || e.target.id === "igConcepto")) aplicarFiltrosIngresos();
    });
    document.addEventListener("click", function (e) {
        if (!e.target || !e.target.closest) return;
        var kpi = e.target.closest(".ig-kpi[data-periodo]");
        if (kpi) { aplicarPeriodo(kpi.dataset.periodo); return; }
        if (e.target.closest("#igLimpiar")) {
            igEl("igBuscar").value = "";
            igEl("igDesde").value = "";
            igEl("igHasta").value = "";
            igEl("igConcepto").value = "";
            aplicarFiltrosIngresos();
        }
    });
}

iniciarIngresos();