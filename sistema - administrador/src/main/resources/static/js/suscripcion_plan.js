// =====================================================================
// MODAL "ESTADO DE TU MEMBRESÍA" (botón "Ver plan actual" del panel)
// Se carga UNA sola vez desde layout.html, por eso sigue funcionando
// aunque se navegue entre módulos con AJAX.
// =====================================================================
(function () {
  "use strict";

  let confirmando = false;   // true = ya se pulsó "Migrar" una vez y falta confirmar
  let temporizador = null;

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

  function reiniciarConfirmacion() {
    confirmando = false;
    clearTimeout(temporizador);
    const btn = $("susBtnMigrar");
    if (btn) {
      btn.textContent = "Migrar al Plan Anual";
      btn.classList.remove("confirmar");
      btn.disabled = false;
    }
    setTexto("susPlanMigrarNota", "");
  }

  // ---------------------------------------------------------------
  // ABRIR EL MODAL Y CARGAR EL ESTADO
  // ---------------------------------------------------------------
  window.abrirModalPlan = function () {
    const modal = $("modalSuscripcion");
    if (!modal) return;
    modal.classList.add("active");
    cargarEstado();
  };

  async function cargarEstado() {
    reiniciarConfirmacion();
    mostrarMensaje("");
    $("susPlanCargando").hidden = false;
    $("susPlanCargando").textContent = "Consultando el estado de tu membresía...";
    $("susPlanContenido").hidden = true;

    try {
      const res = await fetch("/suscripcion/estado", { credentials: "same-origin", cache: "no-store" });
      if (!res.ok) throw new Error("HTTP " + res.status);
      mostrar(await res.json());
    } catch (e) {
      console.error("No se pudo cargar el estado de la membresía:", e);
      $("susPlanCargando").textContent = "No se pudo cargar la información de tu membresía. Intenta nuevamente.";
    }
  }

  // ---------------------------------------------------------------
  // PINTAR LOS DATOS
  // ---------------------------------------------------------------
  function mostrar(d) {
    $("susPlanCargando").hidden = true;
    $("susPlanContenido").hidden = false;

    setTexto("susPlanDescripcion", d.vigente
      ? "La sede «" + d.nombreComercial + "» cuenta con una membresía activa."
      : "La membresía de «" + d.nombreComercial + "» ha vencido. Renuévala para que tus lectores sigan encontrando tu catálogo.");

    setTexto("susPlanTipo", d.nombrePlan + " · S/ " + d.precio + " " + d.periodo);

    const badge = $("susPlanEstado");
    badge.textContent = d.vigente ? "Activo · Al día" : "Vencido";
    badge.className = "sus-estado " + (d.vigente ? "ok" : "vencido");

    setTexto("susPlanFecha", d.fechaVencimiento);
    setTexto("susPlanDias", d.diasRestantes === null || d.diasRestantes === undefined ? "—" : String(d.diasRestantes));

    // La tarjeta de mejora solo se ofrece a quienes tienen el plan mensual
    const upgrade = $("susPlanMigrar");
    upgrade.hidden = !d.puedeMigrar;
    if (d.puedeMigrar) {
      setTexto("susPlanPrecioAnual", "S/ " + d.precioAnual);
      setTexto("susPlanAhorro", "S/ " + d.ahorroAnual);
    }
  }

  // ---------------------------------------------------------------
  // MIGRAR AL PLAN ANUAL (dos pasos: pulsar y luego confirmar)
  // ---------------------------------------------------------------
  window.migrarPlanAnual = async function () {
    const btn = $("susBtnMigrar");
    if (!btn || btn.disabled) return;

    if (!confirmando) {
      confirmando = true;
      btn.textContent = "Confirmar migración";
      btn.classList.add("confirmar");
      setTexto("susPlanMigrarNota", "Se aplicará el cobro del Plan Anual (S/ " + $("susPlanPrecioAnual").textContent.replace("S/ ", "") + "). Pulsa de nuevo para confirmar.");
      // Si no confirma en 8 segundos, el botón vuelve a su estado normal
      temporizador = setTimeout(reiniciarConfirmacion, 8000);
      return;
    }

    clearTimeout(temporizador);
    btn.disabled = true;
    btn.textContent = "Migrando...";
    mostrarMensaje("");

    try {
      const res = await fetch("/suscripcion/migrar-anual", { method: "POST", credentials: "same-origin" });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) {
        reiniciarConfirmacion();
        mostrarMensaje(data.error || "No se pudo migrar el plan. Intenta nuevamente.", "error");
        return;
      }
      confirmando = false;
      mostrar(data);
      mostrarMensaje("¡Listo! Tu sede ahora cuenta con el Plan Anual.", "ok");
    } catch (e) {
      console.error("Error al migrar de plan:", e);
      reiniciarConfirmacion();
      mostrarMensaje("No se pudo conectar con el servidor. Intenta nuevamente.", "error");
    }
  };
})();