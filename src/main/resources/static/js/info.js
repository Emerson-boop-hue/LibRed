// Páginas públicas: Conócenos, Contáctenos y Ayuda.
(function () {
    'use strict';

    document.querySelectorAll('[data-anio]').forEach(function (e) { e.textContent = new Date().getFullYear(); });

    var burger = document.getElementById('infBurger'), nav = document.getElementById('infNav');
    if (burger && nav) {
        burger.addEventListener('click', function () {
            var abierto = nav.classList.toggle('abierto');
            burger.setAttribute('aria-expanded', abierto ? 'true' : 'false');
        });
    }

    // Conócenos: "Soy lector" / "Tengo una librería"
    var conmuta = document.querySelectorAll('.inf-conmuta-btn');
    conmuta.forEach(function (b) {
        b.addEventListener('click', function () {
            conmuta.forEach(function (x) { x.classList.toggle('activo', x === b); });
            document.querySelectorAll('[data-pasos-panel]').forEach(function (p) {
                p.hidden = p.getAttribute('data-pasos-panel') !== b.getAttribute('data-pasos');
            });
        });
    });

    // Ayuda: buscador + categorías
    var lista = document.getElementById('ayLista');
    if (lista) {
        var buscar = document.getElementById('ayBuscar'), vacio = document.getElementById('ayVacio');
        var chips = document.querySelectorAll('#ayChips .inf-chip'), catActual = 'todas';
        var items = Array.prototype.slice.call(lista.querySelectorAll('.inf-faq'));
        var norm = function (t) { return (t || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''); };
        var textos = items.map(function (d) { return norm(d.textContent); });

        function filtrar() {
            var q = norm(buscar.value.trim()), visibles = 0;
            items.forEach(function (d, i) {
                var ok = (catActual === 'todas' || d.getAttribute('data-cat') === catActual) && (!q || textos[i].indexOf(q) !== -1);
                d.hidden = !ok;
                if (ok) visibles++;
            });
            vacio.hidden = visibles !== 0;
        }
        buscar.addEventListener('input', filtrar);
        chips.forEach(function (c) {
            c.addEventListener('click', function () {
                catActual = c.getAttribute('data-cat');
                chips.forEach(function (x) { x.classList.toggle('activo', x === c); });
                filtrar();
            });
        });
    }

    // Contáctenos: formulario
    var form = document.getElementById('ctForm');
    if (form) {
        var $ = function (id) { return document.getElementById(id); };
        var err = $('ctError'), btn = $('ctEnviar'), exito = $('ctExito');
        var campos = ['ctNombre', 'ctCorreo', 'ctMensaje'];

        $('ctMensaje').addEventListener('input', function () { $('ctConteo').textContent = this.value.length; });

        function mostrarError(t) { err.textContent = t; err.hidden = !t; }
        campos.forEach(function (id) { $(id).addEventListener('input', function () { mostrarError(''); }); });

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var nombre = $('ctNombre').value.trim(), correo = $('ctCorreo').value.trim(), mensaje = $('ctMensaje').value.trim();
            if (nombre.length < 2) return mostrarError('Escribe tu nombre.');
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(correo)) return mostrarError('Escribe un correo válido para poder responderte.');
            if (mensaje.length < 10) return mostrarError('El mensaje debe tener al menos 10 caracteres.');
            mostrarError('');
            btn.disabled = true; btn.textContent = 'Enviando…';

            var datos = new URLSearchParams();
            datos.set('nombre', nombre); datos.set('correo', correo);
            datos.set('motivo', $('ctMotivo').value); datos.set('mensaje', mensaje); datos.set('web', $('ctWeb').value);

            fetch('/contacto/enviar', { method: 'POST', body: datos, headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })
                .then(function (r) { return r.json().catch(function () { return { ok: false }; }); })
                .then(function (d) {
                    if (d && d.ok) {
                        Array.prototype.forEach.call(form.children, function (c) { if (c !== exito) c.hidden = true; });
                        exito.hidden = false;
                    } else {
                        mostrarError((d && d.mensaje) || 'No pudimos enviar tu mensaje. Inténtalo de nuevo.');
                    }
                })
                .catch(function () { mostrarError('No pudimos enviar tu mensaje. Revisa tu conexión e inténtalo de nuevo.'); })
                .then(function () { btn.disabled = false; btn.textContent = 'Enviar mensaje'; });
        });

        $('ctOtro').addEventListener('click', function () {
            form.reset(); $('ctConteo').textContent = '0';
            Array.prototype.forEach.call(form.children, function (c) { c.hidden = false; });
            exito.hidden = true; err.hidden = true;
        });
    }
})();