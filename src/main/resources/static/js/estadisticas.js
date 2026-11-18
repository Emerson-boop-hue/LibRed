// Los colores viven en layout_admin.css (variables --chart-*) para que cambien con el modo oscuro
function esVar(nombre) { return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim(); }
function esPaleta() { var p = [], i; for (i = 1; i <= 7; i++) { p.push(esVar("--chart-" + i)); } return p; }
var ES_DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
var ES_NS = "http://www.w3.org/2000/svg";

function esEl(id) { return document.getElementById(id); }
function esMoneda(n) { return "S/. " + (n || 0).toFixed(2); }
function esEsc(t) {
    return String(t).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; });
}
function esMax(arr) { var m = 0; for (var i = 0; i < arr.length; i++) { if (arr[i] > m) { m = arr[i]; } } return m; }
// Tope "redondo" para el eje vertical
function esTope(max) {
    if (max <= 5) { return 5; }
    var p = Math.pow(10, Math.floor(Math.log(max) / Math.LN10)), f = max / p;
    var n = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
    return n * p;
}
function esVacio(caja, texto) { caja.innerHTML = '<div class="es-vacio">' + esEsc(texto) + "</div>"; }

function esIso(d) { return d.getFullYear() + "-" + ("0" + (d.getMonth() + 1)).slice(-2) + "-" + ("0" + d.getDate()).slice(-2); }
function esHoy() {
    var c = document.querySelector(".dashboard-container"), t = c && c.dataset.hoy;
    if (!t) { return new Date(); }
    var p = t.split("-");
    return new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10), 12, 0, 0);
}
function esRango(clave) {
    var hoy = esHoy(), desde = new Date(hoy.getTime()), hasta = new Date(hoy.getTime());
    if (clave === "7") { desde.setDate(hoy.getDate() - 6); }
    else if (clave === "mes") { desde.setDate(1); }
    else if (clave === "anterior") {
        desde = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1, 12, 0, 0);
        hasta = new Date(hoy.getFullYear(), hoy.getMonth(), 0, 12, 0, 0);
    } else { desde.setDate(hoy.getDate() - 29); }   // "30" (por defecto)
    return [esIso(desde), esIso(hasta)];
}
function esSincronizarPeriodo() {
    var sel = esEl("esPeriodo"), d = esEl("esDesde"), h = esEl("esHasta");
    if (!sel || !d || !h) { return; }
    var claves = ["7", "30", "mes", "anterior"], valor = "personalizado";
    for (var i = 0; i < claves.length; i++) {
        var r = esRango(claves[i]);
        if (r[0] === d.value && r[1] === h.value) { valor = claves[i]; break; }
    }
    sel.value = valor;
}

function esTip() {
    var t = esEl("esTip");
    if (!t) { t = document.createElement("div"); t.id = "esTip"; t.className = "es-tip"; document.body.appendChild(t); }
    return t;
}

// gráfico de líneas: préstamos y devoluciones
function esDibujarLinea(d) {
    var caja = esEl("esLinea");
    esEl("esLineaAgrupa").textContent = "por " + d.agrupacion;
    if (esMax(d.prestamos) + esMax(d.devoluciones) === 0) { esVacio(caja, "No hubo préstamos ni devoluciones en este periodo."); return; }
    var W = 640, H = 230, pl = 34, pr = 12, pt = 12, pb = 26, n = d.etiquetas.length;
    var tope = esTope(Math.max(esMax(d.prestamos), esMax(d.devoluciones)));
    function x(i) { return n === 1 ? pl + (W - pl - pr) / 2 : pl + i * (W - pl - pr) / (n - 1); }
    function y(v) { return pt + (H - pt - pb) * (1 - v / tope); }
    var s = '<svg viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="Préstamos y devoluciones">';
    for (var g = 0; g <= 5; g++) {
        var gv = tope * g / 5;
        s += '<line class="es-rejilla" x1="' + pl + '" x2="' + (W - pr) + '" y1="' + y(gv) + '" y2="' + y(gv) + '"/>';
        s += '<text class="es-eje" x="' + (pl - 6) + '" y="' + (y(gv) + 3) + '" text-anchor="end">' + Math.round(gv * 10) / 10 + "</text>";
    }
    var cada = Math.max(1, Math.ceil(n / 9));
    for (var i = 0; i < n; i += cada) {
        s += '<text class="es-eje" x="' + x(i) + '" y="' + (H - 8) + '" text-anchor="middle">' + esEsc(d.etiquetas[i]) + "</text>";
    }
    function serie(valores, color, relleno) {
        var pts = "", i;
        for (i = 0; i < n; i++) { pts += (i ? " " : "") + x(i) + "," + y(valores[i]); }
        var out = "";
        if (relleno && n > 1) {
            out += '<polygon points="' + x(0) + "," + y(0) + " " + pts + " " + x(n - 1) + "," + y(0) + '" fill="' + color + '" opacity="0.08"/>';
        }
        out += '<polyline fill="none" stroke="' + color + '" stroke-width="2.2" stroke-linejoin="round" points="' + pts + '"/>';
        if (n <= 31) {
            for (i = 0; i < n; i++) { out += '<circle cx="' + x(i) + '" cy="' + y(valores[i]) + '" r="2.8" fill="' + color + '"/>'; }
        }
        return out;
    }
    s += serie(d.prestamos, esVar("--chart-prest"), true) + serie(d.devoluciones, esVar("--chart-dev"), false);
    // zonas invisibles para el tooltip (una por punto del eje)
    var ancho = n === 1 ? 60 : (W - pl - pr) / (n - 1);
    for (var k = 0; k < n; k++) {
        var tip = d.detalle[k] + "<br>Préstamos: <b>" + d.prestamos[k] + "</b><br>Devoluciones: <b>" + d.devoluciones[k] + "</b>";
        s += '<rect class="es-punto" x="' + (x(k) - ancho / 2) + '" y="' + pt + '" width="' + ancho + '" height="' + (H - pt - pb) + '" fill="transparent" data-tip="' + esEsc(tip) + '"/>';
    }
    caja.innerHTML = s + "</svg>";
}

function esDibujarIngresos(d) {
    var caja = esEl("esIng");
    esEl("esIngAgrupa").textContent = "por " + d.agrupacion;
    var n = d.etiquetas.length, tot = [], i;
    for (i = 0; i < n; i++) { tot.push(d.servicio[i] + d.mora[i] + d.multas[i]); }
    if (esMax(tot) === 0) { esVacio(caja, "No hubo cobros en este periodo."); return; }
    var W = 640, H = 220, pl = 42, pr = 12, pt = 12, pb = 26, tope = esTope(esMax(tot));
    var zona = (W - pl - pr) / n, bw = Math.max(3, Math.min(34, zona * 0.7));
    function y(v) { return pt + (H - pt - pb) * (1 - v / tope); }
    var s = '<svg viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="Ingresos por día">';
    for (var g = 0; g <= 5; g++) {
        var gv = tope * g / 5;
        s += '<line class="es-rejilla" x1="' + pl + '" x2="' + (W - pr) + '" y1="' + y(gv) + '" y2="' + y(gv) + '"/>';
        s += '<text class="es-eje" x="' + (pl - 6) + '" y="' + (y(gv) + 3) + '" text-anchor="end">' + Math.round(gv * 10) / 10 + "</text>";
    }
    var cada = Math.max(1, Math.ceil(n / 9));
    for (i = 0; i < n; i++) {
        var cx = pl + zona * i + zona / 2, base = 0, tip = d.detalle[i] + "<br>Servicio: <b>" + esMoneda(d.servicio[i]) + "</b><br>Mora: <b>" + esMoneda(d.mora[i]) + "</b><br>Multas: <b>" + esMoneda(d.multas[i]) + "</b><br>Total: <b>" + esMoneda(tot[i]) + "</b>";
        var partes = [[d.servicio[i], esVar("--chart-serv")], [d.mora[i], esVar("--chart-mora")], [d.multas[i], esVar("--chart-multa")]];
        s += '<g class="es-barra-v" data-tip="' + esEsc(tip) + '"><rect x="' + (cx - zona / 2) + '" y="' + pt + '" width="' + zona + '" height="' + (H - pt - pb) + '" fill="transparent"/>';
        for (var p = 0; p < 3; p++) {
            if (partes[p][0] > 0) {
                s += '<rect x="' + (cx - bw / 2) + '" y="' + y(base + partes[p][0]) + '" width="' + bw + '" height="' + (y(base) - y(base + partes[p][0])) + '" fill="' + partes[p][1] + '"/>';
                base += partes[p][0];
            }
        }
        s += "</g>";
        if (i % cada === 0) { s += '<text class="es-eje" x="' + cx + '" y="' + (H - 8) + '" text-anchor="middle">' + esEsc(d.etiquetas[i]) + "</text>"; }
    }
    caja.innerHTML = s + "</svg>";
}

function esDibujarDona(caja, items, colores, textoCentro, vacio) {
    var total = 0, i;
    for (i = 0; i < items.length; i++) { total += items[i].valor; }
    if (total === 0) { esVacio(caja, vacio); return; }
    var R = 52, C = 2 * Math.PI * R, acum = 0;
    var s = '<svg class="es-dona" viewBox="0 0 150 150" role="img">';
    s += '<circle cx="75" cy="75" r="' + R + '" fill="none" stroke="' + esVar("--surface-icon") + '" stroke-width="22"/>';
    for (i = 0; i < items.length; i++) {
        if (items[i].valor <= 0) { continue; }
        var largo = C * items[i].valor / total, pct = Math.round(items[i].valor * 100 / total);
        s += '<circle cx="75" cy="75" r="' + R + '" fill="none" stroke="' + colores[i % colores.length] + '" stroke-width="22" stroke-dasharray="' + largo + " " + (C - largo) + '" stroke-dashoffset="' + (-acum) + '" transform="rotate(-90 75 75)" data-tip="' + esEsc(items[i].nombre + ": <b>" + items[i].valor + "</b> (" + pct + " %)") + '"/>';
        acum += largo;
    }
    s += '<text class="es-dona-total" x="75" y="77" text-anchor="middle">' + total + '</text><text class="es-dona-sub" x="75" y="90" text-anchor="middle">' + esEsc(textoCentro) + "</text></svg>";
    var l = '<div class="es-dona-leyenda">';
    for (i = 0; i < items.length; i++) {
        l += '<span><i style="background:' + colores[i % colores.length] + '"></i>' + esEsc(items[i].nombre) + "<b>" + items[i].valor + "</b></span>";
    }
    caja.innerHTML = s + l + "</div>";
}

function esDibujarTop(lista) {
    var caja = esEl("esTop");
    if (!lista.length) { esVacio(caja, "No hubo préstamos en este periodo."); return; }
    var max = lista[0].valor, h = "";
    for (var i = 0; i < lista.length; i++) {
        h += '<div class="es-fila" data-tip="' + esEsc(lista[i].nombre + " (" + lista[i].codigo + "): <b>" + lista[i].valor + "</b>") + '"><span class="es-fila-nombre">' + esEsc(lista[i].nombre) + "<small>" + esEsc(lista[i].codigo) + '</small></span><span class="es-pista"><i style="width:' + (lista[i].valor * 100 / max) + '%"></i></span><span class="es-fila-valor">' + lista[i].valor + "</span></div>";
    }
    caja.innerHTML = h;
}
function esDibujarCalif(v) {
    var caja = esEl("esCalif"), total = 0, suma = 0, i;
    for (i = 0; i < 5; i++) { total += v[i]; suma += v[i] * (i + 1); }
    esEl("esCalifResumen").textContent = total ? (suma / total).toFixed(1) + " ★ · " + total + (total === 1 ? " voto" : " votos") : "";
    caja.classList.add("es-estrellas");
    if (!total) { esVacio(caja, "Sin calificaciones en este periodo."); return; }
    var max = esMax(v), h = "";
    for (i = 4; i >= 0; i--) {
        h += '<div class="es-fila" data-tip="' + (i + 1) + " ★: <b>" + v[i] + '</b>"><span>' + (i + 1) + ' ★</span><span class="es-pista"><i style="width:' + (v[i] * 100 / max) + '%"></i></span><span class="es-fila-valor">' + v[i] + "</span></div>";
    }
    caja.innerHTML = h;
}

// mapa de calor (día x hora)
function esDibujarHorarios(m) {
    var caja = esEl("esHeat"), max = 0, lo = 8, hi = 20, d, h;
    for (d = 0; d < 7; d++) { for (h = 0; h < 24; h++) { if (m[d][h] > 0) { if (m[d][h] > max) { max = m[d][h]; } if (h < lo) { lo = h; } if (h > hi) { hi = h; } } } }
    if (!max) { esVacio(caja, "No hubo préstamos en este periodo."); return; }
    var cols = hi - lo + 1, html = '<div class="es-heat-tabla" style="grid-template-columns: 78px repeat(' + cols + ', minmax(26px, 1fr))"><span></span>';
    for (h = lo; h <= hi; h++) { html += '<span class="es-heat-hora">' + h + "h</span>"; }
    for (d = 0; d < 7; d++) {
        html += '<span class="es-heat-dia">' + ES_DIAS[d] + "</span>";
        for (h = lo; h <= hi; h++) {
            var v = m[d][h], bg = v ? "rgba(" + esVar("--heat-rgb") + ", " + (0.14 + 0.86 * v / max).toFixed(2) + ")" : "";
            var fg = v && v / max < 0.45 ? "var(--text-dark)" : "#fff";
            html += '<span class="es-heat-celda" ' + (v ? 'style="background:' + bg + ';color:' + fg + '" ' : "") + 'data-tip="' + ES_DIAS[d] + " " + h + ":00 — <b>" + v + "</b> " + (v === 1 ? "préstamo" : "préstamos") + '">' + (v || "") + "</span>";
        }
    }
    caja.innerHTML = html + "</div>";
}

function esDibujarStock(lista) {
    var caja = esEl("esStock"), total = 0, i, cols = [esVar("--chart-dev"), esVar("--chart-mora"), esVar("--chart-multa")];
    for (i = 0; i < lista.length; i++) { total += lista[i].valor; }
    if (!total) { esVacio(caja, "No hay artículos registrados."); return; }
    var b = '<div class="es-stock-barra">', l = '<div class="es-stock-lista">';
    for (i = 0; i < lista.length; i++) {
        if (lista[i].valor > 0) { b += '<i style="width:' + (lista[i].valor * 100 / total) + "%;background:" + cols[i] + '" data-tip="' + esEsc(lista[i].nombre + ": <b>" + lista[i].valor + "</b>") + '"></i>'; }
        l += '<span><i style="background:' + cols[i] + '"></i>' + esEsc(lista[i].nombre) + "<b>" + lista[i].valor + "</b></span>";
    }
    caja.innerHTML = b + "</div>" + l + "</div>";
}

function esDibujarTodo() {
    var nodo = esEl("esDatos");
    if (!nodo) { return; }
    var d;
    try { d = JSON.parse(nodo.textContent); } catch (e) { return; }
    esDibujarLinea(d.tendencia);
    esDibujarIngresos(d.tendencia);
    esDibujarDona(esEl("esCat"), d.categorias, esPaleta(), "préstamos", "No hubo préstamos en este periodo.");
    esDibujarDona(esEl("esEstado"), d.estadoActual, [esVar("--chart-dev"), esVar("--chart-multa"), esVar("--chart-mora")], "en circulación", "No hay préstamos en circulación.");
    esDibujarHorarios(d.horarios);
    esDibujarCalif(d.calificaciones);
    esDibujarTop(d.topArticulos);
    esDibujarStock(d.stock);
}

// pedir otro periodo sin recargar
function esUrl(desde, hasta) { return "/admin/estadisticas?desde=" + encodeURIComponent(desde) + "&hasta=" + encodeURIComponent(hasta); }
function esPedir(url) {
    var zona = esEl("esScroll");
    if (zona) { zona.classList.add("cargando"); }
    fetch(url, { credentials: "same-origin", headers: { "X-Requested-With": "fetch" } })
        .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.text(); })
        .then(function (html) {
            var nuevo = new DOMParser().parseFromString(html, "text/html");
            var nZona = nuevo.getElementById("esScroll"), nD = nuevo.getElementById("esDesde"), nH = nuevo.getElementById("esHasta");
            if (!nZona || !nD || !nH) { throw new Error("respuesta inesperada"); }
            esEl("esScroll").innerHTML = nZona.innerHTML;
            esEl("esDesde").value = nD.value;   // el servidor puede haber corregido el rango
            esEl("esHasta").value = nH.value;
            esSincronizarPeriodo();
            try { history.replaceState(null, "", url); } catch (e) { }
            esEl("esScroll").classList.remove("cargando");
            esEl("esScroll").scrollTop = 0;
            esDibujarTodo();
        })
        .catch(function () { window.location.href = url; });
}

function inicializarEstadisticas() {
    var c = document.querySelector(".dashboard-container"), chk = esEl("esComparar");
    if (c && chk) { c.classList.toggle("es-comparar", chk.checked); }
    esSincronizarPeriodo();
    esDibujarTodo();
    if (window.__esListeners) { return; }
    window.__esListeners = true;
    document.addEventListener("libred:tema", function () { if (esEl("esDatos")) { esDibujarTodo(); } });

    document.addEventListener("change", function (e) {
        var t = e.target;
        if (!t) { return; }
        if (t.id === "esPeriodo") {
            if (t.value === "personalizado") { esEl("esDesde").focus(); return; }
            var r = esRango(t.value);
            esPedir(esUrl(r[0], r[1]));
        } else if (t.id === "esDesde" || t.id === "esHasta") {
            var d = esEl("esDesde").value, h = esEl("esHasta").value;
            if (d && h) { esPedir(esUrl(d, h)); }
        } else if (t.id === "esComparar") {
            var cont = document.querySelector(".dashboard-container");
            if (cont) { cont.classList.toggle("es-comparar", t.checked); }
        }
    });
    document.addEventListener("click", function (e) {
        if (e.target.closest && e.target.closest("#esLimpiar")) {
            var r = esRango("30");
            esPedir(esUrl(r[0], r[1]));
        }
    });
    document.addEventListener("mousemove", function (e) {
        var tip = esTip(), el = e.target.closest ? e.target.closest("[data-tip]") : null;
        if (!el || !el.closest(".dashboard-container")) { tip.style.display = "none"; return; }
        tip.innerHTML = el.getAttribute("data-tip");
        tip.style.display = "block";
        var x = e.clientX + 14, y = e.clientY + 14;
        if (x + tip.offsetWidth > window.innerWidth - 8) { x = e.clientX - tip.offsetWidth - 14; }
        if (y + tip.offsetHeight > window.innerHeight - 8) { y = e.clientY - tip.offsetHeight - 14; }
        tip.style.left = x + "px";
        tip.style.top = y + "px";
    });
}
inicializarEstadisticas();