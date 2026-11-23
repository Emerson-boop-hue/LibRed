// ACCESO DEL LECTOR (login): "Regístrate aquí" y "¿Olvidaste tu contraseña?".
(function () {
    'use strict';
    var $ = function (id) { return document.getElementById(id); };
    if (!$('regOverlay') || !$('recOverlay')) return;

    function post(url, body) {
        return fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
            .then(function (r) {
                return r.json().catch(function () { return {}; }).then(function (j) { return { ok: r.ok, data: j }; });
            });
    }
    function mensaje(d, def) { return (d && (d.error || d.mensaje || d.message)) || def; }
    function texto(el, msg) { el.textContent = msg || ''; if (el.classList.contains('al-error')) el.hidden = !msg; }
    function soloDigitos(v) { return (v || '').replace(/\D/g, ''); }
    function formatoCelular(v) {
        var d = soloDigitos(v).slice(0, 9);
        if (d.length > 6) return d.slice(0, 3) + ' ' + d.slice(3, 6) + ' ' + d.slice(6);
        if (d.length > 3) return d.slice(0, 3) + ' ' + d.slice(3);
        return d;
    }
    function celularOk(v) { return /^9\d{8}$/.test(soloDigitos(v)); }
    function claveOk(v) { return v.length >= 6 && v.length <= 20 && !/\s/.test(v); }
    function girar(btn, on, enCurso, normal) {
        btn.classList.toggle('al-cargando', !!on);
        btn.textContent = on ? enCurso : normal;
    }
    // Fuerza de la contraseña: 0 (vacía) a 4 barras, igual que en el registro de librería
    function fuerza(v) {
        if (!v) return 0;
        var n = 0;
        if (v.length >= 8) n++;
        if (/[a-z]/.test(v) && /[A-Z]/.test(v)) n++;
        if (/\d/.test(v)) n++;
        if (/[^A-Za-z0-9]/.test(v) || v.length >= 12) n++;
        return Math.max(n, 1);
    }
    function pintarFuerza(idBarras, v) { $(idBarras).dataset.n = String(fuerza(v)); }
    // Las animaciones del login se pausan mientras hay una ventana abierta (ver acceso_lector.css)
    function ventanaAbierta(abierta) { document.body.classList.toggle('al-abierto', abierta); }
    function mostrarPaso(paso, todos) { todos.forEach(function (p) { p.hidden = (p !== paso); }); }

    // Notificación de SMS (simulada)
    var sms = $('alSms'), smsTimer = null;
    function ocultarSms() {
        sms.classList.remove('ver');
        clearTimeout(smsTimer);
        setTimeout(function () { if (!sms.classList.contains('ver')) sms.hidden = true; }, 450);
    }
    function mostrarSms(m) {
        if (!m) return;
        $('alSmsRemitente').textContent = m.remitente || 'LibRed';
        $('alSmsTexto').textContent = m.texto || '';
        sms.hidden = false;
        void sms.offsetWidth; // reinicia la animación de entrada
        sms.classList.add('ver');
        clearTimeout(smsTimer);
        smsTimer = setTimeout(ocultarSms, 9000);
    }
    sms.addEventListener('click', ocultarSms);

    // Ojo de las contraseñas (los dos formularios)
    document.addEventListener('click', function (e) {
        var ojo = e.target.closest && e.target.closest('.al-ojo');
        if (!ojo) return;
        var input = $(ojo.getAttribute('data-ojo'));
        var oculta = input.type === 'password';
        input.type = oculta ? 'text' : 'password';
        ojo.firstElementChild.className = oculta ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    });

    // Tras registrarse o recuperar: deja listo el login (código y librería) para que solo falte la contraseña
    function irAlLogin(codigoLector, libreriaId) {
        var codigo = document.querySelector('#cardContent input[name="codigo"]');
        var libreria = document.querySelector('#cardContent select[name="libreriaId"]');
        var clave = document.querySelector('#cardContent input[name="password"]');
        if (codigo) codigo.value = codigoLector;
        if (libreria && libreriaId) libreria.value = String(libreriaId);
        if (clave) { clave.value = ''; clave.focus(); }
    }

    var rOv = $('regOverlay'), rForm = $('regForm'), rOk = $('regOk');
    var R = {
        nombres: $('regNombres'), apellidos: $('regApellidos'), celular: $('regCelular'),
        libreria: $('regLibreria'), clave: $('regClave')
    };
    var rErr = { nombres: $('regNombresErr'), apellidos: $('regApellidosErr'), nombre: $('regNombreErr'), celular: $('regCelularErr'), libreria: $('regLibreriaErr'), clave: $('regClaveErr') };
    var rEnviando = false, rLibreriaId = null;

    function rLimpiarErrores() {
        Object.keys(rErr).forEach(function (k) {
            rErr[k].textContent = '';
            if (R[k]) R[k].closest('.al-input').classList.remove('con-error');
        });
        // "nombre" = nombres + apellidos repetidos: se avisa a todo el ancho y se marcan los dos campos
        R.nombres.closest('.al-input').classList.remove('con-error');
        R.apellidos.closest('.al-input').classList.remove('con-error');
        texto($('regError'), '');
    }
    function rError(campo, msg) {
        var k = { libreriaId: 'libreria', password: 'clave' }[campo] || campo;
        if (k === 'nombre') {
            rErr.nombre.textContent = msg;
            R.nombres.closest('.al-input').classList.add('con-error');
            R.apellidos.closest('.al-input').classList.add('con-error');
        }
        else if (rErr[k]) { rErr[k].textContent = msg; R[k].closest('.al-input').classList.add('con-error'); }
        else texto($('regError'), msg);
    }
    function rActualizar() {
        $('regEnviar').disabled = !(R.nombres.value.trim() && R.apellidos.value.trim() && R.celular.value && R.libreria.value && R.clave.value);
    }
    function rAbrir() {
        Object.keys(R).forEach(function (k) { R[k].value = ''; });
        R.libreria.selectedIndex = 0;
        R.clave.type = 'password'; document.querySelector('[data-ojo="regClave"]').firstElementChild.className = 'fa-regular fa-eye';
        rLimpiarErrores(); rActualizar(); pintarFuerza('regFuerza', '');
        mostrarPaso(rForm, [rForm, rOk]);
        // Si el lector ya eligió su librería en el login, se deja marcada
        var elegida = document.querySelector('#cardContent select[name="libreriaId"]');
        if (elegida && elegida.value) R.libreria.value = elegida.value;
        rActualizar();
        rOv.hidden = false; ventanaAbierta(true);
        setTimeout(function () { R.nombres.focus(); }, 50);
    }
    function rCerrar() { rOv.hidden = true; ventanaAbierta(false); ocultarSms(); }

    R.celular.addEventListener('input', function () { R.celular.value = formatoCelular(R.celular.value); });
    Object.keys(R).forEach(function (k) {
        R[k].addEventListener('input', function () {
            rActualizar();
            if (k === 'clave') pintarFuerza('regFuerza', R.clave.value);
            rErr[k].textContent = ''; R[k].closest('.al-input').classList.remove('con-error'); texto($('regError'), '');
            if ((k === 'nombres' || k === 'apellidos') && rErr.nombre.textContent) {
                rErr.nombre.textContent = '';
                R.nombres.closest('.al-input').classList.remove('con-error');
                R.apellidos.closest('.al-input').classList.remove('con-error');
            }
        });
        R[k].addEventListener('change', rActualizar);
    });

    rForm.addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (rEnviando || $('regEnviar').disabled) return;
        rLimpiarErrores();
        var hayError = false;
        if (R.nombres.value.trim().length < 2) { rError('nombres', 'Ingresa tus nombres.'); hayError = true; }
        if (R.apellidos.value.trim().length < 2) { rError('apellidos', 'Ingresa tus apellidos.'); hayError = true; }
        if (!celularOk(R.celular.value)) { rError('celular', 'Ingresa un celular de 9 dígitos que empiece con 9.'); hayError = true; }
        if (!claveOk(R.clave.value)) { rError('clave', 'Usa entre 6 y 20 caracteres, sin espacios.'); hayError = true; }
        if (hayError) return;

        rEnviando = true; girar($('regEnviar'), true, 'Creando cuenta...', 'Crear cuenta');
        post('/acceso/lector/registro', {
            nombres: R.nombres.value, apellidos: R.apellidos.value, celular: soloDigitos(R.celular.value),
            libreriaId: Number(R.libreria.value), password: R.clave.value
        }).then(function (r) {
            rEnviando = false; girar($('regEnviar'), false, '', 'Crear cuenta');
            if (!r.ok) {
                if (r.data.errores) { Object.keys(r.data.errores).forEach(function (k) { rError(k, r.data.errores[k]); }); return; }
                rError(r.data.campo, mensaje(r.data, 'No se pudo crear la cuenta. Inténtalo de nuevo.')); return;
            }
            $('regCodigo').textContent = r.data.codigoLector;
            $('regLibNombre').textContent = r.data.libreria || '';
            rLibreriaId = r.data.libreriaId;
            mostrarPaso(rOk, [rForm, rOk]);
            mostrarSms(r.data.sms);
        }).catch(function () {
            rEnviando = false; girar($('regEnviar'), false, '', 'Crear cuenta');
            texto($('regError'), 'Sin conexión. Inténtalo de nuevo.');
        });
    });

    $('regIr').addEventListener('click', function () { rCerrar(); irAlLogin($('regCodigo').textContent, rLibreriaId); });
    $('regYaTengo').addEventListener('click', rCerrar);
    $('regCerrar').addEventListener('click', rCerrar);
    rOv.addEventListener('mousedown', function (e) { if (e.target === rOv) rCerrar(); });

    var cOv = $('recOverlay');
    var P = [$('recP1'), $('recP2'), $('recP3'), $('recP4')];
    var cCel = $('recCelular'), cCod = $('recCodigo'), cClave = $('recClave');
    var cEnviando = false, cReenvio = null, cLectorElegido = '';

    function cActualizar() {
        $('recEnviar').disabled = !celularOk(cCel.value);
        $('recVerificar').disabled = cCod.value.length === 0;
        $('recGuardar').disabled = cClave.value.length === 0;
    }
    function cAbrir() {
        cCel.value = cCod.value = cClave.value = ''; cClave.type = 'password';
        document.querySelector('[data-ojo="recClave"]').firstElementChild.className = 'fa-regular fa-eye';
        ['recError1', 'recError2', 'recError3'].forEach(function (id) { texto($(id), ''); });
        clearInterval(cReenvio); cLectorElegido = '';
        cActualizar(); pintarFuerza('recFuerza', ''); mostrarPaso(P[0], P);
        cOv.hidden = false; ventanaAbierta(true);
        setTimeout(function () { cCel.focus(); }, 50);
    }
    function cCerrar() { cOv.hidden = true; ventanaAbierta(false); clearInterval(cReenvio); ocultarSms(); }

    // Cuenta regresiva para poder pedir otro código
    function cEsperar(segundos) {
        var b = $('recReenviar'), falta = segundos;
        clearInterval(cReenvio);
        b.disabled = true;
        b.textContent = 'Reenviar código en ' + falta + ' s';
        cReenvio = setInterval(function () {
            falta--;
            if (falta <= 0) { clearInterval(cReenvio); b.disabled = false; b.textContent = 'Reenviar código'; }
            else b.textContent = 'Reenviar código en ' + falta + ' s';
        }, 1000);
    }

    // Pide el código al servidor. Con SMS simulado muestra la notificación y escribe el código en el campo.
    function cPedirCodigo(btn, normal, alTerminar) {
        if (cEnviando) return;
        cEnviando = true; texto($('recError1'), ''); texto($('recError2'), '');
        if (btn) girar(btn, true, 'Enviando código...', normal);
        post('/acceso/lector/recuperar/codigo', { celular: soloDigitos(cCel.value) }).then(function (r) {
            cEnviando = false; if (btn) girar(btn, false, '', normal);
            if (!r.ok) { alTerminar(false, mensaje(r.data, 'No se pudo enviar el código. Inténtalo de nuevo.')); return; }
            alTerminar(true);
            cEsperar(r.data.espera || 30);
            if (r.data.sms) {
                mostrarSms(r.data.sms);
                if (r.data.sms.codigo) {
                    setTimeout(function () {
                        cCod.value = r.data.sms.codigo; cActualizar(); cCod.classList.remove('con-error');
                        $('recVerificar').focus();
                    }, 1100);
                }
            }
        }).catch(function () {
            cEnviando = false; if (btn) girar(btn, false, '', normal);
            alTerminar(false, 'Sin conexión. Inténtalo de nuevo.');
        });
    }

    cCel.addEventListener('input', function () { cCel.value = formatoCelular(cCel.value); cActualizar(); texto($('recError1'), ''); });
    cCod.addEventListener('input', function () { cCod.value = soloDigitos(cCod.value).slice(0, 6); cActualizar(); texto($('recError2'), ''); });
    cClave.addEventListener('input', function () { cActualizar(); pintarFuerza('recFuerza', cClave.value); texto($('recError3'), ''); });

    P[0].addEventListener('submit', function (ev) {
        ev.preventDefault();
        if ($('recEnviar').disabled) return;
        cPedirCodigo($('recEnviar'), 'Enviar código', function (ok, msg) {
            if (!ok) { texto($('recError1'), msg); return; }
            cCod.value = ''; cActualizar(); mostrarPaso(P[1], P); cCod.focus();
        });
    });
    $('recReenviar').addEventListener('click', function () {
        cPedirCodigo(null, '', function (ok, msg) { if (!ok) texto($('recError2'), msg); });
    });
    $('recOtro').addEventListener('click', function () { clearInterval(cReenvio); mostrarPaso(P[0], P); cCel.focus(); });

    P[1].addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (cEnviando || $('recVerificar').disabled) return;
        if (cCod.value.length !== 6) { texto($('recError2'), 'El código tiene 6 dígitos.'); cCod.focus(); return; }
        cEnviando = true; texto($('recError2'), ''); girar($('recVerificar'), true, 'Verificando...', 'Verificar código');
        post('/acceso/lector/recuperar/verificar', { celular: soloDigitos(cCel.value), codigo: cCod.value }).then(function (r) {
            cEnviando = false; girar($('recVerificar'), false, '', 'Verificar código');
            if (!r.ok) { texto($('recError2'), mensaje(r.data, 'No se pudo verificar el código.')); return; }
            var cuentas = r.data.cuentas || [], caja = $('recCuentas');
            caja.innerHTML = ''; cLectorElegido = '';
            if (cuentas.length > 1) {
                cuentas.forEach(function (c) {
                    var lab = document.createElement('label'); lab.className = 'al-cuenta';
                    var rad = document.createElement('input'); rad.type = 'radio'; rad.name = 'al-cuenta'; rad.value = c.codigoLector;
                    rad.addEventListener('change', function () { cLectorElegido = c.codigoLector; texto($('recError3'), ''); });
                    var cuerpo = document.createElement('span');
                    var b = document.createElement('b'); b.textContent = c.codigoLector + ' · ' + c.nombre;
                    var s = document.createElement('small'); s.textContent = (c.librerias || []).join(', ');
                    cuerpo.appendChild(b); cuerpo.appendChild(s);
                    lab.appendChild(rad); lab.appendChild(cuerpo); caja.appendChild(lab);
                });
                caja.hidden = false;
            } else {
                caja.hidden = true;
                cLectorElegido = cuentas.length === 1 ? cuentas[0].codigoLector : '';
            }
            cClave.value = ''; cActualizar(); mostrarPaso(P[2], P); cClave.focus();
        }).catch(function () {
            cEnviando = false; girar($('recVerificar'), false, '', 'Verificar código');
            texto($('recError2'), 'Sin conexión. Inténtalo de nuevo.');
        });
    });

    P[2].addEventListener('submit', function (ev) {
        ev.preventDefault();
        if (cEnviando || $('recGuardar').disabled) return;
        if (!$('recCuentas').hidden && !cLectorElegido) { texto($('recError3'), 'Elige la cuenta que quieres recuperar.'); return; }
        if (!claveOk(cClave.value)) { texto($('recError3'), 'La contraseña debe tener entre 6 y 20 caracteres, sin espacios.'); cClave.focus(); return; }
        cEnviando = true; texto($('recError3'), ''); girar($('recGuardar'), true, 'Guardando...', 'Cambiar contraseña');
        post('/acceso/lector/recuperar/restablecer', {
            celular: soloDigitos(cCel.value), codigo: cCod.value, codigoLector: cLectorElegido, password: cClave.value
        }).then(function (r) {
            cEnviando = false; girar($('recGuardar'), false, '', 'Cambiar contraseña');
            if (!r.ok) {
                if (r.data.campo === 'codigo') { texto($('recError2'), mensaje(r.data, 'El código ya no es válido.')); mostrarPaso(P[1], P); return; }
                texto($('recError3'), mensaje(r.data, 'No se pudo cambiar la contraseña.')); return;
            }
            $('recCodigoLector').textContent = r.data.codigoLector;
            clearInterval(cReenvio);
            mostrarPaso(P[3], P);
        }).catch(function () {
            cEnviando = false; girar($('recGuardar'), false, '', 'Cambiar contraseña');
            texto($('recError3'), 'Sin conexión. Inténtalo de nuevo.');
        });
    });

    $('recIr').addEventListener('click', function () { cCerrar(); irAlLogin($('recCodigoLector').textContent, null); });
    $('recCerrar').addEventListener('click', cCerrar);
    cOv.addEventListener('mousedown', function (e) { if (e.target === cOv) cCerrar(); });

    // Enlaces del login (delegados: el formulario se reemplaza al cambiar entre lector y administrador)
    document.addEventListener('click', function (e) {
        var a = e.target.closest && e.target.closest('[data-registro-lector], [data-recuperar-lector]');
        if (!a) return;
        e.preventDefault();
        if (a.hasAttribute('data-registro-lector')) rAbrir(); else cAbrir();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape') return;
        if (!rOv.hidden) rCerrar();
        if (!cOv.hidden) cCerrar();
    });
})();