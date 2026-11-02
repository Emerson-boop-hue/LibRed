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

  grid.addEventListener("click", (e) => {
    const ingresar = e.target.closest("[data-ingresar]");
    if (ingresar) { e.stopPropagation(); mostrarAviso("Pronto podrás entrar al catálogo de esta librería."); return; }
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
    if (e.target.closest("[data-ingresar]")) mostrarAviso("Pronto podrás entrar al catálogo de esta librería.");
  });

  // Esc cierra la ventana (se reemplaza el manejador anterior para no acumularlos al navegar)
  if (window.__rgEsc) document.removeEventListener("keydown", window.__rgEsc);
  window.__rgEsc = (e) => { if (e.key === "Escape" && !modal.hidden) cerrar(); };
  document.addEventListener("keydown", window.__rgEsc);

  aplicar();
})();