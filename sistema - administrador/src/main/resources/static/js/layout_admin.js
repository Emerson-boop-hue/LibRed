// =====================================================================
// NAVEGACIÓN AJAX ENTRE MÓDULOS (sin parpadeo)
// =====================================================================
// Idea: NO se toca nada de lo visible hasta que el CSS nuevo ya está descargado.
//  1) se pide la página,  2) se precarga su CSS sin aplicarlo (media="print"),
//  3) en un solo paso síncrono: se inserta el HTML, se activa el CSS nuevo y se quita el viejo,
//  4) recién ahí se carga el JS del módulo.
// El CSS/JS de cada módulo se lee de la propia página recibida (#dynamic-specific-css / #dynamic-specific-js),
// así no hay que mantener un mapa a mano y funcionan todos los módulos (devoluciones, panel, etc.).

let navegacionEnCurso = 0; // token para ignorar respuestas viejas si el usuario hace clic rápido

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll('.sidebar-nav a[href^="/admin/"]').forEach((link) => {
    link.addEventListener("click", function (e) {
      // Respetar ctrl/cmd/shift/clic central (abrir en pestaña nueva)
      if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
      e.preventDefault();
      navegarA(this.getAttribute("href"), true);
    });
  });

  // Botón Atrás / Adelante del navegador
  window.addEventListener("popstate", () => {
    if (location.pathname.startsWith("/admin/")) {
      navegarA(location.pathname + location.search, false);
    } else {
      location.reload();
    }
  });
});

// Descarga un CSS SIN aplicarlo todavía. Devuelve el <link> listo para activar.
function precargarCss(href) {
  return new Promise((resolve) => {
    if (!href) return resolve(null);

    const actual = document.getElementById("dynamic-specific-css");
    // Si es el mismo CSS que ya está activo, no hace falta recargarlo
    if (actual && actual.getAttribute("href") === href) return resolve(actual);

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.media = "print"; // se descarga pero no se aplica
    link.href = href;
    link.onload = () => resolve(link);
    link.onerror = () => resolve(link); // aunque falle, no bloqueamos la navegación
    document.head.appendChild(link);
  });
}

function nombreInicializador(srcJs) {
  // "/js/devoluciones.js" -> "inicializarDevoluciones"
  const base = srcJs.split("/").pop().replace(".js", "");
  return "inicializar" + base.charAt(0).toUpperCase() + base.slice(1);
}

async function navegarA(url, empujarHistorial) {
  const miToken = ++navegacionEnCurso;

  try {
    const resp = await fetch(url, {
      headers: { "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin",
      cache: "no-store", // evita ver datos viejos (multas, préstamos, etc.)
    });

    // Si hubo redirección a otra zona (login) o error, navegación normal
    if (!resp.ok || !new URL(resp.url).pathname.startsWith("/admin/")) {
      window.location.href = url;
      return;
    }

    const html = await resp.text();
    if (miToken !== navegacionEnCurso) return; // llegó tarde, hay una navegación más nueva

    const doc = new DOMParser().parseFromString(html, "text/html");
    const nuevoContenedor = doc.querySelector(".dashboard-container");
    const contenedorActual = document.querySelector(".dashboard-container");

    if (!nuevoContenedor || !contenedorActual) {
      window.location.href = url;
      return;
    }

    // CSS y JS propios de la página que llegó (pueden no existir)
    const cssNuevoHref = doc.getElementById("dynamic-specific-css")?.getAttribute("href") || "";
    const jsNuevoSrc = doc.getElementById("dynamic-specific-js")?.getAttribute("src") || "";

    // 1) Descargar el CSS nuevo ANTES de tocar la pantalla
    const linkNuevo = await precargarCss(cssNuevoHref);
    if (miToken !== navegacionEnCurso) {
      if (linkNuevo && linkNuevo.media === "print") linkNuevo.remove();
      return;
    }

    // 2) Cambio atómico (todo en el mismo ciclo: el navegador no pinta nada a medias)
    const linkViejo = document.getElementById("dynamic-specific-css");
    const scriptViejo = document.getElementById("dynamic-specific-js");

    const titulo = doc.querySelector(".topbar-title-area h1");
    const desc = doc.querySelector(".topbar-title-area p");
    if (titulo) document.querySelector(".topbar-title-area h1").textContent = titulo.textContent;
    if (desc) document.querySelector(".topbar-title-area p").textContent = desc.textContent;
    const nuevoTitle = doc.querySelector("title");
    if (nuevoTitle) document.title = nuevoTitle.textContent;

    contenedorActual.innerHTML = nuevoContenedor.innerHTML;

    if (linkNuevo && linkNuevo !== linkViejo) {
      linkNuevo.media = "all"; // aplicar CSS nuevo
      linkNuevo.id = "dynamic-specific-css";
      if (linkViejo) linkViejo.remove(); // quitar CSS viejo en el mismo instante
    } else if (!linkNuevo && linkViejo) {
      linkViejo.remove(); // la página nueva no tiene CSS propio
    }

    // 3) JS del módulo (los <script> insertados con innerHTML no se ejecutan, por eso se recrea)
    if (scriptViejo) scriptViejo.remove();
    if (jsNuevoSrc) {
      const s = document.createElement("script");
      s.id = "dynamic-specific-js";
      s.src = jsNuevoSrc;
      s.onload = () => {
        const fn = window[nombreInicializador(jsNuevoSrc)];
        if (typeof fn === "function") fn();
      };
      document.body.appendChild(s);
    }

    // 4) URL y menú activo
    if (empujarHistorial) window.history.pushState({}, "", url);
    marcarMenuActivo(url);
    const area = document.getElementById("mainContent");
    if (area) area.scrollTop = 0;
  } catch (err) {
    console.error("Error en navegación AJAX:", err);
    window.location.href = url;
  }
}

function marcarMenuActivo(url) {
  const ruta = url.split("?")[0];
  document.querySelectorAll(".sidebar-nav .nav-item").forEach((item) => {
    item.classList.toggle("active", item.getAttribute("href") === ruta);
  });
}

// =====================================================================
// FUNCIONES NATIVAS GLOBALES
// =====================================================================

function toggleSidebar() {
  document.getElementById("sidebar").classList.toggle("collapsed");
  document.getElementById("mainContent").classList.toggle("expanded");
}

function setActive(element) {
  document.querySelectorAll(".sidebar-nav .nav-item").forEach((i) => i.classList.remove("active"));
  element.classList.add("active");
}

function abrirModalSuscripcion() {
  document.getElementById("modalSuscripcion").classList.add("active");
}

function cerrarModalSuscripcion() {
  document.getElementById("modalSuscripcion").classList.remove("active");
}

function abrirModalLogout(event) {
  event.preventDefault();
  document.getElementById("modalLogout").classList.add("active");
}

function cerrarModalLogout() {
  document.getElementById("modalLogout").classList.remove("active");
}