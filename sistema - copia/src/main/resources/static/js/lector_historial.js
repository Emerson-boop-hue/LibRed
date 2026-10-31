// =====================================================================
// MÓDULO HISTORIAL DEL LECTOR
// Pestañas (Préstamos / Multas) y filtros en vivo sobre la tabla de préstamos.
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

  // ---------- filtros de préstamos ----------
  const inputTexto = document.getElementById("hsTexto");
  const selEstado = document.getElementById("hsEstado");
  const selCategoria = document.getElementById("hsCategoria");
  const btnLimpiar = document.getElementById("hsLimpiar");
  const filas = Array.from(document.querySelectorAll("#hsCuerpo tr"));
  const contador = document.getElementById("hsContador");
  const contadorTexto = document.getElementById("hsContadorTexto");
  const sinResultados = document.getElementById("hsSinResultados");

  if (!inputTexto || !selEstado || !selCategoria) return;

  function filtrar() {
    const texto = inputTexto.value.trim().toLowerCase();
    const estado = selEstado.value;
    const categoria = selCategoria.value;
    let visibles = 0;

    filas.forEach((fila) => {
      const coincide =
        (!texto || fila.dataset.texto.includes(texto)) &&
        (!estado || fila.dataset.estado === estado) &&
        (!categoria || fila.dataset.categoria === categoria);
      fila.hidden = !coincide;
      if (coincide) visibles++;
    });

    if (contador) contador.textContent = visibles;
    if (contadorTexto) contadorTexto.textContent = visibles === 1 ? "préstamo" : "préstamos";
    if (sinResultados) sinResultados.hidden = visibles !== 0;
  }

  inputTexto.addEventListener("input", filtrar);
  selEstado.addEventListener("change", filtrar);
  selCategoria.addEventListener("change", filtrar);
  btnLimpiar.addEventListener("click", () => {
    inputTexto.value = "";
    selEstado.value = "";
    selCategoria.value = "";
    filtrar();
    inputTexto.focus();
  });
})();