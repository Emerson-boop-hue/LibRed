/* Módulo PRÉSTAMOS (admin). Idempotente: el layout vuelve a llamar a inicializarPrestamos() al navegar. */
function inicializarPrestamos() {
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
        selLector.classList.toggle('pr-sancionado', hayLector && opcionLector().dataset.estado === 'SANCIONADO');
        const hayCat = selCat.value !== '';
        const hayArt = selArt.value !== '';
        const dias = parseInt(selDias.value, 10) || 0;

        // Lector + chip de estado
        if (hayLector) {
            const o = opcionLector();
            const estado = o.dataset.estado, activos = parseInt(o.dataset.activos, 10) || 0;
            set('resumenLectorNombre', o.dataset.nombre);
            set('resumenLectorCodigo', o.dataset.codigo);
            const cupo = maxActivos === null ? activos + ' en curso · sin límite'
                                             : activos + ' de ' + maxActivos + ' activos';
            chip.hidden = false;
            chip.className = 'pr-chip';
            chip.innerHTML = '<i class="fa-solid fa-user-check"></i> ' + estado + ' · ' + cupo;
            if (estado === 'SANCIONADO') {
                chip.classList.add('pr-chip-mal');
                chip.innerHTML = '<i class="fa-solid fa-user-slash"></i> SANCIONADO · ' + cupo;
                bloqueo = 'Este lector está SANCIONADO y no puede recibir préstamos.'; rojo = true;
            } else if (maxActivos !== null && activos >= maxActivos) {
                chip.classList.add('pr-chip-warn');
                bloqueo = 'Este lector ya alcanzó el máximo de ' + maxActivos + ' préstamos activos permitido por tu librería.';
            } else {
                chip.classList.add('pr-chip-ok');
            }
        } else {
            chip.hidden = true;
            set('resumenLectorNombre', '--'); set('resumenLectorCodigo', '--');
        }

        // Categoría y artículo
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

        // Fechas y total
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

        mostrarAlerta(bloqueo, rojo);
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

    // Evita dobles envíos
    form.addEventListener('submit', () => { btn.disabled = true; });

    // Ventana de confirmación
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