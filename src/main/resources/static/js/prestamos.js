// Módulo PRÉSTAMOS (admin). Idempotente: el layout vuelve a llamar a inicializarPrestamos() al navegar.

// POR ENTREGAR (segunda pestaña de Préstamos)

function mostrarPestanaPrestamos(nombre) {
    var nuevo = document.getElementById("prTabNuevo");
    var entrega = document.getElementById("prTabEntrega");
    if (!nuevo || !entrega) return;
    nuevo.hidden = nombre !== "nuevo";
    entrega.hidden = nombre !== "entrega";
    document.querySelectorAll(".pr-tab").forEach(function (t) {
        t.classList.toggle("activa", t.dataset.tab === nombre);
    });
}

function iniciarPestanasPrestamos() {
    if (!document.getElementById("prTabNuevo")) return;
    // Tras entregar o cancelar, el servidor vuelve con ?tab=por-entregar
    var params = new URLSearchParams(window.location.search);
    mostrarPestanaPrestamos(params.get("tab") === "por-entregar" ? "entrega" : "nuevo");
    if (window.__prestamosTabsListeners) return;
    window.__prestamosTabsListeners = true;
    document.addEventListener("click", function (e) {
        var tab = e.target && e.target.closest ? e.target.closest(".pr-tab") : null;
        if (tab) mostrarPestanaPrestamos(tab.dataset.tab);
    });
}

var PE = { fila: null };

function peEl(id) { return document.getElementById(id); }
function peMoneda(n) { return "S/. " + (parseFloat(n) || 0).toFixed(2); }
function peNormalizar(t) { return (t || "").toString().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, ""); }

// Último día de devolución si se entrega hoy: hoy + los días que pidió el lector
function peLimite(dias) {
    var d = new Date();
    d.setDate(d.getDate() + (parseInt(dias, 10) || 1));
    var dd = ("0" + d.getDate()).slice(-2), mm = ("0" + (d.getMonth() + 1)).slice(-2);
    return dd + "/" + mm + "/" + d.getFullYear();
}

function iniciarPorEntregar() {
    if (!peEl("tbodyPedidos")) return;
    PE.fila = null;
    registrarEventosPorEntregar();
    filtrarPedidos();
}

function filtrarPedidos() {
    var texto = peNormalizar((peEl("buscarPedido") || {}).value).trim();
    var filas = document.querySelectorAll("#tbodyPedidos .pe-fila");
    var visibles = 0, total = 0;
    filas.forEach(function (f) {
        var base = peNormalizar([f.dataset.lector, f.dataset.codlector, f.dataset.articulo, f.dataset.codarticulo].join(" "));
        var coincide = !texto || base.indexOf(texto) !== -1;
        f.hidden = !coincide;
        if (coincide) { visibles++; total += parseFloat(f.dataset.monto) || 0; }
    });
    var sin = peEl("filaSinResultadosPedido");
    if (sin) sin.hidden = !(filas.length > 0 && visibles === 0);
    var boton = peEl("btnLimpiarBusquedaPedido");
    if (boton) boton.classList.toggle("inactivo", texto === "");
    var resumen = peEl("peResumen");
    if (resumen) {
        resumen.innerHTML = filas.length === 0 ? "" :
            "<strong>" + visibles + "</strong> " + (visibles === 1 ? "pedido" : "pedidos") + " · por cobrar <strong>" + peMoneda(total) + "</strong>";
    }
}

// EVENTOS (una sola vez)
function registrarEventosPorEntregar() {
    if (window.__porEntregarListeners) return;
    window.__porEntregarListeners = true;

    document.addEventListener("input", function (e) {
        if (e.target && e.target.id === "buscarPedido") filtrarPedidos();
    });

    document.addEventListener("click", function (e) {
        if (!e.target || !e.target.closest) return;

        if (e.target.closest("#btnLimpiarBusquedaPedido")) {
            var b = peEl("buscarPedido");
            if (b) b.value = "";
            filtrarPedidos();
            return;
        }
        var fila = e.target.closest("#tbodyPedidos .pe-fila");
        if (fila) { seleccionarPedido(fila); return; }

        if (e.target.closest("#btnEntregar")) { abrirEntrega(); return; }
        if (e.target.closest("#btnVolverEntrega") || e.target.id === "peModalEntrega") { cerrarModal("peModalEntrega"); return; }
        if (e.target.closest("#btnConfirmarEntrega")) { enviarAccion("entregar", "btnConfirmarEntrega"); return; }

        if (e.target.closest("#btnCancelarPedido")) { abrirCancelacion(); return; }
        if (e.target.closest("#btnVolverCancelar") || e.target.id === "peModalCancelar") { cerrarModal("peModalCancelar"); return; }
        if (e.target.closest("#btnConfirmarCancelar")) { enviarAccion("cancelar", "btnConfirmarCancelar"); return; }
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") { cerrarModal("peModalEntrega"); cerrarModal("peModalCancelar"); }
    });
}

function seleccionarPedido(fila) {
    document.querySelectorAll("#tbodyPedidos .pe-fila.seleccionada").forEach(function (f) { f.classList.remove("seleccionada"); });
    fila.classList.add("seleccionada");
    PE.fila = fila;

    var tarifa = parseFloat(fila.dataset.tarifa);
    peEl("sumLector").textContent = fila.dataset.lector;
    peEl("sumCodLector").textContent = fila.dataset.codlector || "--";
    peEl("sumArticulo").textContent = fila.dataset.articulo;
    peEl("sumCodArticulo").textContent = fila.dataset.codarticulo || "--";
    peEl("sumPedido").textContent = fila.dataset.pedido || "--";
    peEl("sumDias").textContent = fila.dataset.dias || "--";
    peEl("sumTarifa").textContent = isNaN(tarifa) ? "--" : peMoneda(tarifa);
    peEl("sumLimite").textContent = peLimite(fila.dataset.dias);
    peEl("sumMonto").textContent = peMoneda(fila.dataset.monto);

    var lnk = peEl("peBtnComprobante");
    if (lnk) lnk.href = "/admin/prestamos/" + fila.dataset.id + "/comprobante.pdf";
    peEl("peDescarga").hidden = false;
    peEl("peSinSeleccion").hidden = true;
    peEl("peTicket").hidden = false;
    peEl("btnEntregar").disabled = false;
    peEl("btnCancelarPedido").disabled = false;
}

// ENTREGA / CANCELACIÓN
function abrirEntrega() {
    var f = PE.fila;
    if (!f) return;
    peEl("enLector").textContent = f.dataset.lector;
    peEl("enArticulo").textContent = f.dataset.articulo;
    peEl("enLimite").textContent = peLimite(f.dataset.dias);
    peEl("enMonto").textContent = peMoneda(f.dataset.monto);
    peEl("peModalEntrega").hidden = false;
}

function abrirCancelacion() {
    var f = PE.fila;
    if (!f) return;
    peEl("caLector").textContent = f.dataset.lector;
    peEl("caArticulo").textContent = f.dataset.articulo;
    peEl("peModalCancelar").hidden = false;
}

function cerrarModal(id) {
    var m = peEl(id);
    if (m) m.hidden = true;
}

function enviarAccion(accion, idBoton) {
    if (!PE.fila) return;
    var boton = peEl(idBoton);
    if (boton) boton.disabled = true; // evita doble envío
    var form = peEl("peFormAccion");
    form.action = "/admin/prestamos/" + accion + "/" + PE.fila.dataset.id;
    form.submit();
}

function inicializarPrestamos() {
    iniciarPestanasPrestamos();
    iniciarPorEntregar();

    const form = document.getElementById('formPrestamo');
    if (!form || form.dataset.listo === '1') return;
    form.dataset.listo = '1';

    const $ = (id) => document.getElementById(id);
    const selLector = $('selectLector'), selCat = $('selectCategoria'), selArt = $('selectRecurso'),
          selDias = $('selectDias'), inFecha = $('inputFechaDevolucion'), btn = $('btnSubmitPrestamo'),
          chip = $('lectorChip'), alerta = $('alertaValidacion');
    const maxActivos = form.dataset.maxActivos === '' ? null : parseInt(form.dataset.maxActivos, 10);
    const base = Array.from(document.querySelectorAll('#prBase li'));

    const pad = (n) => String(n).padStart(2, '0');
    const iso = (d) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    const ver = (d) => pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear();
    const moneda = (n) => 'S/ ' + n.toFixed(2);
    const set = (id, t) => { const e = $(id); if (e) e.textContent = t; };

    function mostrarAlerta(msg, rojo) {
        alerta.textContent = msg || '';
        alerta.hidden = !msg;
        alerta.classList.toggle('pr-alerta-rojo', !!rojo);
    }

    function opcionLector() { return selLector.options[selLector.selectedIndex]; }
    function opcionCat() { return selCat.options[selCat.selectedIndex]; }

    // Rellena los artículos de la categoría elegida
    function cargarArticulos() {
        const cat = selCat.value;
        selArt.innerHTML = '';
        const ph = document.createElement('option');
        ph.value = ''; ph.disabled = true; ph.selected = true;
        ph.textContent = cat ? 'Seleccione un artículo' : 'Seleccione primero una categoría';
        selArt.appendChild(ph);
        base.filter((li) => li.dataset.categoria === cat).forEach((li) => {
            const o = document.createElement('option');
            o.value = li.dataset.id;
            o.dataset.titulo = li.dataset.titulo;
            o.dataset.codigo = li.dataset.codigo;
            o.textContent = li.dataset.codigo + ' - ' + li.dataset.titulo + ' (' + li.dataset.stock + ' disp.)';
            selArt.appendChild(o);
        });
        selArt.disabled = !cat;
    }

    function actualizar() {
        let bloqueo = '', rojo = false;
        const hayLector = selLector.value !== '';
        selLector.classList.toggle('pr-bloqueado', hayLector && opcionLector().dataset.estado === 'BLOQUEADO');
        const hayCat = selCat.value !== '';
        const hayArt = selArt.value !== '';
        const dias = parseInt(selDias.value, 10) || 0;

        // Lector + chip de estado
        if (hayLector) {
            const o = opcionLector();
            const estado = o.dataset.estado, activos = parseInt(o.dataset.activos, 10) || 0;
            set('resumenLectorNombre', o.dataset.nombre);
            set('resumenLectorCodigo', o.dataset.codigo);
            const cupo = maxActivos === null ? activos + ' en curso'
                                             : activos + ' de ' + maxActivos + ' activos';
            chip.hidden = false;
            chip.className = 'pr-chip';
            // Un solo aviso, compacto y junto al título "Lector" (no desplaza el formulario)
            if (estado === 'BLOQUEADO') {
                chip.classList.add('pr-chip-mal');
                chip.textContent = 'Bloqueado · no puede recibir préstamos';
                bloqueo = 'bloqueado';
            } else if (maxActivos !== null && activos >= maxActivos) {
                chip.classList.add('pr-chip-warn');
                chip.textContent = 'Límite alcanzado · ' + cupo;
                bloqueo = 'limite';
            } else {
                chip.classList.add('pr-chip-ok');
                chip.textContent = 'Habilitado · ' + cupo;
            }
        } else {
            chip.hidden = true;
            set('resumenLectorNombre', '--'); set('resumenLectorCodigo', '--');
        }

        let tarifa = 0;
        if (hayCat) {
            const c = opcionCat();
            tarifa = parseFloat(c.dataset.tarifa) || 0;
            set('resumenCategoria', c.dataset.etiqueta);
            set('calcTarifa', moneda(tarifa));
            set('resumenTarifa', moneda(tarifa));
        } else {
            set('resumenCategoria', '--'); set('calcTarifa', '--'); set('resumenTarifa', '--');
        }
        if (hayArt) {
            const a = selArt.options[selArt.selectedIndex];
            set('resumenArticuloTitulo', a.dataset.titulo);
            set('resumenArticuloCodigo', a.dataset.codigo);
        } else {
            set('resumenArticuloTitulo', '--'); set('resumenArticuloCodigo', '--');
        }

        const hoy = new Date();
        set('calcHoy', ver(hoy)); set('resumenFechaPrestamo', ver(hoy));
        if (dias > 0) {
            const lim = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() + dias);
            inFecha.value = iso(lim);
            set('calcLimite', ver(lim)); set('resumenFechaLimite', ver(lim));
            set('resumenDias', dias + (dias === 1 ? ' día' : ' días'));
            if (hayCat) { set('calcTotal', moneda(tarifa * dias)); set('resumenTotal', moneda(tarifa * dias)); }
        } else {
            inFecha.value = '';
            ['calcLimite', 'resumenFechaLimite', 'resumenDias', 'calcTotal', 'resumenTotal'].forEach((i) => set(i, '--'));
        }
        if (dias > 0 && !hayCat) { set('calcTotal', '--'); set('resumenTotal', '--'); }

        mostrarAlerta('', false);   // el estado del lector ya se informa en la etiqueta junto al título
        btn.disabled = !(hayLector && hayCat && hayArt && dias > 0 && !bloqueo);
    }

    function limpiar() {
        form.reset();
        cargarArticulos();
        inFecha.value = '';
        actualizar();
    }

    selLector.addEventListener('change', actualizar);
    selCat.addEventListener('change', () => { cargarArticulos(); actualizar(); });
    selArt.addEventListener('change', actualizar);
    selDias.addEventListener('change', actualizar);
    $('btnLimpiar').addEventListener('click', limpiar);

    form.addEventListener('submit', () => { btn.disabled = true; });

    const modal = $('prModalOk');
    if (modal) {
        const cerrar = () => modal.remove();
        const b = $('prModalCerrar');
        if (b) b.addEventListener('click', cerrar);
        modal.addEventListener('click', (e) => { if (e.target === modal) cerrar(); });
    }

    cargarArticulos();
    actualizar();
}

document.addEventListener('DOMContentLoaded', inicializarPrestamos);