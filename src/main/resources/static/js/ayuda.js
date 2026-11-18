// Ayuda y soporte: asistente de soporte en 3 pasos (tema, ayuda rápida con las guías, canal: solicitud o WhatsApp).

var AY_CAT = "todas";
var AY_TEMA = "";
// tema del asistente -> categoría de las preguntas frecuentes
var AY_TEMA_FAQ = { prestamos: "prestamos", devoluciones: "devoluciones", multas: "multas", inventario: "inventario", lectores: "lectores", plan: "ajustes" };

function ayNormalizar(t) {
    return String(t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
}

function ayMostrarTab(tab) {
    var paneles = { guias: "ayPanelGuias", soporte: "ayPanelSoporte", solicitudes: "ayPanelSolicitudes" };
    for (var k in paneles) {
        var p = document.getElementById(paneles[k]);
        if (p) { p.hidden = (k !== tab); }
    }
    var tabs = document.querySelectorAll(".ay-tab");
    for (var i = 0; i < tabs.length; i++) {
        tabs[i].classList.toggle("activo", tabs[i].getAttribute("data-tab") === tab);
    }
    try { history.replaceState(null, "", "/admin/ayuda?tab=" + tab); } catch (e) { }
}

// Aplica a la vez el filtro por tema (chips) y el texto del buscador
function ayFiltrar() {
    var caja = document.getElementById("ayBuscar");
    var lista = document.getElementById("ayLista");
    if (!caja || !lista) { return; }
    var q = ayNormalizar(caja.value).trim();
    var items = lista.querySelectorAll(".ay-item");
    var visibles = 0;
    for (var i = 0; i < items.length; i++) {
        var it = items[i];
        var cats = " " + (it.getAttribute("data-cat") || "") + " ";
        var okCat = AY_CAT === "todas" || cats.indexOf(" " + AY_CAT + " ") !== -1;
        var texto = ayNormalizar((it.getAttribute("data-buscar") || "") + " " + it.textContent);
        var okTexto = q === "" || texto.indexOf(q) !== -1;
        var ok = okCat && okTexto;
        it.hidden = !ok;
        if (ok) { visibles++; }
        if (q !== "") { it.open = ok; } else { it.open = false; }
    }
    var vacio = document.getElementById("ayVacio");
    if (vacio) { vacio.hidden = visibles > 0; }
}

// Asistente de soporte: tema -> ayuda rápida -> canal (o formulario)
function ayPaso(nombre) {
    var as = document.getElementById("ayAsistente");
    if (!as) { return; }
    var paneles = as.querySelectorAll("[data-pane]");
    for (var i = 0; i < paneles.length; i++) { paneles[i].hidden = (paneles[i].getAttribute("data-pane") !== nombre); }
    var n = nombre === "tema" ? 1 : (nombre === "ayuda" ? 2 : 3);
    var pasos = as.querySelectorAll(".ay-paso");
    for (var j = 0; j < pasos.length; j++) {
        var k = Number(pasos[j].getAttribute("data-p"));
        pasos[j].classList.toggle("activo", k === n);
        pasos[j].classList.toggle("hecho", k < n);
    }
    if (nombre === "canal") { ayPrepararWhatsapp(); }
}

function ayTemaNombre() {
    var b = document.querySelector('.ay-tema[data-tema="' + AY_TEMA + '"] strong');
    return b ? b.textContent : "";
}

// Copia al paso 2 las guías del tema elegido (hasta 4); si no hay ninguna, pasa directo a los canales
function ayElegirTema(tema) {
    AY_TEMA = tema;
    var campo = document.getElementById("ayTema");
    if (campo) { campo.value = tema; }
    var tipo = document.getElementById("ayCategoria");
    if (tipo && !tipo.getAttribute("data-tocado")) { tipo.value = tema === "plan" ? "PLAN" : "DUDA"; }
    var cont = document.getElementById("aySug"), lista = document.getElementById("ayLista");
    var nombre = document.getElementById("ayTemaNombre");
    if (nombre) { nombre.textContent = ayTemaNombre(); }
    var cat = AY_TEMA_FAQ[tema];
    var puestas = 0;
    if (cont) { cont.innerHTML = ""; }
    if (cont && lista && cat) {
        var items = lista.querySelectorAll(".ay-item");
        for (var i = 0; i < items.length && puestas < 4; i++) {
            if ((" " + items[i].getAttribute("data-cat") + " ").indexOf(" " + cat + " ") === -1) { continue; }
            var c = items[i].cloneNode(true);
            c.hidden = false; c.open = false;
            cont.appendChild(c);
            puestas++;
        }
    }
    ayPaso(puestas > 0 ? "ayuda" : "canal");
}

// El mensaje de WhatsApp sale ya escrito: librería, código del administrador y tema
function ayPrepararWhatsapp() {
    var a = document.getElementById("ayWhatsapp"), as = document.getElementById("ayAsistente");
    if (!a || !as || !a.getAttribute("data-numero")) { return; }
    var tema = AY_TEMA === "otro" ? "" : ayTemaNombre();
    var texto = "Hola, soy el administrador de " + (as.getAttribute("data-nombre") || "mi librería")
        + " (código " + (as.getAttribute("data-codigo") || "-") + ")."
        + (tema ? "\nTema: " + tema : "")
        + "\n--------------------\nMi consulta: ";
    a.href = "https://wa.me/" + a.getAttribute("data-numero") + "?text=" + encodeURIComponent(texto);
}

function ayIniciarAsistente() {
    var as = document.getElementById("ayAsistente");
    if (!as) { return; }
    AY_TEMA = as.getAttribute("data-tema") || "";
    var tipo = document.getElementById("ayCategoria");
    if (tipo) { tipo.setAttribute("data-tocado", as.getAttribute("data-paso") === "form" ? "1" : ""); }
    ayPaso(as.getAttribute("data-paso") || "tema");
}

function ayConteo() {
    var m = document.getElementById("ayMensaje"), c = document.getElementById("ayConteo");
    if (m && c) { c.textContent = m.value.length; }
}

// Pide la pantalla de nuevo y reemplaza solo la lista de solicitudes (y el contador de la pestaña) si cambió
function ayRefrescar() {
    var lista = document.getElementById("ayTickets");
    if (!lista) {
        if (window.__ayTimer) { clearInterval(window.__ayTimer); window.__ayTimer = null; }
        return;
    }
    var panel = document.getElementById("ayPanelSolicitudes");
    if (!panel || panel.hidden || document.hidden) { return; }
    fetch("/admin/ayuda?tab=solicitudes", { credentials: "same-origin" })
        .then(function (r) { return r.ok ? r.text() : Promise.reject(); })
        .then(function (html) {
            var doc = new DOMParser().parseFromString(html, "text/html");
            var nueva = doc.getElementById("ayTickets");
            var actual = document.getElementById("ayTickets");
            if (nueva && actual && nueva.innerHTML !== actual.innerHTML) {
                actual.innerHTML = nueva.innerHTML;
                var tabN = doc.querySelector('.ay-tab[data-tab="solicitudes"]');
                var tabA = document.querySelector('.ay-tab[data-tab="solicitudes"]');
                if (tabN && tabA) { tabA.innerHTML = tabN.innerHTML; }
            }
        })
        .catch(function () { /* sin conexión: se reintenta en el siguiente ciclo */ });
}

// Envío de la solicitud: se envía sin recargar la página y, si salió bien, se muestra la ventana "Solicitud enviada"
function ayModalCerrar() {
    var m = document.getElementById("ayModal");
    if (m) { m.hidden = true; }
}

function ayEnviar() {
    var f = document.getElementById("ayForm");
    if (!f || !f.reportValidity()) { return; }
    var btn = f.querySelector(".ay-boton");
    if (btn) { btn.disabled = true; }
    fetch(f.getAttribute("action"), {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams(new FormData(f)).toString()
    })
        .then(function (r) { return r.ok ? r.text() : Promise.reject(); })
        .then(function (html) {
            // El servidor responde con la pantalla ya actualizada: lista nueva o, si algo falló, el aviso en rojo
            var doc = new DOMParser().parseFromString(html, "text/html");
            var nuevo = doc.getElementById("ayPanelSoporte");
            var actual = document.getElementById("ayPanelSoporte");
            var nuevoSol = doc.getElementById("ayPanelSolicitudes");
            var actualSol = document.getElementById("ayPanelSolicitudes");
            if (!nuevo || !actual || !nuevoSol || !actualSol) { throw new Error("sin panel"); }
            var huboError = nuevo.querySelector(".ay-error") !== null;
            var primera = nuevoSol.querySelector(".ay-ticket-num");
            actual.innerHTML = nuevo.innerHTML;
            actualSol.innerHTML = nuevoSol.innerHTML;
            var tabN = doc.querySelector('.ay-tab[data-tab="solicitudes"]');
            var tabA = document.querySelector('.ay-tab[data-tab="solicitudes"]');
            if (tabN && tabA) { tabA.innerHTML = tabN.innerHTML; }
            ayConteo();
            ayIniciarAsistente();
            if (!huboError) {
                var num = document.getElementById("ayModalNum");
                if (num && primera) { num.textContent = primera.textContent; }
                var m = document.getElementById("ayModal");
                if (m) { m.hidden = false; }
                var cerrar = document.getElementById("ayModalCerrar");
                if (cerrar) { cerrar.focus(); }
            }
        })
        .catch(function () { window.location.href = "/admin/ayuda?tab=soporte"; });
}

function inicializarAyuda() {
    var cont = document.querySelector(".dashboard-container");
    var tab = cont ? cont.getAttribute("data-tab") : "guias";
    AY_CAT = "todas";
    ayMostrarTab(tab || "guias");
    ayConteo();
    ayIniciarAsistente();

    if (!window.__ayTimer) { window.__ayTimer = setInterval(ayRefrescar, 30000); }

    if (window.__ayListeners) { return; }
    window.__ayListeners = true;

    document.addEventListener("click", function (e) {
        if (!e.target.closest) { return; }
        var chip = e.target.closest(".ay-chip");
        if (chip) {
            AY_CAT = chip.getAttribute("data-cat");
            var chips = document.querySelectorAll(".ay-chip");
            for (var i = 0; i < chips.length; i++) { chips[i].classList.toggle("activo", chips[i] === chip); }
            ayFiltrar();
            return;
        }
        var tema = e.target.closest(".ay-tema");
        if (tema) { ayElegirTema(tema.getAttribute("data-tema")); return; }
        var ir = e.target.closest("[data-ir-paso]");
        if (ir) { ayPaso(ir.getAttribute("data-ir-paso")); return; }
        if (e.target.closest("#ayResuelto")) { ayPaso("tema"); return; }
        if (e.target.closest(".ay-canal.apagado")) { e.preventDefault(); return; }
        var irModal = e.target.closest("#ayModal [data-ir]");
        if (irModal) { ayModalCerrar(); ayMostrarTab(irModal.getAttribute("data-ir")); return; }
        if (e.target.closest("#ayModal [data-accion]")) { ayModalCerrar(); return; }
        if (e.target.id === "ayModal") { ayModalCerrar(); return; }
        var t = e.target.closest(".ay-tab, [data-ir]");
        if (t) { ayMostrarTab(t.getAttribute("data-tab") || t.getAttribute("data-ir")); }
    });
    document.addEventListener("submit", function (e) {
        if (e.target.id === "ayForm") { e.preventDefault(); ayEnviar(); }
    });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { ayModalCerrar(); }
    });
    document.addEventListener("input", function (e) {
        if (e.target.id === "ayBuscar") { ayFiltrar(); }
        if (e.target.id === "ayMensaje") { ayConteo(); }
    });
    document.addEventListener("change", function (e) {
        if (e.target.id === "ayCategoria") { e.target.setAttribute("data-tocado", "1"); }
    });
}

inicializarAyuda();