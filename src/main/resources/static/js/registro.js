// PÁGINA DE REGISTRO DE LIBRERÍAS (/registro) Orden: 1) Cuenta (correo + código) 2) Plan 3) Pago 4) Librería 5) Listo
// Todo lo importante (códigos, pagos, creación de la librería) lo decide el servidor; aquí solo se ayuda a escribir
// bien los datos y se muestran los resultados.
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const soloDigitos = (v) => (v || "").replace(/\D/g, "");
  const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

  const TOKEN_KEY = "libredRegistroToken";
  const NOMBRES_PASO = ["Cuenta", "Plan", "Pago", "Librería", "Perfil"];
  const CORREO_RE = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

  const estado = {
    paso: 1,
    token: null,
    correo: "",
    plan: null,
    planes: [],
    metodo: "TARJETA",
    ocupado: false,
    esperaCorreo: null,  // temporizador del botón "Reenviar código"
    yapeListo: false,    // ya se pidió el código de aprobación de Yape
    yapeEspera: 0,       // segundos que faltan para poder pedir otro código
    yapeTimer: null,
    paypal: null,
  };

  let otpCorreo = null;
  let otpYape = null;

  function guardarToken(t) { try { sessionStorage.setItem(TOKEN_KEY, t); } catch (e) { /* sin almacenamiento */ } }
  function leerToken() { try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; } }
  function borrarToken() { try { sessionStorage.removeItem(TOKEN_KEY); } catch (e) { } }

  // Anillo giratorio dentro de un botón mientras se espera al servidor
  function girar(id, on) {
    const b = $(id);
    if (b) b.classList.toggle("rg-cargando", !!on);
  }

  async function llamar(url, opciones) {
    try {
      const res = await fetch(url, opciones);
      const data = await res.json().catch(() => ({}));
      if (!res.ok && !data.error) data.error = "No se pudo completar la operación. Intenta nuevamente.";
      return { ok: res.ok, status: res.status, data: data };
    } catch (e) {
      return { ok: false, status: 0, data: { error: "No se pudo conectar con el servidor. Intenta nuevamente." } };
    }
  }
  const postJson = (url, cuerpo) => llamar(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cuerpo),
  });

  function luhn(numero) {
    let suma = 0;
    let doblar = false;
    for (let i = numero.length - 1; i >= 0; i--) {
      let d = Number(numero[i]);
      if (doblar) { d *= 2; if (d > 9) d -= 9; }
      suma += d;
      doblar = !doblar;
    }
    return suma % 10 === 0;
  }

  function vencimientoValido(v) {
    const m = /^(\d{2})\/(\d{2})$/.exec(v);
    if (!m) return false;
    const mes = parseInt(m[1], 10);
    const anio = 2000 + parseInt(m[2], 10);
    if (mes < 1 || mes > 12) return false;
    const hoy = new Date();
    const anioActual = hoy.getFullYear();
    const mesActual = hoy.getMonth() + 1;
    return anio > anioActual || (anio === anioActual && mes >= mesActual);
  }

  // RUC peruano: 11 dígitos, empieza con 10/15/16/17/20 y cumple el dígito verificador
  function rucValido(v) {
    if (!/^\d{11}$/.test(v)) return false;
    if (["10", "15", "16", "17", "20"].indexOf(v.slice(0, 2)) < 0) return false;
    const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
    let suma = 0;
    for (let i = 0; i < 10; i++) suma += Number(v[i]) * pesos[i];
    let digito = 11 - (suma % 11);
    if (digito === 10) digito = 0; else if (digito === 11) digito = 1;
    return digito === Number(v[10]);
  }

  // REGLAS DE CADA CAMPO (el error solo se muestra al salir del campo)
  const REGLAS = {
    rgCorreo:    { ok: (v) => CORREO_RE.test(v.trim()), msg: "Ingresa un correo válido." },
    rgFa:    { ok: (v) => soloDigitos(v).length === 16 && luhn(soloDigitos(v)), msg: "Número de tarjeta inválido." },
    rgFb:   { ok: (v) => /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' .-]{3,60}$/.test(v.trim()), msg: "Ingresa el nombre del titular." },
    rgFc:     { ok: vencimientoValido, msg: "Fecha inválida o tarjeta vencida." },
    rgFd:       { ok: (v) => /^\d{3}$/.test(v), msg: "El CVV tiene 3 dígitos." },
    rgCelular:   { ok: (v) => /^9\d{8}$/.test(soloDigitos(v)), msg: "Ingresa un celular de 9 dígitos que empiece con 9." },
    rgNombre:    { ok: (v) => v.trim().length >= 2, msg: "Ingresa el nombre comercial." },
    rgRuc:       { ok: rucValido, msg: "El RUC no es válido. Revisa los dígitos." },
    rgDireccion: { ok: (v) => v.trim().length >= 5, msg: "Ingresa la dirección del establecimiento." },
    rgTelefono:  { ok: (v) => soloDigitos(v) === "" || /^\d{7,9}$/.test(soloDigitos(v)), msg: "El teléfono debe tener entre 7 y 9 dígitos." },
    rgCorreoContacto: { ok: (v) => v.trim() === "" || CORREO_RE.test(v.trim()), msg: "Ingresa un correo válido." },
    rgHorario:   { ok: (v) => v.trim().length <= 120, msg: "Máximo 120 caracteres." },
    rgDescripcion: { ok: (v) => v.trim().length <= 300, msg: "Máximo 300 caracteres." },
    rgPassword:  { ok: (v) => v.length >= 8 && v.length <= 20 && !/\s/.test(v), msg: "Usa entre 8 y 20 caracteres, sin espacios." },
  };

  const FORMATO = {
    rgFa: (v) => soloDigitos(v).slice(0, 16).replace(/(.{4})(?=.)/g, "$1 "),
    rgFc: (v) => {
      const d = soloDigitos(v).slice(0, 4);
      return d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
    },
    rgFd: (v) => soloDigitos(v).slice(0, 3),
    rgCelular: (v) => soloDigitos(v).slice(0, 9).replace(/(\d{3})(?=\d)/g, "$1 "),
    rgRuc: (v) => soloDigitos(v).slice(0, 11),
    rgTelefono: (v) => soloDigitos(v).slice(0, 9).replace(/(\d{3})(?=\d)/g, "$1 "),
  };

  // Campo del servidor -> campo de la pantalla
  const CAMPO_A_INPUT = {
    correo: "rgCorreo", numero: "rgFa", titular: "rgFb", vence: "rgFc", cvv: "rgFd",
    celular: "rgCelular", nombreComercial: "rgNombre", ruc: "rgRuc", direccion: "rgDireccion", password: "rgPassword",
    telefono: "rgTelefono", correoContacto: "rgCorreoContacto", descripcion: "rgDescripcion", horario: "rgHorario",
  };

  const valido = (id) => REGLAS[id].ok($(id).value);

  function marcar(id, mensaje) {
    const input = $(id);
    const caja = input.closest(".rg-input");
    const err = $(id + "Err");
    if (caja) caja.classList.toggle("invalido", !!mensaje);
    if (err) err.textContent = mensaje || "";
  }

  function configurarCampo(id) {
    const input = $(id);
    input.addEventListener("input", () => {
      if (FORMATO[id]) input.value = FORMATO[id](input.value);
      // Si ya tenía un error visible, se revisa al escribir para que desaparezca apenas esté bien
      const caja = input.closest(".rg-input");
      if (caja && caja.classList.contains("invalido")) marcar(id, valido(id) ? "" : REGLAS[id].msg);
      if (id === "rgFa") actualizarMarcaTarjeta();
      if (id === "rgDescripcion") $("rgDescripcionCont").textContent = input.value.length + " / 300";
      if (id === "rgPassword") actualizarFuerza();
      if (id === "rgCelular" && estado.yapeListo) reiniciarYape();
      ocultar("rgPagoError");
      ocultar("rgFinError");
      actualizar();
    });
    input.addEventListener("blur", () => {
      if (input.value.trim() !== "") marcar(id, valido(id) ? "" : REGLAS[id].msg);
    });
    input.addEventListener("keydown", (e) => {
      if (e.key !== "Enter") return;
      e.preventDefault();
      const boton = botonPrincipalDelPaso();
      if (boton && !boton.disabled) boton.click();
    });
  }

  function ocultar(id) { const e = $(id); if (e) e.hidden = true; }
  function mostrarBloque(id, texto) { const e = $(id); e.textContent = texto; e.hidden = false; }

  function botonPrincipalDelPaso() {
    if (estado.paso === 1) return $("rgVerifForm").hidden ? $("rgEnviarCodigo") : $("rgVerificar");
    if (estado.paso === 2) return $("rgPlanSiguiente");
    if (estado.paso === 3) return $("rgPagar");
    if (estado.paso === 4) return $("rgContinuarLib");
    if (estado.paso === 5) return $("rgFinalizar");
    return null;
  }

  function crearOtp(contenedor, alCambiar) {
    contenedor.textContent = "";
    const cajas = [];
    for (let i = 0; i < 6; i++) {
      const inp = document.createElement("input");
      inp.type = "text";
      inp.inputMode = "numeric";
      inp.maxLength = 1;
      inp.autocomplete = "off";
      inp.name = "rg-otp-" + contenedor.id + "-" + i;
      inp.setAttribute("aria-label", "Dígito " + (i + 1));
      contenedor.appendChild(inp);
      cajas.push(inp);

      inp.addEventListener("input", () => {
        inp.value = soloDigitos(inp.value).slice(0, 1);
        contenedor.classList.remove("invalido");
        if (inp.value && i < 5) cajas[i + 1].focus();
        alCambiar(false);
      });
      inp.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !inp.value && i > 0) {
          cajas[i - 1].value = "";
          cajas[i - 1].focus();
          alCambiar(false);
        } else if (e.key === "ArrowLeft" && i > 0) {
          cajas[i - 1].focus();
        } else if (e.key === "ArrowRight" && i < 5) {
          cajas[i + 1].focus();
        } else if (e.key === "Enter") {
          e.preventDefault();
          alCambiar(true);
        }
      });
      inp.addEventListener("paste", (e) => {
        e.preventDefault();
        const texto = soloDigitos((e.clipboardData || window.clipboardData).getData("text")).slice(0, 6);
        texto.split("").forEach((c, k) => { cajas[k].value = c; });
        cajas[Math.min(texto.length, 5)].focus();
        contenedor.classList.remove("invalido");
        alCambiar(false);
      });
      inp.addEventListener("focus", () => inp.select());
    }
    return {
      valor: () => cajas.map((c) => c.value).join(""),
      limpiar: () => { cajas.forEach((c) => { c.value = ""; }); contenedor.classList.remove("invalido"); },
      enfocar: () => cajas[0].focus(),
      error: () => { contenedor.classList.remove("invalido"); void contenedor.offsetWidth; contenedor.classList.add("invalido"); },
    };
  }

  function planActual() { return estado.planes.find((p) => p.codigo === estado.plan) || null; }

  function ir(n) {
    estado.paso = n;
    document.querySelectorAll(".rg-panel").forEach((p) => p.classList.toggle("activo", Number(p.dataset.panel) === n));
    document.querySelectorAll("#rgPasos li").forEach((li) => {
      const k = Number(li.dataset.paso);
      li.classList.toggle("activo", k === n);
      li.classList.toggle("hecho", k < n || n === 6);
    });
    const visible = Math.min(n, 5);
    $("rgMovilTxt").textContent = n === 6 ? "¡Listo!" : "Paso " + visible + " de 5 · " + NOMBRES_PASO[visible - 1];
    $("rgMovilBarra").style.width = (n === 6 ? 100 : visible * 20) + "%";
    const caja = document.querySelector(".rg-caja");
    if (caja) caja.classList.toggle("ancha", n === 2);
    renderPedido();
    if (n === 5) $("rgCorreoCuentaTxt").textContent = estado.correo || "";
    if (n === 3) notaRenovacion();
    actualizar();
    window.scrollTo(0, 0);

    const foco = { 1: "rgCorreo", 3: "rgFa", 4: "rgNombre" }[n];
    if (n === 1 && !$("rgVerifForm").hidden) { if (otpCorreo) otpCorreo.enfocar(); }
    else if (foco) setTimeout(() => { if ($(foco)) $(foco).focus(); }, 60);
  }

  function renderPedido() {
    const p = planActual();
    const ver = !!p && estado.paso >= 2 && estado.paso <= 5;
    $("rgPedido").hidden = !ver;
    if (!ver) return;
    $("rgPedidoPlan").textContent = p.nombre;
    $("rgPedidoMonto").textContent = "S/ " + p.precio;
    $("rgPedidoNota").textContent = "Acceso por " + p.dias + " días" + (p.ahorro ? " · ahorras S/ " + p.ahorro : "");
    $("rgRpPlan").textContent = p.nombre + " · " + p.dias + " días";
    const fin = new Date(); fin.setDate(fin.getDate() + p.dias);
    $("rgRpAcceso").textContent = String(fin.getDate()).padStart(2, "0") + "/" + String(fin.getMonth() + 1).padStart(2, "0") + "/" + fin.getFullYear();
    $("rgRpTotal").textContent = "S/ " + p.precio;
  }

  // Activa o desactiva todos los botones según lo que ya está completo
  function actualizar() {
    const p = planActual();
    $("rgEnviarCodigo").disabled = estado.ocupado || !valido("rgCorreo") || !$("rgAcepta").checked;
    $("rgEnviarCodigo").textContent = $("rgEnviarCodigo").classList.contains("rg-cargando") ? "Enviando código..." : "Enviar código";
    $("rgVerificar").disabled = estado.ocupado || !otpCorreo || otpCorreo.valor().length !== 6;
    $("rgPlanSiguiente").disabled = !p;

    const m = estado.metodo;
    const monto = p ? "S/ " + p.precio : "";
    $("rgPagar").textContent = m === "PAYPAL" ? "Continuar con PayPal" : "Pagar " + monto;
    let listo = false;
    if (m === "TARJETA") listo = ["rgFa", "rgFb", "rgFc", "rgFd"].every(valido);
    else if (m === "YAPE") listo = estado.yapeListo && !!otpYape && otpYape.valor().length === 6;
    else listo = true;
    $("rgPagar").disabled = estado.ocupado || !p || !listo;
    $("rgYapeSolicitar").disabled = estado.ocupado || !valido("rgCelular") || estado.yapeEspera > 0;

    const opcionalesOk = valido("rgTelefono") && valido("rgHorario") && valido("rgDescripcion")
      && ($("rgUsarCuenta").checked || valido("rgCorreoContacto"));
    const requeridosOk = ["rgNombre", "rgRuc", "rgDireccion", "rgPassword"].every(valido);
    $("rgContinuarLib").disabled = !requeridosOk;
    $("rgFinalizar").disabled = estado.ocupado || !opcionalesOk || !requeridosOk;
    $("rgFinalizar").textContent = estado.ocupado && estado.paso === 5 ? "Creando tu librería..." : "Finalizar registro";
    $("rgPerfilAtras").disabled = estado.ocupado;

    $("rgPpEntrar").disabled = $("rgPpCorreo").value.trim() === "" || $("rgPpClave").value === "";
  }

  function iniciarEsperaCorreo(segundos) {
    clearInterval(estado.esperaCorreo);
    let resta = segundos;
    const boton = $("rgReenviar");
    const pintar = () => {
      boton.disabled = resta > 0;
      boton.textContent = resta > 0 ? "Reenviar código en " + resta + " s" : "Reenviar código";
    };
    pintar();
    estado.esperaCorreo = setInterval(() => {
      resta--;
      pintar();
      if (resta <= 0) clearInterval(estado.esperaCorreo);
    }, 1000);
  }

  async function enviarCodigo(reenvio) {
    if (estado.ocupado) return;
    const correo = $("rgCorreo").value.trim().toLowerCase();
    const idBoton = reenvio ? "rgReenviar" : "rgEnviarCodigo";
    estado.ocupado = true;
    girar(idBoton, true);
    actualizar();
    const r = await postJson("/registro/correo", { correo: correo, acepta: $("rgAcepta").checked });
    estado.ocupado = false;
    girar(idBoton, false);

    if (!r.ok) {
      if (r.data.campo === "terminos") $("rgAceptaErr").textContent = r.data.error;
      else if (reenvio) $("rgOtpErr").textContent = r.data.error;
      else marcar("rgCorreo", r.data.error);
      actualizar();
      return;
    }
    estado.correo = r.data.correo;
    $("rgCorreoVerif").textContent = estado.correo;
    $("rgCuentaForm").hidden = true;
    $("rgVerifForm").hidden = false;
    otpCorreo.limpiar();
    $("rgOtpErr").textContent = "";
    iniciarEsperaCorreo(r.data.espera || 60);
    actualizar();
    otpCorreo.enfocar();
  }

  async function verificarCodigo() {
    if (estado.ocupado || otpCorreo.valor().length !== 6) return;
    estado.ocupado = true;
    $("rgVerificar").textContent = "Verificando...";
    girar("rgVerificar", true);
    actualizar();
    const r = await postJson("/registro/verificar", { correo: estado.correo, codigo: otpCorreo.valor(), acepta: true });
    estado.ocupado = false;
    girar("rgVerificar", false);
    $("rgVerificar").textContent = "Verificar y continuar";

    if (!r.ok) {
      $("rgOtpErr").textContent = r.data.error;
      otpCorreo.error();
      actualizar();
      return;
    }
    estado.token = r.data.token;
    guardarToken(r.data.token);
    if (r.data.plan) estado.plan = r.data.plan;
    marcarPlan();
    ir(r.data.estado === "PAGADO" ? 4 : 2);
  }

  function cambiarCorreo() {
    clearInterval(estado.esperaCorreo);
    $("rgVerifForm").hidden = true;
    $("rgCuentaForm").hidden = false;
    $("rgOtpErr").textContent = "";
    actualizar();
    $("rgCorreo").focus();
  }

  function sesionVencida(mensaje) {
    borrarToken();
    estado.token = null;
    estado.plan = null;
    estado.metodo = "TARJETA";
    cambiarCorreo();
    $("rgCorreo").value = "";
    marcar("rgCorreo", mensaje || "Tu sesión de registro venció. Verifica tu correo nuevamente.");
    ir(1);
  }

  function el(tag, clase, texto) {
    const e = document.createElement(tag);
    if (clase) e.className = clase;
    if (texto !== undefined) e.textContent = texto;
    return e;
  }

  function crearTarjetaPlan(p) {
    const b = el("button", "rg-plan");
    b.type = "button";
    b.dataset.plan = p.codigo;
    if (p.ahorro) b.appendChild(el("span", "rg-plan-etiqueta", "Ahorras S/ " + p.ahorro));
    const marca = el("span", "rg-plan-marca");
    marca.innerHTML = '<i class="fa-solid fa-check"></i>';
    b.appendChild(marca);
    b.appendChild(el("h3", "", p.nombre));
    const precio = el("div", "rg-plan-precio", "S/ " + p.precio);
    precio.appendChild(el("small", "", p.periodo));
    b.appendChild(precio);

    const puntos = [["fa-check", "Acceso completo por " + p.dias + " días"], ["fa-check", "Todos los módulos, sin límites"]];
    if (p.equivalenteMensual) {
      puntos.push(["fa-star", "Equivale a solo S/ " + p.equivalenteMensual + " al mes", "estrella"]);
      const mens = estado.planes.find((x) => !x.equivalenteMensual);
      if (mens && p.ahorro && Number(mens.precio) > 0) {
        const gratis = Number(p.ahorro) / Number(mens.precio);
        if (Math.abs(gratis - Math.round(gratis)) < 0.05 && gratis >= 1) puntos.push(["fa-gift", "Pagas " + (12 - Math.round(gratis)) + " meses y usas 12"]);
      }
    } else {
      puntos.push(["fa-check", "Ideal para empezar y conocer LibRed"]);
    }
    const lista = el("ul", "rg-plan-lista");
    puntos.forEach((pt) => {
      const li = el("li", pt[2] || "");
      li.innerHTML = '<i class="fa-solid ' + pt[0] + '"></i>';
      li.appendChild(document.createTextNode(pt[1]));
      lista.appendChild(li);
    });
    b.appendChild(lista);

    b.addEventListener("click", () => {
      estado.plan = p.codigo;
      marcarPlan();
      renderPedido();
      actualizar();
    });
    return b;
  }

  function marcarPlan() {
    document.querySelectorAll(".rg-plan").forEach((b) => b.classList.toggle("elegido", b.dataset.plan === estado.plan));
  }

  async function cargarPlanes() {
    const cont = $("rgPlanes");
    const r = await llamar("/suscripcion/planes", { cache: "no-store" });
    if (!r.ok || !Array.isArray(r.data)) {
      cont.textContent = "No se pudieron cargar los planes. Recarga la página.";
      return;
    }
    estado.planes = r.data;
    cont.textContent = "";
    estado.planes.forEach((p) => cont.appendChild(crearTarjetaPlan(p)));
    marcarPlan();
  }

  function elegirMetodo(m) {
    estado.metodo = m;
    document.querySelectorAll(".rg-metodo").forEach((b) => b.classList.toggle("activo", b.dataset.metodo === m));
    document.querySelectorAll(".rg-mpanel").forEach((p) => p.classList.toggle("activo", p.dataset.mpanel === m));
    ocultar("rgPagoError");
    notaRenovacion();
    actualizar();
  }

  // Aviso de cómo se renueva la membresía según el método (tarjeta y PayPal: automática; Yape: manual)
  function notaRenovacion() {
    const nota = $("rgAutoNota");
    if (!nota) return;
    const p = planActual();
    const periodo = p && p.codigo === "ANUAL" ? "cada año" : "cada mes";
    nota.textContent = estado.metodo === "YAPE"
      ? "Yape no permite cobros automáticos: renovarás tú mismo y te avisaremos por correo antes de que venza."
      : "Tu membresía se renovará automáticamente " + periodo + ". Puedes cancelarlo cuando quieras desde «Ver plan actual».";
  }

  function actualizarMarcaTarjeta() {
    const n = soloDigitos($("rgFa").value);
    const icono = $("rgMarcaIcono");
    const visa = /^4/.test(n);
    const mc = /^5[1-5]/.test(n) || /^2(22[1-9]|2[3-9]\d|[3-6]\d\d|7[01]\d|720)/.test(n);
    icono.className = visa ? "fa-brands fa-cc-visa" : mc ? "fa-brands fa-cc-mastercard" : "fa-regular fa-credit-card";
    icono.style.color = visa ? "#1a1f71" : mc ? "#eb001b" : "";
    icono.style.fontSize = (visa || mc) ? "22px" : "";
  }

  function mostrarProcesando(ver) { $("rgProcesando").hidden = !ver; }

  async function pagar(extra) {
    const p = planActual();
    if (estado.ocupado || !p) return;
    const cuerpo = { token: estado.token, plan: p.codigo, metodo: estado.metodo };
    if (estado.metodo === "TARJETA") {
      cuerpo.numero = soloDigitos($("rgFa").value);
      cuerpo.titular = $("rgFb").value.trim();
      cuerpo.vence = $("rgFc").value;
      cuerpo.cvv = $("rgFd").value;
    } else if (estado.metodo === "YAPE") {
      cuerpo.celular = soloDigitos($("rgCelular").value);
      cuerpo.codigo = otpYape.valor();
    } else if (extra) {
      cuerpo.correoPaypal = extra.correoPaypal;
      cuerpo.clavePaypal = extra.clavePaypal;
    }

    estado.ocupado = true;
    ocultar("rgPagoError");
    girar("rgPagar", true);
    actualizar();
    mostrarProcesando(true);
    const resultados = await Promise.all([postJson("/registro/pago", cuerpo), esperar(1800)]);
    const r = resultados[0];
    mostrarProcesando(false);
    girar("rgPagar", false);
    estado.ocupado = false;

    if (!r.ok) {
      errorDePago(r);
      actualizar();
      return;
    }
    mostrarPagoAprobado(r.data);
    ir(4);
  }

  function errorDePago(r) {
    if (r.status === 401 && !r.data.campo) { sesionVencida(r.data.error); return; }
    if (r.data.campo === "codigo") {
      otpYape.error();
      $("rgCodigoErr").textContent = r.data.error;
      return;
    }
    const id = CAMPO_A_INPUT[r.data.campo];
    if (id && $(id)) marcar(id, r.data.error);
    else mostrarBloque("rgPagoError", r.data.error);
  }

  function mostrarPagoAprobado(d) {
    $("rgPagoOkTxt").textContent = "Pago aprobado · N.º " + d.operacion + " · " + d.metodo;
    $("rgPagoOk").hidden = false;
  }

  function reiniciarYape() {
    estado.yapeListo = false;
    clearInterval(estado.yapeTimer);
    estado.yapeEspera = 0;
    $("rgYapeSolicitar").textContent = "Obtener código de aprobación";
    $("rgYapeApp").hidden = true;
    $("rgYapeIngreso").hidden = true;
    $("rgCodigoErr").textContent = "";
    if (otpYape) otpYape.limpiar();
  }

  function esperaYape(segundos) {
    clearInterval(estado.yapeTimer);
    estado.yapeEspera = segundos;
    const boton = $("rgYapeSolicitar");
    const pintar = () => {
      boton.textContent = estado.yapeEspera > 0 ? "Obtener otro código (" + estado.yapeEspera + " s)" : "Obtener otro código";
      actualizar();
    };
    pintar();
    estado.yapeTimer = setInterval(() => {
      estado.yapeEspera--;
      pintar();
      if (estado.yapeEspera <= 0) clearInterval(estado.yapeTimer);
    }, 1000);
  }

  async function solicitarYape() {
    if (estado.ocupado || !valido("rgCelular")) return;
    estado.ocupado = true;
    girar("rgYapeSolicitar", true);
    actualizar();
    const r = await postJson("/registro/yape/solicitar", { token: estado.token, celular: soloDigitos($("rgCelular").value) });
    estado.ocupado = false;
    girar("rgYapeSolicitar", false);
    if (!r.ok) {
      if (r.status === 401 && !r.data.campo) { sesionVencida(r.data.error); return; }
      if (r.data.campo === "celular") marcar("rgCelular", r.data.error);
      else mostrarBloque("rgPagoError", r.data.error);
      actualizar();
      return;
    }
    ocultar("rgPagoError");
    estado.yapeListo = true;
    const c = r.data.codigo;
    $("rgYapeCodigoVista").textContent = c.slice(0, 3) + " " + c.slice(3);
    $("rgYapeApp").hidden = false;
    $("rgYapeIngreso").hidden = false;
    $("rgCodigoErr").textContent = "";
    otpYape.limpiar();
    esperaYape(r.data.espera || 10);
    otpYape.enfocar();
  }

  function abrirPaypal() {
    $("rgPpCorreo").value = "";
    $("rgPpClave").value = "";
    $("rgPpErr").textContent = "";
    $("rgPpLogin").hidden = false;
    $("rgPpRevisar").hidden = true;
    $("rgPaypal").hidden = false;
    actualizar();
    $("rgPpCorreo").focus();
  }

  function cerrarPaypal() { $("rgPaypal").hidden = true; }

  async function paypalEntrar() {
    const p = planActual();
    if (!p || $("rgPpEntrar").disabled) return;
    const correo = $("rgPpCorreo").value.trim().toLowerCase();
    const clave = $("rgPpClave").value;
    $("rgPpEntrar").disabled = true;
    girar("rgPpEntrar", true);
    $("rgPpErr").textContent = "";
    const r = await postJson("/registro/paypal/validar", { token: estado.token, plan: p.codigo, correo: correo, clave: clave });
    girar("rgPpEntrar", false);
    if (!r.ok) {
      if (r.status === 401 && !r.data.campo) { cerrarPaypal(); sesionVencida(r.data.error); return; }
      $("rgPpErr").textContent = r.data.error;
      actualizar();
      return;
    }
    estado.paypal = { correo: correo, clave: clave, nombre: r.data.nombre, montoUsd: r.data.montoUsd };
    $("rgPpNombre").textContent = r.data.nombre;
    $("rgPpConcepto").textContent = "Membresía LibRed · " + p.nombre;
    $("rgPpTotal").textContent = "US$ " + r.data.montoUsd + "  (S/ " + p.precio + ")";
    $("rgPpLogin").hidden = true;
    $("rgPpRevisar").hidden = false;
  }

  function paypalPagar() {
    if (!estado.paypal) return;
    const datos = { correoPaypal: estado.paypal.correo, clavePaypal: estado.paypal.clave };
    cerrarPaypal();
    pagar(datos);
  }

  // HORARIO DE ATENCIÓN (selector por días; se guarda como texto legible)
  const DIAS_CORTO = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
  const DIAS_LARGO = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
  const hor = { filas: [] };

  function minutosATexto(m) { return Math.floor(m / 60) + ":" + String(m % 60).padStart(2, "0"); }

  function llenarHoras(sel, desde, hasta, valor) {
    sel.textContent = "";
    for (let m = desde; m <= hasta; m += 30) {
      const o = document.createElement("option");
      o.value = m; o.textContent = minutosATexto(m);
      if (m === valor) o.selected = true;
      sel.appendChild(o);
    }
  }

  function textoHorario() {
    const act = hor.filas.map((f, i) => ({ i, on: f.sw.checked, a: Number(f.ap.value), c: Number(f.ci.value) })).filter((x) => x.on);
    const grupos = [];
    act.forEach((d) => {
      const g = grupos[grupos.length - 1];
      if (g && g.fin === d.i - 1 && g.a === d.a && g.c === d.c) g.fin = d.i;
      else grupos.push({ ini: d.i, fin: d.i, a: d.a, c: d.c });
    });
    return grupos.map((g) => {
      const dias = g.ini === g.fin ? DIAS_CORTO[g.ini]
        : g.fin === g.ini + 1 ? DIAS_CORTO[g.ini] + " y " + DIAS_CORTO[g.fin]
        : DIAS_CORTO[g.ini] + " a " + DIAS_CORTO[g.fin];
      return dias + " " + minutosATexto(g.a) + "–" + minutosATexto(g.c);
    }).join("; ");
  }

  function refrescarHorario() {
    hor.filas.forEach((f) => {
      const on = f.sw.checked;
      f.fila.classList.toggle("off", !on);
      f.horas.hidden = !on;
      f.cerrado.hidden = on;
    });
    const t = textoHorario();
    $("rgHorario").value = t;
    $("rgHorResumen").textContent = t ? "Se mostrará así: " + t : "";
    marcar("rgHorario", t.length > 120 ? "El horario es demasiado largo. Simplifica los días." : "");
    actualizar();
  }

  function aplicarPreset(p) {
    const base = hor.filas.find((f) => f.sw.checked);
    const ap = base ? Number(base.ap.value) : 540, ci = base ? Number(base.ci.value) : 1080;
    const dias = p === "semana" ? [0, 1, 2, 3, 4] : p === "sabado" ? [0, 1, 2, 3, 4, 5] : p === "todos" ? [0, 1, 2, 3, 4, 5, 6] : [];
    hor.filas.forEach((f, i) => {
      const on = dias.includes(i);
      f.sw.checked = on;
      if (on) { f.ap.value = ap; f.ci.value = ci; }
    });
    refrescarHorario();
  }

  function iniciarHorario() {
    const cont = $("rgHorDias");
    DIAS_LARGO.forEach((nombre, i) => {
      const fila = el("div", "rg-hor-dia off");
      const sw = el("label", "rg-sw");
      const chk = document.createElement("input"); chk.type = "checkbox"; chk.setAttribute("aria-label", "Atiende el " + nombre);
      sw.appendChild(chk); sw.appendChild(el("span"));
      fila.appendChild(sw);
      fila.appendChild(el("b", "", nombre));
      const cerrado = el("span", "rg-hor-cerrado", "Cerrado");
      const horas = el("span", "rg-hor-horas");
      horas.hidden = true;
      const ap = document.createElement("select"); ap.setAttribute("aria-label", "Hora de apertura " + nombre);
      const ci = document.createElement("select"); ci.setAttribute("aria-label", "Hora de cierre " + nombre);
      llenarHoras(ap, 300, 1350, 540);
      llenarHoras(ci, 360, 1410, 1080);
      horas.appendChild(ap); horas.appendChild(document.createTextNode("a")); horas.appendChild(ci);
      fila.appendChild(cerrado); fila.appendChild(horas);
      cont.appendChild(fila);
      hor.filas.push({ fila, sw: chk, ap, ci, horas, cerrado });

      chk.addEventListener("change", refrescarHorario);
      ap.addEventListener("change", () => {
        if (Number(ci.value) <= Number(ap.value)) ci.value = Math.min(Number(ap.value) + 60, 1410);
        refrescarHorario();
      });
      ci.addEventListener("change", () => {
        if (Number(ci.value) <= Number(ap.value)) ap.value = Math.max(Number(ci.value) - 60, 300);
        refrescarHorario();
      });
    });
    $("rgHorPresets").addEventListener("click", (e) => {
      const b = e.target.closest("button[data-p]");
      if (b) aplicarPreset(b.dataset.p);
    });
    refrescarHorario();
  }

  function actualizarFuerza() {
    const v = $("rgPassword").value;
    let n = 0;
    if (v.length >= 8) n++;
    if (/[a-z]/.test(v) && /[A-Z]/.test(v)) n++;
    if (/\d/.test(v)) n++;
    if (/[^A-Za-z0-9]/.test(v) || v.length >= 12) n++;
    $("rgFuerza").dataset.n = v ? String(Math.max(n, 1)) : "0";
  }

  async function finalizar() {
    if (estado.ocupado) return;
    estado.ocupado = true;
    ocultar("rgFinError");
    girar("rgFinalizar", true);
    actualizar();
    const r = await postJson("/registro/finalizar", {
      token: estado.token,
      nombreComercial: $("rgNombre").value.trim(),
      ruc: $("rgRuc").value,
      direccion: $("rgDireccion").value.trim(),
      password: $("rgPassword").value,
      telefono: soloDigitos($("rgTelefono").value),
      correoContacto: $("rgUsarCuenta").checked ? "" : $("rgCorreoContacto").value.trim(),
      descripcion: $("rgDescripcion").value.trim(),
      horario: $("rgHorario").value.trim(),
    });
    estado.ocupado = false;
    girar("rgFinalizar", false);

    if (!r.ok) {
      if (r.status === 401 && !r.data.campo) { sesionVencida(r.data.error); return; }
      const id = CAMPO_A_INPUT[r.data.campo];
      if (id && $(id)) {
        if (["rgNombre", "rgRuc", "rgDireccion", "rgPassword"].includes(id)) ir(4);
        marcar(id, r.data.error);
        if (id === "rgHorario") mostrarBloque("rgFinError", r.data.error);
      } else mostrarBloque("rgFinError", r.data.error);
      actualizar();
      return;
    }
    borrarToken();
    estado.token = null;
    $("rgExitoCorreo").textContent = r.data.correo;
    $("rgExitoTexto").textContent = r.data.comprobanteEnviado
      ? "Te enviamos el comprobante de pago a"
      : "Tu comprobante de pago llegará en unos minutos a";
    $("rgExitoNombre").textContent = r.data.nombreComercial;
    $("rgExitoCodigo").textContent = r.data.codigoAdmin;
    $("rgExitoPlan").textContent = r.data.plan;
    $("rgExitoVence").textContent = r.data.vence;
    $("rgExitoRenov").textContent = r.data.renovacionAutomatica ? "Automática" : "Manual";
    ir(6);
  }

  function abrirTerminos(ev) { if (ev) ev.preventDefault(); $("rgTerminos").hidden = false; }
  function cerrarTerminos() { $("rgTerminos").hidden = true; }

  async function iniciar() {
    otpCorreo = crearOtp($("rgOtp"), (enter) => {
      $("rgOtpErr").textContent = "";
      actualizar();
      if (otpCorreo.valor().length === 6) verificarCodigo();
    });
    otpYape = crearOtp($("rgOtpYape"), (enter) => {
      $("rgCodigoErr").textContent = "";
      ocultar("rgPagoError");
      actualizar();
      if (enter && !$("rgPagar").disabled) pagar();
    });

    Object.keys(REGLAS).forEach(configurarCampo);

    $("rgAcepta").addEventListener("change", () => { $("rgAceptaErr").textContent = ""; actualizar(); });
    $("rgVerTerminos").addEventListener("click", abrirTerminos);
    $("rgTerminosCerrar").addEventListener("click", cerrarTerminos);
    $("rgTerminosOk").addEventListener("click", cerrarTerminos);
    $("rgTerminos").addEventListener("click", (e) => { if (e.target === $("rgTerminos")) cerrarTerminos(); });

    $("rgEnviarCodigo").addEventListener("click", () => enviarCodigo(false));
    $("rgVerificar").addEventListener("click", verificarCodigo);
    $("rgReenviar").addEventListener("click", () => enviarCodigo(true));
    $("rgCambiarCorreo").addEventListener("click", cambiarCorreo);

    $("rgPlanSiguiente").addEventListener("click", () => { if (planActual()) ir(3); });

    document.querySelectorAll(".rg-metodo").forEach((b) => b.addEventListener("click", () => elegirMetodo(b.dataset.metodo)));
    $("rgYapeSolicitar").addEventListener("click", solicitarYape);
    $("rgPagoAtras").addEventListener("click", () => { if (!estado.ocupado) ir(2); });
    $("rgPagar").addEventListener("click", () => {
      if (estado.metodo === "PAYPAL") abrirPaypal(); else pagar();
    });

    $("rgPpCorreo").addEventListener("input", actualizar);
    $("rgPpClave").addEventListener("input", actualizar);
    $("rgPpClave").addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); paypalEntrar(); } });
    $("rgPpEntrar").addEventListener("click", paypalEntrar);
    $("rgPpPagar").addEventListener("click", paypalPagar);
    $("rgPpCancelar1").addEventListener("click", cerrarPaypal);
    $("rgPpCancelar2").addEventListener("click", cerrarPaypal);

    $("rgOjo").addEventListener("click", () => {
      const input = $("rgPassword");
      const oculta = input.type === "password";
      input.type = oculta ? "text" : "password";
      $("rgOjo").classList.toggle("fa-eye", !oculta);
      $("rgOjo").classList.toggle("fa-eye-slash", oculta);
    });
    $("rgUsarCuenta").addEventListener("change", () => {
      const usa = $("rgUsarCuenta").checked;
      $("rgCorreoContactoCaja").hidden = usa;
      if (usa) { $("rgCorreoContacto").value = ""; marcar("rgCorreoContacto", ""); }
      else $("rgCorreoContacto").focus();
      actualizar();
    });
    $("rgContinuarLib").addEventListener("click", () => { if (!$("rgContinuarLib").disabled) ir(5); });
    $("rgPerfilAtras").addEventListener("click", () => { if (!estado.ocupado) ir(4); });
    $("rgFinalizar").addEventListener("click", finalizar);
    iniciarHorario();

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      if (!$("rgTerminos").hidden) cerrarTerminos();
      else if (!$("rgPaypal").hidden) cerrarPaypal();
    });

    await cargarPlanes();

    // Si recarga la página con un registro en curso, se retoma donde iba
    const token = leerToken();
    if (token) {
      const r = await llamar("/registro/estado?token=" + encodeURIComponent(token), { cache: "no-store" });
      if (r.ok) {
        estado.token = token;
        estado.correo = r.data.correo;
        if (r.data.plan) estado.plan = r.data.plan;
        marcarPlan();
        ir(r.data.estado === "PAGADO" ? 4 : 2);
        return;
      }
      borrarToken();
    }
    ir(1);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
  else iniciar();
})();