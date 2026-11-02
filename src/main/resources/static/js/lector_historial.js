// =====================================================================
// MÓDULO HISTORIAL DEL LECTOR
// Pestañas (Préstamos / Multas) y filtros en vivo en cada tabla.
// Es una función autocontenida: el layout la ejecuta cada vez que se entra al módulo (navegación AJAX).
// =====================================================================
(function () {
  const tabs = document.querySelectorAll(".hs-tab");
  if (!tabs.length) return;

  // ---------- pestañas ----------
  const paneles = {
    prestamos: document.getElementById("panelPrestamos"),
    multas: document.getElementById("panelMultas"),
  };

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      tabs.forEach((t) => t.classList.toggle("activa", t === tab));
      Object.keys(paneles).forEach((k) => {
        if (paneles[k]) paneles[k].hidden = k !== tab.dataset.tab;
      });
    });
  });

  // ---------- filtro genérico de tabla ----------
  // cfg: { cuerpo, texto, selects: [{ el, atributo }], limpiar, contador, contadorTexto, sinResultados, singular, plural }
  function conectarFiltros(cfg) {
    const cuerpo = document.getElementById(cfg.cuerpo);
    const inputTexto = document.getElementById(cfg.texto);
    const btnLimpiar = document.getElementById(cfg.limpiar);
    if (!cuerpo || !inputTexto) return;

    const filas = Array.from(cuerpo.querySelectorAll("tr"));
    const selects = cfg.selects.map((s) => ({ el: document.getElementById(s.id), atributo: s.atributo }));
    const contador = document.getElementById(cfg.contador);
    const contadorTexto = document.getElementById(cfg.contadorTexto);
    const sinResultados = document.getElementById(cfg.sinResultados);

    function filtrar() {
      const texto = inputTexto.value.trim().toLowerCase();
      let visibles = 0;

      filas.forEach((fila) => {
        let coincide = !texto || fila.dataset.texto.includes(texto);
        selects.forEach((s) => {
          if (s.el && s.el.value && fila.dataset[s.atributo] !== s.el.value) coincide = false;
        });
        fila.hidden = !coincide;
        if (coincide) visibles++;
      });

      if (contador) contador.textContent = visibles;
      if (contadorTexto) contadorTexto.textContent = visibles === 1 ? cfg.singular : cfg.plural;
      if (sinResultados) sinResultados.hidden = visibles !== 0;
    }

    inputTexto.addEventListener("input", filtrar);
    selects.forEach((s) => s.el && s.el.addEventListener("change", filtrar));
    if (btnLimpiar) {
      btnLimpiar.addEventListener("click", () => {
        inputTexto.value = "";
        selects.forEach((s) => s.el && (s.el.value = ""));
        filtrar();
        inputTexto.focus();
      });
    }
  }

  conectarFiltros({
    cuerpo: "hsCuerpo",
    texto: "hsTexto",
    selects: [
      { id: "hsEstado", atributo: "estado" },
      { id: "hsCategoria", atributo: "categoria" },
    ],
    limpiar: "hsLimpiar",
    contador: "hsContador",
    contadorTexto: "hsContadorTexto",
    sinResultados: "hsSinResultados",
    singular: "préstamo",
    plural: "préstamos",
  });

  conectarFiltros({
    cuerpo: "hmCuerpo",
    texto: "hmTexto",
    selects: [{ id: "hmEstado", atributo: "estado" }],
    limpiar: "hmLimpiar",
    contador: "hmContador",
    contadorTexto: "hmContadorTexto",
    sinResultados: "hmSinResultados",
    singular: "multa",
    plural: "multas",
  });
})();