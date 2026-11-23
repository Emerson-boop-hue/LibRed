/* Módulo AYUDA Y SOPORTE del lector. IIFE: el layout vuelve a cargar este script al navegar entre módulos. */
(function () {
  const lista = document.getElementById("ayLista");
  if (!lista) return;

  const buscador = document.getElementById("ayBuscar");
  const chips = Array.from(document.querySelectorAll("#ayChips .ay-chip"));
  const items = Array.from(lista.querySelectorAll(".ay-item"));
  const sinResultados = document.getElementById("aySinResultados");
  let categoria = "todas";

  // Sin tildes y en minúsculas, para que "contrasena" encuentre "contraseña"
  const norm = (t) => (t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");

  function filtrar() {
    const texto = norm(buscador.value.trim());
    let visibles = 0;
    items.forEach((it) => {
      const coincideCat = categoria === "todas" || it.dataset.cat === categoria;
      const coincideTexto = !texto || norm(it.textContent).includes(texto);
      const mostrar = coincideCat && coincideTexto;
      it.hidden = !mostrar;
      if (mostrar) visibles++;
    });
    sinResultados.hidden = visibles !== 0;
  }

  buscador.addEventListener("input", filtrar);
  chips.forEach((c) => c.addEventListener("click", () => {
    categoria = c.dataset.cat;
    chips.forEach((x) => x.classList.toggle("activo", x === c));
    filtrar();
  }));

  // Abrir una pregunta cierra las demás (acordeón)
  items.forEach((it) => it.addEventListener("toggle", () => {
    if (it.open) items.forEach((o) => { if (o !== it) o.open = false; });
  }));
})();