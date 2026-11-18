// MODAL "ESTADO DE TU MEMBRESÍA" (botón "Ver plan actual" del panel) Muestra el plan, cómo se renueva (automática con
// tarjeta/PayPal, manual con Yape), permite cancelar o reactivar la renovación automática y pagar (renovar o pasar al
// Plan Anual) con tarjeta, Yape o PayPal.
(function () {
  "use strict";

  let confirmandoCancelar = false;   // true = ya se pulsó "Cancelar renovación" una vez y falta confirmar
  let temporizador = null;
  let metodo = "TARJETA";            // método elegido en el panel de pago
  let planPago = "MENSUAL";          // plan que se está pagando
  let pagando = false;

  const $ = (id) => document.getElementById(id);

  function setTexto(id, texto) {
    const el = $(id);
    if (el) el.textContent = texto;
  }

  function mostrarMensaje(texto, tipo) {
    const el = $("susPlanMsg");
    if (!el) return;
    el.textContent = texto || "";
    el.className = "sus-plan-msg" + (tipo ? " " + tipo : "");
  }

  const CIRCUNFERENCIA = 326.73;   // 2 * PI * 52 (radio del anillo)
  let ultimo = null;               // últimos datos recibidos del servidor
  let animacionDias = null;

  const sinAnimacion = () => window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const soloDigitos = (v) => (v || "").replace(/\D/g, "");

  function reiniciarConfirmacion() {
    confirmandoCancelar = false;
    clearTimeout(temporizador);
    const btn = $("susBtnCancelar");
    if (btn) {
      btn.classList.remove("confirmar", "cargando");
      btn.disabled = false;
    }
    setTexto("susBtnCancelarTxt", "Cancelar renovación automática");
    setTexto("susRenovNota", "");
  }

  window.cerrarModalSuscripcion = function () {
    const modal = $("modalSuscripcion");
    if (modal) modal.classList.remove("active");
    const exito = $("susExito");
    if (exito) exito.classList.remove("visible");
    reiniciarConfirmacion();
    cerrarPagoInterno();
  };

  window.abrirModalPlan = function () {
    const modal = $("modalSuscripcion");
    if (!modal) return;
    modal.classList.add("active");
    cerrarPagoInterno();
    // Si ya se consultó antes, se muestra al instante y se actualiza en segundo plano
    if (ultimo) {
      reiniciarConfirmacion();
      mostrarMensaje("");
      mostrar(ultimo);
      animar();
      cargarEstado(true);
    } else {
      cargarEstado(false);
    }
  };

  async function cargarEstado(enSegundoPlano) {
    if (!enSegundoPlano) {
      reiniciarConfirmacion();
      mostrarMensaje("");
      $("susPlanCargando").hidden = false;
      $("susPlanCargando").innerHTML = '<span class="sus-spinner"></span> Consultando el estado de tu membresía...';
      $("susPlanContenido").hidden = true;
    }

    try {
      const res = await fetch("/suscripcion/estado", { credentials: "same-origin", cache: "no-store" });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const datos = await res.json();
      if (enSegundoPlano) {
        // Solo se repinta si algo cambió y el usuario no está en medio de una confirmación o de un pago
        if (!confirmandoCancelar && $("susPago").hidden && JSON.stringify(datos) !== JSON.stringify(ultimo)) { mostrar(datos); animar(); }
      } else {
        mostrar(datos);
        animar();
      }
    } catch (e) {
      console.error("No se pudo cargar el estado de la membresía:", e);
      if (!enSegundoPlano) {
        $("susPlanCargando").textContent = "No se pudo cargar la información de tu membresía. Intenta nuevamente.";
      }
    }
  }

  function mostrar(d) {
    ultimo = d;
    $("susPlanCargando").hidden = true;
    $("susPlanContenido").hidden = false;

    setTexto("susPlanDescripcion", d.vigente
      ? "La sede «" + d.nombreComercial + "» cuenta con una membresía activa."
      : "La membresía de «" + d.nombreComercial + "» ha vencido.");
    $("susPlanAvisoVencido").hidden = !!d.vigente;

    setTexto("susPlanNombre", d.nombrePlan);
    setTexto("susPlanPrecio", "S/ " + d.precio + " " + d.periodo);

    const badge = $("susPlanEstado");
    badge.textContent = d.vigente ? "Activo · Al día" : "Vencido";
    badge.className = "sus-estado " + (d.vigente ? "ok" : "vencido");

    const auto = d.renovacion === "AUTOMATICA";
    setTexto("susPlanInicio", d.inicioPeriodo || "—");
    setTexto("susPlanFinEtiq", auto ? "Se renueva el" : "Vence el");
    setTexto("susPlanFecha", d.fechaVencimiento);

    setTexto("susDatoSede", d.nombreComercial || "—");
    setTexto("susDatoCodigo", d.codigoAdmin || "—");
    setTexto("susDatoDesde", d.miembroDesde || "—");
    setTexto("susDatoTarjeta", d.metodoPago || "—");
    setTexto("susDatoMetodoNota", d.ultimoPago ? "el " + d.ultimoPago : "—");
    if (auto) {
      setTexto("susDatoCobroEtiq", "Próximo cobro");
      setTexto("susDatoCobro", "S/ " + d.proximoCobro);
      setTexto("susDatoCobroFecha", "el " + d.fechaVencimiento);
    } else {
      setTexto("susDatoCobroEtiq", "Vencimiento");
      setTexto("susDatoCobro", d.fechaVencimiento);
      setTexto("susDatoCobroFecha", d.renovacion === "CANCELADA" ? "Cancelada" : "Manual");
    }

    pintarRenovacion(d);

    // Mejora solo para el plan mensual; el anual ve un mensaje de "mejor plan"
    $("susPlanMigrar").hidden = !d.puedeMigrar;
    $("susPlanTop").hidden = !!d.puedeMigrar || !d.vigente;
    if (d.puedeMigrar) {
      setTexto("susPlanPrecioAnual", "S/ " + d.precioAnual);
      setTexto("susPlanAhorro", "S/ " + d.ahorroAnual);
      setTexto("susCompMensual", "S/ " + d.totalAnualMensual);
      setTexto("susCompPrecioMes", "S/ " + d.precio);
    } else {
      setTexto("susPlanTopAhorro", "S/ " + d.ahorroAnual);
    }
  }

  // Bloque "Renovación": automática, cancelada o manual
  function pintarRenovacion(d) {
    const cancelar = $("susBtnCancelar"), reactivar = $("susBtnReactivar"), renovar = $("susBtnRenovar");
    const alerta = $("susRenovAlerta");
    const periodo = d.tipoPlan === "ANUAL" ? "cada año" : "cada mes";
    cancelar.hidden = true; reactivar.hidden = true; renovar.hidden = true; alerta.hidden = true;
    renovar.className = "sus-btn-sec";
    let icono = "fa-rotate", clase = "";

    if (d.renovacion === "AUTOMATICA") {
      setTexto("susRenovTitulo", "Renovación automática activada");
      setTexto("susRenovTexto", "Cobraremos S/ " + d.proximoCobro + " " + periodo + " a " + (d.medioRenovacion || "tu método de pago") +
        ". Próximo cobro: " + d.fechaVencimiento + ".");
      cancelar.hidden = false;
      renovar.hidden = false;
      renovar.textContent = "Pagar con otro método";
      if (d.cobroFallido) {
        alerta.textContent = "No pudimos cobrar el último intento. Lo reintentaremos mañana; si prefieres, paga con otro método.";
        alerta.hidden = false;
        clase = "aviso";
      }
    } else if (d.renovacion === "CANCELADA") {
      icono = "fa-ban"; clase = "apagada";
      setTexto("susRenovTitulo", "Renovación automática cancelada");
      setTexto("susRenovTexto", d.vigente
        ? "Tu membresía seguirá activa hasta el " + d.fechaVencimiento + " y luego se desactivará."
        : "Tu membresía venció. Renuévala para volver a usar LibRed.");
      if (d.puedeReactivar) reactivar.hidden = false;
      renovar.hidden = false;
      renovar.textContent = "Renovar ahora";
      renovar.className = d.vigente ? "sus-btn-sec" : "sus-btn-prim";
    } else {
      icono = "fa-hand-holding-dollar"; clase = "manual";
      setTexto("susRenovTitulo", "Renovación manual");
      setTexto("susRenovTexto", d.vigente
        ? "Esta membresía no tiene cobro automático. Renuévala antes del " + d.fechaVencimiento + "; te avisaremos por correo unos días antes."
        : "Tu membresía venció. Renuévala para volver a usar LibRed.");
      renovar.hidden = false;
      renovar.textContent = "Renovar ahora";
      renovar.className = d.vigente ? "sus-btn-sec" : "sus-btn-prim";
    }
    $("susRenovIcono").innerHTML = '<i class="fa-solid ' + icono + '"></i>';
    $("susRenov").className = "sus-renov sus-anim " + clase;
  }

  // ANIMACIONES: entrada escalonada, anillo, barra y contador de días
  function animar() {
    if (!ultimo) return;
    const cont = $("susPlanContenido");
    cont.classList.remove("sus-entra");
    void cont.offsetWidth;                 // reinicia la animación CSS
    cont.classList.add("sus-entra");

    const dias = ultimo.diasRestantes === null || ultimo.diasRestantes === undefined ? null : Number(ultimo.diasRestantes);
    const total = Number(ultimo.diasPlan) || 30;
    const pct = dias === null ? 0 : Math.max(0, Math.min(1, dias / total));

    const anillo = $("susPlanAnillo");
    const barra = $("susPlanBarra");
    cancelAnimationFrame(animacionDias);

    if (sinAnimacion()) {
      anillo.style.transition = "none";
      anillo.style.strokeDashoffset = String(CIRCUNFERENCIA * (1 - pct));
      barra.style.width = Math.round(pct * 100) + "%";
      setTexto("susPlanDias", dias === null ? "—" : String(dias));
      return;
    }

    // Parte de cero y crece hasta el valor real
    anillo.style.transition = "none";
    anillo.style.strokeDashoffset = String(CIRCUNFERENCIA);
    barra.style.transition = "none";
    barra.style.width = "0%";
    void anillo.getBoundingClientRect();
    anillo.style.transition = "stroke-dashoffset 0.6s ease-out";
    anillo.style.strokeDashoffset = String(CIRCUNFERENCIA * (1 - pct));
    barra.style.transition = "";
    barra.style.width = Math.round(pct * 100) + "%";

    if (dias === null) { setTexto("susPlanDias", "—"); return; }
    const inicio = performance.now(), duracion = 550;
    const paso = (ahora) => {
      const t = Math.min(1, (ahora - inicio) / duracion);
      setTexto("susPlanDias", String(Math.round(dias * (1 - Math.pow(1 - t, 3)))));
      if (t < 1) animacionDias = requestAnimationFrame(paso);
    };
    setTexto("susPlanDias", "0");
    animacionDias = requestAnimationFrame(paso);
  }

  async function llamar(url, cuerpo) {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin",
      body: JSON.stringify(cuerpo || {}),
    });
    const data = await res.json().catch(() => ({}));
    return { ok: res.ok, data: data };
  }

  window.cancelarRenovacion = async function () {
    const btn = $("susBtnCancelar");
    if (!btn || btn.disabled) return;

    if (!confirmandoCancelar) {
      confirmandoCancelar = true;
      btn.classList.add("confirmar");
      setTexto("susBtnCancelarTxt", "Confirmar cancelación");
      setTexto("susRenovNota", "Tu membresía seguirá activa hasta el " + (ultimo ? ultimo.fechaVencimiento : "—") + ", pero ya no se renovará sola. Pulsa de nuevo para confirmar.");
      temporizador = setTimeout(reiniciarConfirmacion, 8000);
      return;
    }
    clearTimeout(temporizador);
    btn.disabled = true;
    btn.classList.add("cargando");
    mostrarMensaje("");
    try {
      const r = await llamar("/suscripcion/renovacion/cancelar");
      reiniciarConfirmacion();
      if (!r.ok) { mostrarMensaje(r.data.error || "No se pudo cancelar la renovación.", "error"); return; }
      mostrar(r.data);
    } catch (e) {
      reiniciarConfirmacion();
      mostrarMensaje("No se pudo conectar con el servidor. Intenta nuevamente.", "error");
    }
  };

  window.reactivarRenovacion = async function () {
    const btn = $("susBtnReactivar");
    if (!btn || btn.disabled) return;
    btn.disabled = true;
    mostrarMensaje("");
    try {
      const r = await llamar("/suscripcion/renovacion/reactivar");
      if (!r.ok) { mostrarMensaje(r.data.error || "No se pudo reactivar la renovación.", "error"); return; }
      mostrar(r.data);
    } catch (e) {
      mostrarMensaje("No se pudo conectar con el servidor. Intenta nuevamente.", "error");
    } finally {
      btn.disabled = false;
    }
  };

  // PAGO: RENOVAR O PASAR AL PLAN ANUAL (tarjeta, Yape o PayPal)
  const CAMPOS = ["susNum", "susTit", "susVen", "susCvv", "susCel", "susCod", "susPpCorreo", "susPpClave"];

  function limpiarPago() {
    CAMPOS.forEach((id) => { const el = $(id); if (el) { el.value = ""; el.classList.remove("con-error"); } });
    $("susYapeApp").hidden = true;
    $("susYapeIngreso").hidden = true;
    $("susPpClave").type = "password";
    const ico = $("susOjo") && $("susOjo").querySelector("i");
    if (ico) { ico.classList.add("fa-eye"); ico.classList.remove("fa-eye-slash"); }
    $("susAuto").checked = true;
    errorPago("");
  }

  function errorPago(texto, campo) {
    const el = $("susPagoError");
    el.textContent = texto || "";
    el.hidden = !texto;
    CAMPOS.forEach((id) => $(id) && $(id).classList.remove("con-error"));
    const mapa = { numero: "susNum", titular: "susTit", vence: "susVen", cvv: "susCvv", celular: "susCel", codigo: "susCod", paypal: "susPpCorreo" };
    if (campo && mapa[campo] && $(mapa[campo])) $(mapa[campo]).classList.add("con-error");
  }

  window.abrirPago = function (cual) {
    if (!ultimo) return;
    planPago = cual === "ANUAL" ? "ANUAL" : ultimo.tipoPlan;
    const anual = planPago === "ANUAL";
    const precio = anual ? ultimo.precioAnual : ultimo.precio;
    const mejora = anual && ultimo.tipoPlan === "MENSUAL";

    limpiarPago();
    setTexto("susPagoTitulo", mejora ? "Pasar al Plan Anual" : "Renovar " + (anual ? "Plan Anual" : "Plan Mensual"));
    setTexto("susPagoPlan", anual ? "Plan Anual · 365 días" : "Plan Mensual · 30 días");
    // El periodo nuevo se suma al final del actual (si todavía está vigente)
    setTexto("susPagoHasta", ultimo.vigente ? "Se suma a tu plan (hasta el " + ultimo.fechaVencimiento + ")" : "Desde hoy");
    setTexto("susPagoTotal", "S/ " + precio);
    setTexto("susPpUsd", "US$ " + (anual ? ultimo.usdAnual : ultimo.usdMensual));
    setTexto("susAutoTxt", "Renovar automáticamente " + (anual ? "cada año" : "cada mes") + ". Puedes cancelarlo cuando quieras.");
    setTexto("susBtnPagarTxt", "Pagar S/ " + precio);

    $("susPlanContenido").hidden = true;
    $("susPago").hidden = false;
    elegirMetodo("TARJETA");
  };

  function cerrarPagoInterno() {
    const p = $("susPago");
    if (p) p.hidden = true;
    if ($("susPlanContenido") && ultimo) $("susPlanContenido").hidden = false;
  }
  window.cerrarPago = function () { if (!pagando) cerrarPagoInterno(); };

  function elegirMetodo(m) {
    metodo = m;
    document.querySelectorAll(".sus-metodo").forEach((b) => b.classList.toggle("activo", b.dataset.m === m));
    document.querySelectorAll(".sus-mpanel").forEach((p) => (p.hidden = p.dataset.mp !== m));
    $("susAutoCaja").hidden = m === "YAPE";
    $("susAutoNotaYape").hidden = m !== "YAPE";
    errorPago("");
    actualizarBotonPago();
  }

  // El botón de pagar se habilita solo con los datos completos del método elegido
  function actualizarBotonPago() {
    let ok = false;
    if (metodo === "TARJETA") {
      ok = soloDigitos($("susNum").value).length === 16 && $("susTit").value.trim().length >= 3 &&
           /^\d{2}\/\d{2}$/.test($("susVen").value) && /^\d{3}$/.test($("susCvv").value);
    } else if (metodo === "YAPE") {
      ok = /^9\d{8}$/.test(soloDigitos($("susCel").value)) && /^\d{6}$/.test($("susCod").value);
      $("susYapeSolicitar").disabled = !/^9\d{8}$/.test(soloDigitos($("susCel").value));
    } else {
      ok = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test($("susPpCorreo").value.trim()) && $("susPpClave").value.length > 0;
    }
    $("susBtnPagar").disabled = !ok || pagando;
  }

  function armarPago() {
    const base = { plan: planPago, metodo: metodo };
    if (metodo === "TARJETA") {
      base.numero = soloDigitos($("susNum").value); base.titular = $("susTit").value;
      base.vence = $("susVen").value; base.cvv = $("susCvv").value;
      base.automatica = $("susAuto").checked;
    } else if (metodo === "YAPE") {
      base.celular = soloDigitos($("susCel").value); base.codigo = $("susCod").value;
    } else {
      base.correoPaypal = $("susPpCorreo").value.trim(); base.clavePaypal = $("susPpClave").value;
      base.automatica = $("susAuto").checked;
    }
    return base;
  }

  async function pagar() {
    if (pagando || $("susBtnPagar").disabled) return;
    pagando = true;
    errorPago("");
    const btn = $("susBtnPagar");
    btn.classList.add("cargando");
    btn.disabled = true;
    try {
      const r = await llamar("/suscripcion/pagar", armarPago());
      if (!r.ok) {
        errorPago(r.data.error || "No se pudo procesar el pago. Intenta nuevamente.", r.data.campo);
        return;
      }
      ultimo = r.data;
      const anual = planPago === "ANUAL";
      setTexto("susExitoTitulo", r.data.mejora ? "¡Plan Anual activado!" : "¡Pago recibido!");
      setTexto("susExitoTexto", "Tu membresía está vigente hasta el " + r.data.fechaVencimiento + ".");
      const exito = $("susExito");
      exito.classList.add("visible");
      setTimeout(function () {
        if (document.querySelector(".mb-chip, .mb-franja")) { window.location.reload(); return; }
        exito.classList.remove("visible");
        cerrarPagoInterno();
        mostrar(ultimo);
        animar();
      }, 1800);
    } catch (e) {
      console.error("Error al pagar la membresía:", e);
      errorPago("No se pudo conectar con el servidor. Intenta nuevamente.");
    } finally {
      pagando = false;
      btn.classList.remove("cargando");
      actualizarBotonPago();
    }
  }

  async function pedirCodigoYape() {
    const btn = $("susYapeSolicitar");
    if (btn.disabled) return;
    errorPago("");
    btn.disabled = true;
    btn.classList.add("cargando");
    try {
      const r = await llamar("/suscripcion/yape/codigo", { celular: soloDigitos($("susCel").value) });
      if (!r.ok) { errorPago(r.data.error || "No se pudo generar el código.", r.data.campo); return; }
      const c = r.data.codigo;
      setTexto("susYapeCodigo", c.slice(0, 3) + " " + c.slice(3));
      $("susYapeApp").hidden = false;
      $("susYapeIngreso").hidden = false;
      $("susCod").value = "";
      $("susCod").focus();
    } catch (e) {
      errorPago("No se pudo conectar con el servidor. Intenta nuevamente.");
    } finally {
      btn.classList.remove("cargando");
      actualizarBotonPago();
    }
  }

  // Conexión de los campos (una sola vez: este script se carga una vez en el layout)
  function iniciarPago() {
    if (!$("susPago")) return;
    document.querySelectorAll(".sus-metodo").forEach((b) => b.addEventListener("click", () => elegirMetodo(b.dataset.m)));
    $("susNum").addEventListener("input", () => { $("susNum").value = soloDigitos($("susNum").value).slice(0, 16).replace(/(.{4})(?=.)/g, "$1 "); actualizarBotonPago(); });
    $("susVen").addEventListener("input", () => { const d = soloDigitos($("susVen").value).slice(0, 4); $("susVen").value = d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d; actualizarBotonPago(); });
    $("susCvv").addEventListener("input", () => { $("susCvv").value = soloDigitos($("susCvv").value).slice(0, 3); actualizarBotonPago(); });
    $("susCel").addEventListener("input", () => { const d = soloDigitos($("susCel").value).slice(0, 9); $("susCel").value = d.replace(/(\d{3})(?=\d)/g, "$1 "); actualizarBotonPago(); });
    $("susCod").addEventListener("input", () => { $("susCod").value = soloDigitos($("susCod").value).slice(0, 6); actualizarBotonPago(); });
    ["susTit", "susPpCorreo", "susPpClave"].forEach((id) => $(id).addEventListener("input", actualizarBotonPago));
    $("susOjo").addEventListener("click", () => {
      const input = $("susPpClave");
      const mostrar = input.type === "password";
      input.type = mostrar ? "text" : "password";
      const ico = $("susOjo").querySelector("i");
      ico.classList.toggle("fa-eye", !mostrar);
      ico.classList.toggle("fa-eye-slash", mostrar);
    });
    $("susYapeSolicitar").addEventListener("click", pedirCodigoYape);
    $("susBtnPagar").addEventListener("click", pagar);
  }
  iniciarPago();

  function textoInvitacion() {
    const el = document.querySelector(".user-name");
    const nombre = el ? el.textContent.trim() : "";
    const quien = nombre ? "Soy de " + nombre + " y " : "";
    return "¡Hola! " + quien + "administro mi librería con LibRed, una red de librerías donde llevas el inventario, " +
           "los préstamos, las devoluciones y las multas desde la web. Conoce los planes y suma tu librería aquí: " +
           window.location.origin + "/login-admin";
  }

  window.abrirModalInvitar = function () {
    const texto = textoInvitacion();
    $("invitarTexto").value = texto;
    setTexto("invitarCopiarTxt", "Copiar mensaje");
    $("invitarCopiar").classList.remove("listo");
    $("modalInvitar").classList.add("active");
  };

  window.cerrarModalInvitar = function () {
    const m = $("modalInvitar");
    if (m) m.classList.remove("active");
  };

  window.copiarInvitacion = function () {
    const area = $("invitarTexto");
    const listo = () => {
      setTexto("invitarCopiarTxt", "¡Copiado!");
      $("invitarCopiar").classList.add("listo");
    };
    const alterno = () => {
      area.select();
      try { document.execCommand("copy"); listo(); } catch (e) { /* el usuario puede copiar a mano */ }
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(area.value).then(listo).catch(alterno);
    } else {
      alterno();
    }
  };

  // Cerrar al pulsar fuera del cuadro o con Escape
  document.addEventListener("click", function (e) {
    if (e.target && e.target.id === "modalInvitar") window.cerrarModalInvitar();
    if (e.target && e.target.id === "modalSuscripcion") window.cerrarModalSuscripcion();
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      window.cerrarModalInvitar();
      window.cerrarModalSuscripcion();
    }
  });
})();