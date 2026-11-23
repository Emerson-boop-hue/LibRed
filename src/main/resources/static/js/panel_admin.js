// Panel de Control: filtro del historial de actividad. Solo funciones y un listener delegado (el layout re-ejecuta
// este archivo).
function pnFiltrar(tipo) {
    var filas = document.querySelectorAll("#pnHistorial .pn-mov");
    var visibles = 0;
    for (var i = 0; i < filas.length; i++) {
        var ok = tipo === "todo" || filas[i].getAttribute("data-filtro") === tipo;
        filas[i].hidden = !ok;
        if (ok) { visibles++; }
    }
    var grupos = document.querySelectorAll("#pnHistorial .pn-grupo");
    for (var j = 0; j < grupos.length; j++) {
        grupos[j].hidden = grupos[j].querySelectorAll(".pn-mov:not([hidden])").length === 0;
    }
    var vacio = document.getElementById("pnSinResultados");
    if (vacio) { vacio.hidden = visibles > 0; }
    var conteo = document.getElementById("pnConteo");
    if (conteo) { conteo.textContent = visibles; }
    var h = document.getElementById("pnHistorial");
    if (h) { h.scrollTop = 0; }
}

if (!window.__pnListeners) {
    window.__pnListeners = true;
    document.addEventListener("click", function (e) {
        var b = e.target.closest ? e.target.closest(".pn-filtro") : null;
        if (!b) { return; }
        var todos = document.querySelectorAll(".pn-filtro");
        for (var i = 0; i < todos.length; i++) { todos[i].classList.toggle("activo", todos[i] === b); }
        pnFiltrar(b.getAttribute("data-filtro"));
    });
}