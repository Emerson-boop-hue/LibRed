/* Vista General del lector: saltar a otra librería donde ya tiene cuenta. IIFE: el layout vuelve a cargar este script al navegar. */
(function () {
  const raiz = document.getElementById("pnRaiz");
  if (!raiz) return;

  const carga = document.getElementById("pnCarga");
  const cargaTexto = document.getElementById("pnCargaTexto");
  const toast = document.getElementById("pnToast");
  const pausa = (ms) => new Promise((r) => setTimeout(r, ms));
  const MIN_ETAPA1 = 1600, MIN_ETAPA2 = 1400; // mismos tiempos mínimos que en Red Global
  let entrando = false;
  let temporizador = null;

  function aviso(msg) {
    toast.textContent = msg;
    toast.hidden = false;
    clearTimeout(temporizador);
    temporizador = setTimeout(() => (toast.hidden = true), 3000);
  }

  function textoCarga(t, ok) {
    cargaTexto.classList.remove("pn-fade");
    void cargaTexto.offsetWidth; // reinicia la animación
    cargaTexto.textContent = (ok ? "✓ " : "") + t;
    cargaTexto.classList.toggle("pn-ok", !!ok);
    cargaTexto.classList.add("pn-fade");
  }

  async function entrar(boton) {
    if (entrando) return;
    entrando = true;
    const libId = boton.dataset.libId;
    const nombre = boton.dataset.libNombre || "la librería";
    carga.hidden = false;
    textoCarga("Verificando tu cuenta…"); // ya es miembro de esta librería

    const fallo = (msg) => { entrando = false; carga.hidden = true; aviso(msg); };
    try {
      const [resp] = await Promise.all([
        fetch("/lector/cambiar-sede", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
          body: JSON.stringify({ libreriaId: libId })
        }),
        pausa(MIN_ETAPA1)
      ]);
      const tipo = resp.headers.get("content-type") || "";
      if (!tipo.includes("application/json")) { window.location.href = "/login"; return; }
      const datos = await resp.json();
      if (!datos.ok) { fallo(datos.error || "No se pudo cambiar de librería."); return; }
      textoCarga("Cuenta verificada", true);
      await pausa(700);
      textoCarga("Cargando el panel de " + (datos.libreria || nombre) + "…");
      await pausa(MIN_ETAPA2 - 700);
      window.location.href = datos.destino || "/lector/panel";
    } catch (err) {
      fallo("No se pudo conectar con el servidor. Inténtalo otra vez.");
    }
  }

  document.querySelectorAll(".pn-sede-chip").forEach((b) => b.addEventListener("click", () => entrar(b)));
})();