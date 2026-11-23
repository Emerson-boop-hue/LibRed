// Modo claro / oscuro compartido por todas las pantallas (públicas, lector y administrador).
(function () {
  if (window.LibRedTema) return; // el layout puede volver a ejecutar el script: se registra una sola vez
  var CLAVE = "libred-tema";
  var raiz = document.documentElement;

  function leerGuardado() {
    try {
      return localStorage.getItem(CLAVE) || localStorage.getItem("lectorTema");
    } catch (e) {
      return null;
    }
  }

  function esOscuro() {
    return raiz.getAttribute("data-tema") === "oscuro";
  }

  function pintarBotones() {
    var oscuro = esOscuro();
    document.querySelectorAll("[data-tema-toggle]").forEach(function (b) {
      b.title = oscuro ? "Modo claro" : "Modo oscuro";
      b.setAttribute("aria-pressed", oscuro ? "true" : "false");
    });
  }

  function aplicar(oscuro) {
    if (oscuro) raiz.setAttribute("data-tema", "oscuro");
    else raiz.removeAttribute("data-tema");
    pintarBotones();
  }

  function alternar() {
    var oscuro = !esOscuro();
    aplicar(oscuro);
    try {
      localStorage.setItem(CLAVE, oscuro ? "oscuro" : "claro");
    } catch (e) {
      // sin almacenamiento: el tema solo dura esta visita
    }
    document.dispatchEvent(new CustomEvent("libred:tema", { detail: { oscuro: oscuro } }));
  }

  if (leerGuardado() === "oscuro") raiz.setAttribute("data-tema", "oscuro");

  document.addEventListener("click", function (e) {
    if (e.target.closest && e.target.closest("[data-tema-toggle]")) alternar();
  });
  document.addEventListener("DOMContentLoaded", pintarBotones);

  // Si el tema cambia en otra pestaña, esta también se actualiza
  window.addEventListener("storage", function (e) {
    if (e.key === CLAVE) aplicar(e.newValue === "oscuro");
  });

  window.LibRedTema = { alternar: alternar, esOscuro: esOscuro };
})();
