// =====================================================================
// PANEL DEL LECTOR: NAVEGACIÓN ENTRE MÓDULOS SIN PARPADEO
// =====================================================================
// Antes, cada clic en el menú recargaba TODA la página (y con ella el logo, que pesa ~1 MB),
// por eso el logo parpadeaba. Ahora solo se cambia la zona de contenido y la barra lateral
// (logo incluido) no se toca nunca.
//  1) se pide la página nueva,  2) se precarga su CSS sin aplicarlo,
//  3) en un solo paso se cambia el HTML y el CSS,  4) se carga el JS del módulo.
// El CSS/JS de cada módulo se lee de la propia página recibida (#dynamic-specific-css / #dynamic-specific-js),
// así que los módulos nuevos funcionan sin tocar este archivo.

let navegacionEnCurso = 0; // para ignorar respuestas viejas si el usuario hace clic rápido

// Enlaces internos del panel (menú lateral y enlaces dentro del contenido). El cierre de sesión no entra aquí.
function esEnlaceDelPanel(enlace) {
  const href = enlace.getAttribute("href") || "";
  return (
    href.startsWith("/lector/") &&
    !href.startsWith("/lector/logout") &&
    !enlace.target &&
    !enlace.hasAttribute("download")
  );
}

document.addEventListener("click", (e) => {
  // Respetar ctrl/cmd/shift/clic central (abrir en pestaña nueva)
  if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
  const enlace = e.target.closest("a[href]");
  if (!enlace || !esEnlaceDelPanel(enlace)) return;
  e.preventDefault();
  navegarA(enlace.getAttribute("href"), true);
});

// Botón Atrás / Adelante del navegador
window.addEventListener("popstate", () => {
  if (location.pathname.startsWith("/lector/")) {
    navegarA(location.pathname + location.search, false);
  } else {
    location.reload();
  }
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

async function navegarA(url, empujarHistorial) {
  const miToken = ++navegacionEnCurso;

  try {
    const resp = await fetch(url, {
      headers: { "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin",
      cache: "no-store", // evita ver datos viejos
    });

    // Si la sesión caducó (redirige al login) o hubo un error, se navega de forma normal
    if (!resp.ok || !new URL(resp.url).pathname.startsWith("/lector/")) {
      window.location.href = url;
      return;
    }

    const html = await resp.text();
    if (miToken !== navegacionEnCurso) return; // llegó tarde, hay una navegación más nueva

    const doc = new DOMParser().parseFromString(html, "text/html");
    const nuevoContenedor = doc.querySelector(".lector-container");
    const contenedorActual = document.querySelector(".lector-container");

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
    contenedorActual.scrollTop = 0;

    if (linkNuevo && linkNuevo !== linkViejo) {
      linkNuevo.media = "all"; // aplicar CSS nuevo
      linkNuevo.id = "dynamic-specific-css";
      if (linkViejo) linkViejo.remove(); // quitar CSS viejo en el mismo instante
    } else if (!linkNuevo && linkViejo) {
      linkViejo.remove(); // la página nueva no tiene CSS propio
    }

    // 3) JS del módulo (los <script> insertados con innerHTML no se ejecutan, por eso se recrea).
    //    Al cargarse, el script se ejecuta y se engancha a la zona de contenido nueva.
    if (scriptViejo) scriptViejo.remove();
    if (jsNuevoSrc) {
      const s = document.createElement("script");
      s.id = "dynamic-specific-js";
      s.src = jsNuevoSrc;
      document.body.appendChild(s);
    }

    // 4) URL y menú activo
    if (empujarHistorial) window.history.pushState({}, "", url);
    marcarMenuActivo(url);
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
// FUNCIONES GLOBALES
// =====================================================================

// Contraer / expandir la barra lateral (se recuerda la última elección)
function toggleSidebar() {
  const sidebar = document.getElementById("sidebar");
  sidebar.classList.toggle("collapsed");
  try {
    localStorage.setItem("lectorSidebarCollapsed", sidebar.classList.contains("collapsed") ? "1" : "0");
  } catch (e) {
    /* si el navegador bloquea el almacenamiento, simplemente no se recuerda */
  }
}

// Modal de cierre de sesión
function abrirModalLogout(event) {
  event.preventDefault();
  document.getElementById("modalLogout").classList.add("active");
}

function cerrarModalLogout() {
  document.getElementById("modalLogout").classList.remove("active");
}

document.addEventListener("DOMContentLoaded", () => {
  // Restaurar el estado de la barra lateral
  try {
    if (localStorage.getItem("lectorSidebarCollapsed") === "1") {
      document.getElementById("sidebar").classList.add("collapsed");
    }
  } catch (e) {
    /* sin almacenamiento: se queda expandida */
  }

  // Cerrar el modal al hacer clic fuera de él o con la tecla Esc
  const modal = document.getElementById("modalLogout");
  if (modal) {
    modal.addEventListener("click", (e) => {
      if (e.target === modal) cerrarModalLogout();
    });
  }
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") cerrarModalLogout();
  });
});