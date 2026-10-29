// =====================================================================
// ASISTENTE DE REGISTRO Y SUSCRIPCIÓN (login administrativo)
// Se abre con el enlace "Conoce nuestros planes" -> abrirRegistroSuscripcion(event)
// =====================================================================
(function () {
  "use strict";

  const PASOS_TOTAL = 3;

  const estado = {
    paso: 1,
    plan: null,          // "MENSUAL" | "ANUAL"
    planes: [],          // lista que devuelve /suscripcion/planes
    planesCargados: false,
    tocados: {},         // campos que el usuario ya visitó (para mostrar su error)
    enviando: false,
  };

  const $ = (id) => document.getElementById(id);
  const soloDigitos = (v) => (v || "").replace(/\D/g, "");

  // ---------------------------------------------------------------
  // REGLAS DE VALIDACIÓN (paso 2 = pago, paso 3 = datos de la librería)
  // ---------------------------------------------------------------
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

  const REGLAS = {
    susTarjeta:   { paso: 2, ok: (v) => soloDigitos(v).length === 16, msg: "Ingresa los 16 dígitos de la tarjeta." },
    susTitular:   { paso: 2, ok: (v) => /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ' ]{3,60}$/.test(v.trim()), msg: "Ingresa el nombre del titular (solo letras)." },
    susVence:     { paso: 2, ok: vencimientoValido, msg: "Fecha inválida o tarjeta vencida (MM/AA)." },
    susCvv:       { paso: 2, ok: (v) => /^\d{3}$/.test(v), msg: "El CVV tiene 3 dígitos." },
    susNombre:    { paso: 3, ok: (v) => v.trim().length >= 2 && v.trim().length <= 150, msg: "Ingresa el nombre comercial." },
    susRuc:       { paso: 3, ok: (v) => /^\d{11}$/.test(v), msg: "El RUC debe tener 11 dígitos." },
    susDireccion: { paso: 3, ok: (v) => v.trim().length >= 5 && v.trim().length <= 255, msg: "Ingresa la dirección del establecimiento." },
    susPassword:  { paso: 3, ok: (v) => v.length >= 4 && v.length <= 6, msg: "La contraseña debe tener entre 4 y 6 caracteres." },
  };

  // Formato automático mientras se escribe
  const FORMATO = {
    susTarjeta: (v) => soloDigitos(v).slice(0, 16).replace(/(.{4})(?=.)/g, "$1 "),
    susVence: (v) => {
      const d = soloDigitos(v).slice(0, 4);
      return d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
    },
    susCvv: (v) => soloDigitos(v).slice(0, 3),
    susRuc: (v) => soloDigitos(v).slice(0, 11),
  };

  const campoValido = (id) => REGLAS[id].ok($(id).value);

  function pasoValido(n) {
    if (n === 1) return estado.plan !== null;
    return Object.keys(REGLAS)
      .filter((id) => REGLAS[id].paso === n)
      .every(campoValido);
  }

  function mostrarErrorCampo(id) {
    const invalido = !!estado.tocados[id] && !campoValido(id);
    const err = $(id + "Err");
    if (err) err.textContent = invalido ? REGLAS[id].msg : "";
    $(id).parentElement.classList.toggle("invalid", invalido);
  }

  function ponerErrorServidor(id, mensaje) {
    estado.tocados[id] = true;
    const err = $(id + "Err");
    if (err) err.textContent = mensaje;
    $(id).parentElement.classList.add("invalid");
  }

  // ---------------------------------------------------------------
  // PASO 1: PLANES (vienen del servidor, así los precios están en un solo lugar)
  // ---------------------------------------------------------------
  function crearElemento(tag, clase, texto) {
    const el = document.createElement(tag);
    if (clase) el.className = clase;
    if (texto !== undefined) el.textContent = texto;
    return el;
  }

  function crearTarjetaPlan(p) {
    const btn = crearElemento("button", "sus-plan");
    btn.type = "button";
    btn.dataset.plan = p.codigo;

    if (p.ahorro) btn.appendChild(crearElemento("span", "sus-plan-badge", "Ahorras S/ " + p.ahorro));

    const check = crearElemento("span", "sus-plan-check");
    check.innerHTML = '<i class="fa-solid fa-check"></i>';
    btn.appendChild(check);

    btn.appendChild(crearElemento("h3", "", p.nombre));

    const precio = crearElemento("div", "sus-plan-precio", "S/ " + p.precio);
    precio.appendChild(crearElemento("small", "", p.periodo));
    btn.appendChild(precio);

    const detalle = p.equivalenteMensual
      ? "Equivale a S/ " + p.equivalenteMensual + " al mes. Renovación automática cada " + p.dias + " días."
      : "Ideal para comenzar. Renovación automática cada " + p.dias + " días.";
    btn.appendChild(crearElemento("p", "sus-plan-detalle", detalle));

    btn.addEventListener("click", () => seleccionarPlan(p.codigo));
    return btn;
  }

  async function cargarPlanes() {
    const cont = $("susPlanes");
    cont.textContent = "Cargando planes...";
    try {
      const res = await fetch("/suscripcion/planes", { cache: "no-store" });
      if (!res.ok) throw new Error("HTTP " + res.status);
      estado.planes = await res.json();
      cont.textContent = "";
      estado.planes.forEach((p) => cont.appendChild(crearTarjetaPlan(p)));
      estado.planesCargados = true;
      marcarPlanSeleccionado();
    } catch (e) {
      console.error("No se pudieron cargar los planes:", e);
      cont.textContent = "No se pudieron cargar los planes. Cierra y vuelve a intentarlo.";
    }
  }

  function marcarPlanSeleccionado() {
    document.querySelectorAll(".sus-plan").forEach((b) => {
      b.classList.toggle("selected", b.dataset.plan === estado.plan);
    });
  }

  function seleccionarPlan(codigo) {
    estado.plan = codigo;
    marcarPlanSeleccionado();
    actualizarBotones();
  }

  function renderResumenPlan() {
    const cont = $("susResumenPlan");
    cont.textContent = "";
    const p = estado.planes.find((x) => x.codigo === estado.plan);
    if (!p) return;
    cont.appendChild(crearElemento("span", "", "Plan seleccionado: " + p.nombre));
    cont.appendChild(crearElemento("strong", "", "S/ " + p.precio + " " + p.periodo));
  }

  // ---------------------------------------------------------------
  // PASO 3: código de administrador autogenerado
  // ---------------------------------------------------------------
  async function cargarCodigo() {
    const campo = $("susCodigo");
    campo.value = "";
    try {
      const res = await fetch("/suscripcion/siguiente-codigo", { cache: "no-store" });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();
      campo.value = data.codigo;
    } catch (e) {
      console.error("No se pudo obtener el código:", e);
      campo.value = "Se asignará al registrar";
    }
  }

  // ---------------------------------------------------------------
  // NAVEGACIÓN ENTRE PASOS
  // ---------------------------------------------------------------
  function irPaso(n) {
    estado.paso = n;

    document.querySelectorAll(".sus-panel").forEach((p) => {
      p.classList.toggle("active", Number(p.dataset.panel) === n);
    });

    document.querySelectorAll(".sus-step").forEach((s) => {
      const k = Number(s.dataset.step);
      s.classList.toggle("active", k === n);
      s.classList.toggle("done", k < n);
      s.querySelector(".sus-step-dot").innerHTML = k < n ? '<i class="fa-solid fa-check"></i>' : String(k);
    });

    document.querySelectorAll(".sus-step-line").forEach((l, i) => {
      l.classList.toggle("done", i + 1 < n);
    });

    if (n === 2) renderResumenPlan();
    if (n === 3) cargarCodigo();

    $("susErrorGeneral").textContent = "";
    actualizarBotones();

    // Foco en el primer campo del paso (en el paso 1 no hay campos)
    if (n > 1) {
      const primero = Object.keys(REGLAS).find((id) => REGLAS[id].paso === n);
      if (primero) setTimeout(() => $(primero).focus(), 50);
    }
  }

  function actualizarBotones() {
    const ant = $("susAnterior");
    const sig = $("susSiguiente");
    ant.style.visibility = estado.paso === 1 ? "hidden" : "visible";
    ant.disabled = estado.enviando;
    if (estado.enviando) {
      sig.textContent = "Registrando...";
    } else {
      sig.textContent = estado.paso === PASOS_TOTAL ? "Finalizar Registro" : "Siguiente";
    }
    // Opaco y bloqueado hasta que el paso esté completo
    sig.disabled = estado.enviando || !pasoValido(estado.paso);
  }

  function siguiente() {
    if (estado.enviando || !pasoValido(estado.paso)) return;
    if (estado.paso < PASOS_TOTAL) {
      irPaso(estado.paso + 1);
    } else {
      enviarRegistro();
    }
  }

  function anterior() {
    if (estado.enviando || estado.paso === 1) return;
    irPaso(estado.paso - 1);
  }

  // ---------------------------------------------------------------
  // ENVÍO DEL REGISTRO
  // ---------------------------------------------------------------
  const CAMPO_A_INPUT = {
    nombreComercial: "susNombre",
    ruc: "susRuc",
    direccion: "susDireccion",
    password: "susPassword",
  };

  async function enviarRegistro() {
    estado.enviando = true;
    actualizarBotones();
    $("susErrorGeneral").textContent = "";

    // La tarjeta (paso 2) NO se envía: el pago es simulado
    const payload = {
      tipoPlan: estado.plan,
      nombreComercial: $("susNombre").value.trim(),
      ruc: $("susRuc").value,
      direccion: $("susDireccion").value.trim(),
      password: $("susPassword").value,
    };

    try {
      const res = await fetch("/suscripcion/registrar", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        manejarErrorServidor(data);
        return;
      }
      mostrarExito(data);
    } catch (e) {
      console.error("Error al registrar:", e);
      $("susErrorGeneral").textContent = "No se pudo conectar con el servidor. Intenta nuevamente.";
    } finally {
      estado.enviando = false;
      actualizarBotones();
    }
  }

  function manejarErrorServidor(data) {
    const mensaje = data.error || "No se pudo completar el registro. Intenta nuevamente.";
    const inputId = CAMPO_A_INPUT[data.campo];
    if (inputId) {
      ponerErrorServidor(inputId, mensaje);
    } else {
      if (data.campo === "tipoPlan") irPaso(1);
      $("susErrorGeneral").textContent = mensaje;
    }
  }

  function mostrarExito(data) {
    $("susModal").style.display = "none";
    $("susFinalCodigo").textContent = data.codigoAdmin || "";
    $("susFinal").hidden = false;

    // Se muestra el mensaje un momento, se desvanece y se redirige al login
    setTimeout(() => {
      $("susOverlay").classList.add("sus-fade");
      setTimeout(() => { window.location.href = "/login-admin"; }, 600);
    }, 2800);
  }

  // ---------------------------------------------------------------
  // ABRIR / CERRAR / REINICIAR
  // ---------------------------------------------------------------
  function reiniciar() {
    estado.plan = null;
    estado.tocados = {};
    estado.enviando = false;

    Object.keys(REGLAS).forEach((id) => {
      $(id).value = "";
      $(id + "Err").textContent = "";
      $(id).parentElement.classList.remove("invalid");
    });
    $("susCodigo").value = "";
    $("susPassword").type = "password";
    $("susOjo").classList.remove("fa-eye-slash");
    $("susOjo").classList.add("fa-eye");

    $("susModal").style.display = "";
    $("susFinal").hidden = true;
    $("susOverlay").classList.remove("sus-fade");

    marcarPlanSeleccionado();
    irPaso(1);
  }

  window.abrirRegistroSuscripcion = function (event) {
    if (event) event.preventDefault();
    reiniciar();
    const overlay = $("susOverlay");
    overlay.classList.add("active");
    overlay.setAttribute("aria-hidden", "false");
    if (!estado.planesCargados) cargarPlanes();
  };

  function cerrar() {
    if (estado.enviando) return;
    const overlay = $("susOverlay");
    overlay.classList.remove("active");
    overlay.setAttribute("aria-hidden", "true");
  }

  // ---------------------------------------------------------------
  // INICIALIZACIÓN
  // ---------------------------------------------------------------
  function iniciar() {
    if (!$("susOverlay")) return;

    Object.keys(REGLAS).forEach((id) => {
      const input = $(id);

      input.addEventListener("input", () => {
        if (FORMATO[id]) input.value = FORMATO[id](input.value);
        if (estado.tocados[id]) mostrarErrorCampo(id);
        $("susErrorGeneral").textContent = "";
        actualizarBotones();
      });

      input.addEventListener("blur", () => {
        estado.tocados[id] = true;
        mostrarErrorCampo(id);
      });

      // Enter avanza si el paso ya está completo
      input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          siguiente();
        }
      });
    });

    $("susOjo").addEventListener("click", () => {
      const input = $("susPassword");
      const ojo = $("susOjo");
      const oculta = input.type === "password";
      input.type = oculta ? "text" : "password";
      ojo.classList.toggle("fa-eye", !oculta);
      ojo.classList.toggle("fa-eye-slash", oculta);
    });

    $("susSiguiente").addEventListener("click", siguiente);
    $("susAnterior").addEventListener("click", anterior);
    $("susCerrar").addEventListener("click", cerrar);

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && $("susOverlay").classList.contains("active") && $("susFinal").hidden) cerrar();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", iniciar);
  } else {
    iniciar();
  }
})();