// =====================================================================
// MÓDULO CATÁLOGO DEL LECTOR
// Filtra en vivo: al escribir o cambiar un filtro se vuelve a pedir la página al servidor
// y solo se reemplaza la zona de resultados (sin recargar toda la pantalla).
// Si el JavaScript falla, el formulario sigue funcionando con el botón "Buscar".
// =====================================================================
(function () {
  const form = document.getElementById("formFiltros");
  if (!form) return;

  const inputTexto = document.getElementById("filtroTexto");
  const inputAutor = document.getElementById("filtroAutor");
  const selectCategoria = document.getElementById("filtroCategoria");
  const selectDisponibilidad = document.getElementById("filtroDisponibilidad");
  const btnLimpiar = document.getElementById("btnLimpiar");

  let temporizador = null;
  let peticionEnCurso = 0; // para ignorar respuestas viejas si el usuario escribe rápido

  function construirUrl() {
    const params = new URLSearchParams();
    const valores = {
      texto: inputTexto.value.trim(),
      categoria: selectCategoria.value,
      autor: inputAutor.value.trim(),
      disponibilidad: selectDisponibilidad.value,
    };
    Object.entries(valores).forEach(([clave, valor]) => {
      if (valor) params.set(clave, valor);
    });
    const consulta = params.toString();
    return "/lector/catalogo" + (consulta ? "?" + consulta : "");
  }

  async function filtrar() {
    const url = construirUrl();
    const miPeticion = ++peticionEnCurso;
    try {
      const resp = await fetch(url, { credentials: "same-origin", cache: "no-store" });

      // Si la sesión caducó, el servidor manda al login: se navega normalmente
      if (!resp.ok || !new URL(resp.url).pathname.startsWith("/lector/")) {
        window.location.href = url;
        return;
      }

      const html = await resp.text();
      if (miPeticion !== peticionEnCurso) return;

      const doc = new DOMParser().parseFromString(html, "text/html");
      const nuevo = doc.getElementById("catalogoResultados");
      const actual = document.getElementById("catalogoResultados");
      if (nuevo && actual) {
        actual.replaceWith(nuevo);
        window.history.replaceState({}, "", url);
      }
    } catch (err) {
      console.error("Error al filtrar el catálogo:", err);
    }
  }

  function filtrarConRetraso() {
    clearTimeout(temporizador);
    temporizador = setTimeout(filtrar, 300);
  }

  // El botón Buscar (o la tecla Enter) filtra al instante sin recargar la página
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    clearTimeout(temporizador);
    filtrar();
  });

  inputTexto.addEventListener("input", filtrarConRetraso);
  inputAutor.addEventListener("input", filtrarConRetraso);
  selectCategoria.addEventListener("change", filtrar);
  selectDisponibilidad.addEventListener("change", filtrar);

  btnLimpiar.addEventListener("click", () => {
    inputTexto.value = "";
    inputAutor.value = "";
    selectCategoria.value = "";
    selectDisponibilidad.value = "";
    filtrar();
  });
})();