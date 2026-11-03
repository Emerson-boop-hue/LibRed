/* Módulo RED GLOBAL del lector. IIFE: el layout vuelve a cargar este script al navegar entre módulos. */
(function () {
  const grid = document.getElementById("rgGrid");
  if (!grid) return;

  const $ = (id) => document.getElementById(id);
  const inTexto = $("rgTexto"), selCategoria = $("rgCategoria"), selRelacion = $("rgRelacion"),
        selOrden = $("rgOrden"), btnLimpiar = $("rgLimpiar"), contador = $("rgContador"),
        contadorTexto = $("rgContadorTexto"), sinResultados = $("rgSinResultados"),
        zonaScroll = $("rgScroll"), modal = $("rgModal"), modalContenido = $("rgModalContenido"),
        aviso = $("rgAviso");
  const tarjetas = Array.from(grid.querySelectorAll(".rg-card"));

  const norm = (t) => (t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim();
  const num = (c, k) => parseFloat(c.dataset[k]) || 0;
  const porNombre = (a, b) => a.dataset.nombre.localeCompare(b.dataset.nombre, "es");

  const comparadores = {
    nombre: porNombre,
    puntaje: (a, b) => num(b, "puntaje") - num(a, "puntaje") || porNombre(a, b),
    articulos: (a, b) => num(b, "articulos") - num(a, "articulos") || porNombre(a, b),
    tarifa: (a, b) => num(a, "tarifa") - num(b, "tarifa") || porNombre(a, b),
    dias: (a, b) => num(b, "dias") - num(a, "dias") || porNombre(a, b),
  };
  // La librería donde estoy siempre va primera, sea cual sea el orden elegido
  const conSedePrimero = (cmp) => (a, b) => (b.dataset.sede - a.dataset.sede) || cmp(a, b);

  function aplicar() {
    const texto = norm(inTexto.value), categoria = selCategoria.value, relacion = selRelacion.value;

    let visibles = 0;
    tarjetas.forEach((c) => {
      const ok =
        (!texto || norm(c.dataset.texto).includes(texto)) &&
        (!categoria || c.dataset.cats.includes("|" + categoria + "|")) &&
        (!relacion || c.dataset.miembro === relacion);
      c.hidden = !ok;
      if (ok) visibles++;
    });

    tarjetas.slice().sort(conSedePrimero(comparadores[selOrden.value] || porNombre))
      .forEach((c) => grid.insertBefore(c, sinResultados));

    contador.textContent = visibles;
    contadorTexto.textContent = visibles === 1 ? "librería" : "librerías";
    sinResultados.hidden = visibles !== 0;
    zonaScroll.scrollTop = 0;
  }

  inTexto.addEventListener("input", aplicar);
  [selCategoria, selRelacion, selOrden].forEach((e) => e.addEventListener("change", aplicar));
  btnLimpiar.addEventListener("click", () => {
    inTexto.value = ""; selCategoria.value = ""; selRelacion.value = ""; selOrden.value = "nombre";
    aplicar();
    inTexto.focus();
  });

  // ---------- Aviso breve (el botón Ingresar todavía no lleva al catálogo de otra librería) ----------
  let temporizador;
  function mostrarAviso(msg) {
    aviso.textContent = msg;
    aviso.hidden = false;
    clearTimeout(temporizador);
    temporizador = setTimeout(() => (aviso.hidden = true), 2600);
  }

  // ---------- Ventana de detalles ----------
  function abrir(tarjeta) {
    const detalle = tarjeta.querySelector(".rg-detalle");
    if (!detalle) return;
    modalContenido.innerHTML = detalle.innerHTML;
    modal.hidden = false;
    modal.querySelector(".rg-modal-caja").scrollTop = 0;
    $("rgModalCerrar").focus();
  }
  function cerrar() { modal.hidden = true; modalContenido.innerHTML = ""; }


  // ---- Cambio de librería (botón "Ingresar") ----
  const carga = $("rgCarga"), cargaTexto = $("rgCargaTexto");
  let entrando = false;

  const pausa = (ms) => new Promise((r) => setTimeout(r, ms));
  const MIN_ETAPA1 = 1600, MIN_ETAPA2 = 1400; // tiempos mínimos para que se alcance a leer cada etapa

  // Cambia el texto con un pequeño fundido; "ok" agrega el ✓ de etapa completada
  function textoCarga(t, ok) {
    cargaTexto.classList.remove("rg-fade");
    void cargaTexto.offsetWidth; // reinicia la animación
    cargaTexto.textContent = (ok ? "✓ " : "") + t;
    cargaTexto.classList.toggle("rg-ok", !!ok);
    cargaTexto.classList.add("rg-fade");
  }

  async function entrar(boton) {
    if (entrando) return;
    const libId = boton.dataset.libId;
    const nombre = boton.dataset.libNombre || "la librería";
    const primeraVez = boton.dataset.libMiembro === "0";
    entrando = true;
    modal.hidden = true;
    carga.hidden = false;
    const etapa1 = primeraVez ? "Creando tu cuenta en " + nombre + "…" : "Verificando tu cuenta…";
    textoCarga(etapa1);

    const fallo = (msg) => {
      entrando = false;
      carga.hidden = true;
      mostrarAviso(msg);
    };
    try {
      // El mínimo corre en paralelo: si el servidor tarda más, se espera al servidor (no se suman tiempos)
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
      textoCarga(primeraVez ? "Cuenta creada" : "Cuenta verificada", true);
      await pausa(700);
      textoCarga("Cargando el panel de " + (datos.libreria || nombre) + "…");
      await pausa(MIN_ETAPA2 - 700);
      window.location.href = datos.destino || "/lector/panel";
    } catch (err) {
      fallo("No se pudo conectar con el servidor. Inténtalo otra vez.");
    }
  }

  grid.addEventListener("click", (e) => {
    const ingresar = e.target.closest("[data-ingresar]");
    if (ingresar) { e.stopPropagation(); entrar(ingresar); return; }
    if (e.target.closest("a")) return; // "Ir al catálogo" navega normalmente
    const t = e.target.closest(".rg-card");
    if (t) abrir(t);
  });
  grid.addEventListener("keydown", (e) => {
    if ((e.key === "Enter" || e.key === " ") && e.target.classList.contains("rg-card")) {
      e.preventDefault();
      abrir(e.target);
    }
  });

  modal.addEventListener("click", (e) => {
    if (e.target === modal || e.target.closest("#rgModalCerrar")) { cerrar(); return; }
    const ing = e.target.closest("[data-ingresar]");
    if (ing) entrar(ing);
  });

  // Esc cierra la ventana (se reemplaza el manejador anterior para no acumularlos al navegar)
  if (window.__rgEsc) document.removeEventListener("keydown", window.__rgEsc);
  window.__rgEsc = (e) => { if (e.key === "Escape" && !modal.hidden) cerrar(); };
  document.addEventListener("keydown", window.__rgEsc);

  aplicar();
})();