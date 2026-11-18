// Reportes: selector de periodo (Hoy / Esta semana / Este mes / Mes anterior / Personalizado) + fechas + Limpiar.

function rpEl(id) { return document.getElementById(id); }
function rpIso(d) {
    return d.getFullYear() + "-" + ("0" + (d.getMonth() + 1)).slice(-2) + "-" + ("0" + d.getDate()).slice(-2);
}
function rpHoy() {
    var c = document.querySelector(".dashboard-container");
    var t = c && c.dataset.hoy;
    if (!t) { return new Date(); }
    var p = t.split("-");
    return new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10), 12, 0, 0);
}
// Rango [desde, hasta] (texto yyyy-mm-dd) de cada opción del selector
function rpRango(clave) {
    var hoy = rpHoy();
    var desde = new Date(hoy.getTime());
    var hasta = new Date(hoy.getTime());
    if (clave === "semana") {
        desde.setDate(hoy.getDate() - ((hoy.getDay() + 6) % 7));
    } else if (clave === "mes") {
        desde.setDate(1);
    } else if (clave === "anterior") {
        desde = new Date(hoy.getFullYear(), hoy.getMonth() - 1, 1, 12, 0, 0);
        hasta = new Date(hoy.getFullYear(), hoy.getMonth(), 0, 12, 0, 0);
    }
    return [rpIso(desde), rpIso(hasta)];
}
// Deja el selector en la opción que coincide con las fechas actuales (o en "Personalizado")
function rpSincronizarPeriodo() {
    var sel = rpEl("rpPeriodo"), d = rpEl("rpDesde"), h = rpEl("rpHasta");
    if (!sel || !d || !h) { return; }
    var claves = ["hoy", "semana", "mes", "anterior"], valor = "personalizado";
    for (var i = 0; i < claves.length; i++) {
        var r = rpRango(claves[i]);
        if (r[0] === d.value && r[1] === h.value) { valor = claves[i]; break; }
    }
    sel.value = valor;
}
function rpTipoActual() {
    var f = document.getElementById("rpForm");
    return f && f.elements["tipo"] ? f.elements["tipo"].value : "cierre";
}
function rpUrl(tipo, desde, hasta) {
    return "/admin/reportes?tipo=" + encodeURIComponent(tipo) + "&desde=" + encodeURIComponent(desde) + "&hasta=" + encodeURIComponent(hasta);
}
function rpPedir(url) {
    var hoja = document.querySelector(".rp-scroll");
    if (hoja) { hoja.classList.add("cargando"); }
    fetch(url, { credentials: "same-origin", headers: { "X-Requested-With": "fetch" } })
        .then(function (r) { if (!r.ok) { throw new Error("http " + r.status); } return r.text(); })
        .then(function (html) {
            var nuevo = new DOMParser().parseFromString(html, "text/html");
            var nHoja = nuevo.querySelector(".rp-scroll"), nTabs = nuevo.querySelector(".rp-tabs"),
                nDesc = nuevo.querySelector(".rp-descargas"), nForm = nuevo.getElementById("rpForm");
            if (!nHoja || !nTabs || !nDesc || !nForm) { throw new Error("respuesta inesperada"); }
            document.querySelector(".rp-scroll").innerHTML = nHoja.innerHTML;
            document.querySelector(".rp-tabs").innerHTML = nTabs.innerHTML;
            document.querySelector(".rp-descargas").innerHTML = nDesc.innerHTML;
            rpEl("rpForm").elements["tipo"].value = nForm.elements["tipo"].value;
            rpEl("rpDesde").value = nForm.elements["desde"].value;   // el servidor puede haber corregido el rango
            rpEl("rpHasta").value = nForm.elements["hasta"].value;
            rpSincronizarPeriodo();
            try { history.replaceState(null, "", url); } catch (e) { }
            var h = document.querySelector(".rp-scroll");
            if (h) { h.classList.remove("cargando"); h.scrollTop = 0; }
        })
        .catch(function () { window.location.href = url; });   // si algo falla, carga normal
}
function rpCargarPeriodo(desde, hasta) { rpPedir(rpUrl(rpTipoActual(), desde, hasta)); }

function inicializarReportes() {
    rpSincronizarPeriodo();
    if (window.__rpListeners) { return; }
    window.__rpListeners = true;

    document.addEventListener("click", function (e) {
        if (!e.target.closest) { return; }
        var tab = e.target.closest(".rp-tab");
        if (tab) {
            if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) { return; }
            e.preventDefault();
            rpPedir(tab.getAttribute("href"));
            return;
        }
        if (e.target.closest("#rpLimpiar")) {
            var r = rpRango("mes");
            rpCargarPeriodo(r[0], r[1]);
        }
    });
    document.addEventListener("change", function (e) {
        var t = e.target;
        if (!t) { return; }
        if (t.id === "rpPeriodo") {
            if (t.value === "personalizado") { rpEl("rpDesde").focus(); return; }
            var r = rpRango(t.value);
            rpCargarPeriodo(r[0], r[1]);
        } else if (t.id === "rpDesde" || t.id === "rpHasta") {
            var d = rpEl("rpDesde").value, h = rpEl("rpHasta").value;
            if (d && h) { rpCargarPeriodo(d, h); }
        }
    });
}
inicializarReportes();