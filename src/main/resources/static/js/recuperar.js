(function () {
    'use strict';
    var $ = function (id) { return document.getElementById(id); };
    var ov = $('rcOverlay');
    if (!ov) return;
    var p1 = $('rcPaso1'), p2 = $('rcPaso2'), p3 = $('rcPaso3');
    var correo = $('rcCorreo'), codigo = $('rcCodigo'), clave = $('rcClave');
    var e1 = $('rcError1'), e2 = $('rcError2');
    var enviando = false;

    function mostrar(p) { [p1, p2, p3].forEach(function (x) { x.hidden = (x !== p); }); }
    function err(el, msg) { el.textContent = msg || ''; el.hidden = !msg; }
    function emailOk(v) { return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v); }
    function claveOk(v) { return v.length >= 8 && v.length <= 20 && !/\s/.test(v); }
    // Fuerza de la contraseña: 0 (vacía) a 4 barras, igual que en el registro de librería y en el acceso del lector
    function fuerza(v) {
        if (!v) return 0;
        var n = 0;
        if (v.length >= 8) n++;
        if (/[a-z]/.test(v) && /[A-Z]/.test(v)) n++;
        if (/\d/.test(v)) n++;
        if (/[^A-Za-z0-9]/.test(v) || v.length >= 12) n++;
        return Math.max(n, 1);
    }

    function post(url, body) {
        return fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
            .then(function (r) {
                return r.json().catch(function () { return {}; }).then(function (j) { return { ok: r.ok, data: j }; });
            });
    }
    function girar(id, on) { var b = $(id); if (b) b.classList.toggle('rc-cargando', !!on); }
    function mensaje(d, def) { return (d && (d.mensaje || d.message || d.error)) || def; }

    function abrir() {
        correo.value = codigo.value = clave.value = ''; clave.type = 'password'; $('rcOjo').firstElementChild.className = 'fa-regular fa-eye';
        err(e1); err(e2); validar();
        mostrar(p1); ov.hidden = false;
        setTimeout(function () { correo.focus(); }, 50);
    }
    function cerrar() { ov.hidden = true; }

    function validar() {
        $('rcEnviar').disabled = !emailOk(correo.value.trim());
        $('rcFuerza').dataset.n = String(fuerza(clave.value));
        // Se habilita apenas haya algo escrito; lo que falte se explica al pulsar (así nunca queda "muerto" sin saber
        // por qué)
        $('rcGuardar').disabled = !(codigo.value.length > 0 && clave.value.length > 0);
    }

    correo.addEventListener('input', validar);
    clave.addEventListener('input', validar);
    codigo.addEventListener('input', function () { codigo.value = codigo.value.replace(/\D/g, '').slice(0, 6); validar(); });

    p1.addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (enviando || $('rcEnviar').disabled) return;
        enviando = true; err(e1); girar('rcEnviar', true); $('rcEnviar').textContent = 'Enviando código...';
        post('/acceso/recuperar/codigo', { correo: correo.value.trim() }).then(function (r) {
            enviando = false; girar('rcEnviar', false); $('rcEnviar').textContent = 'Enviar código';
            if (!r.ok) { err(e1, mensaje(r.data, 'No se pudo enviar el código. Inténtalo de nuevo.')); return; }
            mostrar(p2); codigo.focus();
        }).catch(function () { enviando = false; girar('rcEnviar', false); $('rcEnviar').textContent = 'Enviar código'; err(e1, 'Sin conexión. Inténtalo de nuevo.'); });
    });

    p2.addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (enviando || $('rcGuardar').disabled) return;
        if (codigo.value.length !== 6) {
            var faltan = 6 - codigo.value.length;
            err(e2, 'El código tiene 6 dígitos' + (faltan > 0 ? ' (te falta' + (faltan > 1 ? 'n ' + faltan + ' dígitos)' : ' 1 dígito)') : '') + '.');
            codigo.focus();
            return;
        }
        if (!claveOk(clave.value)) { err(e2, 'La contraseña debe tener entre 8 y 20 caracteres, sin espacios.'); clave.focus(); return; }
        enviando = true; err(e2); girar('rcGuardar', true); $('rcGuardar').textContent = 'Guardando...';
        post('/acceso/recuperar/restablecer', { correo: correo.value.trim(), codigo: codigo.value, password: clave.value }).then(function (r) {
            enviando = false; girar('rcGuardar', false); $('rcGuardar').textContent = 'Cambiar contraseña';
            if (!r.ok) { err(e2, mensaje(r.data, 'No se pudo cambiar la contraseña.')); return; }
            mostrar(p3);
        }).catch(function () { enviando = false; girar('rcGuardar', false); $('rcGuardar').textContent = 'Cambiar contraseña'; err(e2, 'Sin conexión. Inténtalo de nuevo.'); });
    });

    $('rcOjo').addEventListener('click', function () {
        var oculta = clave.type === 'password';
        clave.type = oculta ? 'text' : 'password';
        this.firstElementChild.className = oculta ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    });
    codigo.addEventListener('input', function () { err(e2); });
    clave.addEventListener('input', function () { err(e2); });
    $('rcAtras').addEventListener('click', function () { mostrar(p1); });
    $('rcCerrar').addEventListener('click', cerrar);
    $('rcListo').addEventListener('click', cerrar);
    ov.addEventListener('mousedown', function (e) { if (e.target === ov) cerrar(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !ov.hidden) cerrar(); });

    document.addEventListener('click', function (e) {
        var a = e.target.closest && e.target.closest('[data-recuperar]');
        if (a) { e.preventDefault(); abrir(); }
    });
})();