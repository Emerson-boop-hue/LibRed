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
    calificaciones: document.getElementById("panelCalificaciones"),
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

  // ---------- pestaña Calificaciones: filtros (las filas pueden aumentar al calificar, por eso se leen en cada filtrado) ----------
  const hcCuerpo = document.getElementById("hcCuerpo");
  const hcTexto = document.getElementById("hcTexto");
  const hcPuntaje = document.getElementById("hcPuntaje");
  const hcCategoria = document.getElementById("hcCategoria");
  const hcLimpiar = document.getElementById("hcLimpiar");

  function ocultar(id, si) { const e = document.getElementById(id); if (e) e.classList.toggle("oculto", si); }

  function filtrarCalificaciones() {
    if (!hcCuerpo) return;
    const filas = Array.from(hcCuerpo.querySelectorAll("tr"));
    const texto = hcTexto.value.trim().toLowerCase();
    let visibles = 0;
    filas.forEach((f) => {
      let ok = !texto || f.dataset.texto.includes(texto);
      if (hcPuntaje.value && f.dataset.puntaje !== hcPuntaje.value) ok = false;
      if (hcCategoria.value && f.dataset.categoria !== hcCategoria.value) ok = false;
      f.hidden = !ok;
      if (ok) visibles++;
    });
    document.getElementById("hcContador").textContent = visibles;
    document.getElementById("hcContadorTexto").textContent = visibles === 1 ? "calificación" : "calificaciones";
    document.getElementById("hcSinResultados").hidden = !(filas.length > 0 && visibles === 0);
    // sin ninguna calificación: solo se muestra el mensaje de vacío
    ocultar("hcFiltros", filas.length === 0);
    ocultar("hcResumen", filas.length === 0);
    ocultar("hcTablaWrap", filas.length === 0);
    ocultar("hcVacio", filas.length > 0);
    const tabNum = document.getElementById("hcTabNum");
    if (tabNum) tabNum.textContent = filas.length;
  }
  if (hcCuerpo) {
    hcTexto.addEventListener("input", filtrarCalificaciones);
    hcPuntaje.addEventListener("change", filtrarCalificaciones);
    hcCategoria.addEventListener("change", filtrarCalificaciones);
    hcLimpiar.addEventListener("click", () => {
      hcTexto.value = ""; hcPuntaje.value = ""; hcCategoria.value = "";
      filtrarCalificaciones();
      hcTexto.focus();
    });
  }

  // ---------- calificar un préstamo devuelto / cambiar una calificación ----------
  const modal = document.getElementById("hsModalCalif");
  if (modal) {
    const botonesEstrella = Array.from(modal.querySelectorAll(".hs-estrella"));
    const cara = document.getElementById("hsCalifCara");
    const etiqueta = document.getElementById("hsCalifEtiqueta");
    const btnGuardar = document.getElementById("hsCalifGuardar");
    const errorBox = document.getElementById("hsCalifError");
    const avisoCambio = document.getElementById("hsCalifAviso");
    const tituloModal = document.getElementById("hsCalifTitulo");
    const toast = document.getElementById("hsToast");

    const NIVELES = {
      0: { txt: "Elige de 1 a 5 estrellas", icono: "fa-regular fa-face-meh-blank" },
      1: { txt: "Muy malo", icono: "fa-regular fa-face-angry" },
      2: { txt: "Malo", icono: "fa-regular fa-face-frown" },
      3: { txt: "Regular", icono: "fa-regular fa-face-meh" },
      4: { txt: "Bueno", icono: "fa-regular fa-face-smile" },
      5: { txt: "Excelente", icono: "fa-regular fa-face-grin-stars" },
    };

    let actual = null;      // { prestamo, titulo, codigo, categoria, estrellas, modo: "nueva" | "cambio" }
    let elegido = 0;
    let confirmando = false; // en modo "cambio" hay una confirmación antes de enviar

    function textoBoton() {
      if (actual && actual.modo === "cambio") {
        btnGuardar.innerHTML = confirmando
          ? '<i class="fa-solid fa-check"></i> Sí, confirmar cambio'
          : '<i class="fa-solid fa-pen"></i> Guardar cambio';
      } else {
        btnGuardar.innerHTML = '<i class="fa-solid fa-paper-plane"></i> Enviar calificación';
      }
    }

    function pintar(nivel, definitivo) {
      botonesEstrella.forEach((b) => b.classList.toggle("on", Number(b.dataset.valor) <= nivel));
      cara.dataset.nivel = nivel;
      cara.innerHTML = '<i class="' + NIVELES[nivel].icono + '"></i>';
      etiqueta.textContent = NIVELES[nivel].txt;
      etiqueta.classList.toggle("elegida", definitivo && nivel > 0);
    }

    function abrir(datos) {
      actual = datos;
      elegido = datos.estrellas;
      confirmando = false;
      document.getElementById("hsCalifArticulo").textContent = datos.titulo;
      tituloModal.textContent = datos.modo === "cambio" ? "Cambiar mi calificación" : "¿Cómo fue tu experiencia?";
      avisoCambio.hidden = datos.modo !== "cambio";
      errorBox.hidden = true;
      pintar(elegido, true);
      textoBoton();
      // en modo "cambio" hay que elegir un valor distinto al actual
      btnGuardar.disabled = datos.modo === "cambio" ? true : elegido === 0;
      modal.hidden = false;
      (botonesEstrella[0] || btnGuardar).focus();
    }
    function cerrar() { modal.hidden = true; actual = null; confirmando = false; }

    botonesEstrella.forEach((b) => {
      b.addEventListener("mouseenter", () => pintar(Number(b.dataset.valor), false));
      b.addEventListener("focus", () => pintar(Number(b.dataset.valor), false));
      b.addEventListener("click", () => {
        elegido = Number(b.dataset.valor);
        confirmando = false;
        textoBoton();
        pintar(elegido, true);
        btnGuardar.disabled = actual && actual.modo === "cambio" ? elegido === actual.estrellas : false;
      });
    });
    document.getElementById("hsCalifEstrellas").addEventListener("mouseleave", () => pintar(elegido, true));

    document.getElementById("hsCalifCerrar").addEventListener("click", cerrar);
    document.getElementById("hsCalifCancelar").addEventListener("click", cerrar);
    modal.addEventListener("click", (e) => { if (e.target === modal) cerrar(); });
    if (window.__hsEsc) document.removeEventListener("keydown", window.__hsEsc);
    window.__hsEsc = (e) => { if (e.key === "Escape" && !modal.hidden) cerrar(); };
    document.addEventListener("keydown", window.__hsEsc);

    // "Calificar" (pestaña Préstamos)
    const cuerpo = document.getElementById("hsCuerpo");
    if (cuerpo) {
      cuerpo.addEventListener("click", (e) => {
        const boton = e.target.closest(".hs-btn-calificar");
        if (!boton) return;
        const c = boton.closest(".hs-calif");
        abrir({ modo: "nueva", prestamo: c.dataset.prestamo, titulo: c.dataset.titulo, codigo: c.dataset.codigo,
                categoria: c.dataset.categoria, estrellas: 0 });
      });
    }
    // "Cambiar" (pestaña Calificaciones)
    if (hcCuerpo) {
      hcCuerpo.addEventListener("click", (e) => {
        const boton = e.target.closest(".hs-btn-cambiar");
        if (!boton) return;
        abrir({ modo: "cambio", prestamo: boton.dataset.prestamo, titulo: boton.dataset.titulo,
                estrellas: Number(boton.dataset.estrellas) });
      });
    }

    function avisar(msg) {
      toast.textContent = msg;
      toast.hidden = false;
      clearTimeout(avisar.t);
      avisar.t = setTimeout(() => (toast.hidden = true), 2600);
    }

    const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

    function agregarFilaCalificacion(d, r) {
      if (!hcCuerpo) return;
      const tr = document.createElement("tr");
      tr.dataset.texto = (d.titulo + " " + d.codigo).toLowerCase();
      tr.dataset.puntaje = r.estrellas;
      tr.dataset.categoria = d.categoria;
      tr.dataset.prestamo = d.prestamo;
      tr.innerHTML =
        "<td>#" + esc(d.prestamo) + "</td>" +
        '<td><div class="hs-art-titulo" title="' + esc(d.titulo) + '">' + esc(d.titulo) + '</div><div class="hs-art-codigo">' + esc(d.codigo) + "</div></td>" +
        "<td>" + esc(d.categoria) + "</td>" +
        '<td class="hc-nota"><span class="hs-estrellas" style="--p:' + r.estrellas * 20 + '%">★★★★★</span> <span class="hc-palabra">' + esc(r.etiqueta) + "</span></td>" +
        "<td><div>" + esc(r.fecha) + "</div></td>" +
        '<td class="hc-accion"><button type="button" class="hs-btn-cambiar" data-prestamo="' + esc(d.prestamo) + '" data-titulo="' + esc(d.titulo) +
        '" data-estrellas="' + r.estrellas + '"><i class="fa-solid fa-pen"></i> Cambiar</button></td>';
      hcCuerpo.insertBefore(tr, hcCuerpo.firstChild);
    }

    function actualizarFilaCalificacion(d, r) {
      const tr = hcCuerpo && hcCuerpo.querySelector('tr[data-prestamo="' + d.prestamo + '"]');
      if (!tr) return;
      tr.dataset.puntaje = r.estrellas;
      tr.querySelector(".hs-estrellas").style.setProperty("--p", r.estrellas * 20 + "%");
      tr.querySelector(".hc-palabra").textContent = r.etiqueta;
      const fecha = tr.children[4];
      fecha.innerHTML = "<div>" + esc(r.fecha) + '</div><div class="hs-sub hc-modificada">Modificada</div>';
      const accion = tr.querySelector(".hc-accion");
      if (accion) accion.innerHTML = ""; // ya usó su única modificación
    }

    btnGuardar.addEventListener("click", async () => {
      if (!elegido || !actual) return;
      // En modo "cambio" primero se pide confirmar (es la única oportunidad)
      if (actual.modo === "cambio" && !confirmando) {
        confirmando = true;
        textoBoton();
        etiqueta.textContent = "Esta será tu única modificación. ¿Confirmas?";
        return;
      }
      btnGuardar.disabled = true;
      errorBox.hidden = true;
      const d = actual;
      try {
        const resp = await fetch("/lector/historial/calificar", {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
          credentials: "same-origin",
          body: JSON.stringify({ prestamoId: d.prestamo, estrellas: String(elegido) }),
        });
        const tipo = resp.headers.get("content-type") || "";
        if (!tipo.includes("application/json")) { window.location.href = "/login"; return; }
        const r = await resp.json();
        if (!r.ok) {
          errorBox.textContent = r.error || "No se pudo guardar la calificación.";
          errorBox.hidden = false;
          btnGuardar.disabled = false;
          confirmando = false;
          textoBoton();
          return;
        }
        // Se actualiza la pantalla sin recargar
        const celda = document.querySelector('#hsCuerpo .hs-calif[data-prestamo="' + d.prestamo + '"]');
        if (celda) {
          celda.dataset.estrellas = r.estrellas;
          celda.innerHTML = '<span class="hs-calif-hecha" title="Ya calificaste este préstamo">' +
            '<span class="hs-estrellas" style="--p:' + r.estrellas * 20 + '%">★★★★★</span></span>';
        }
        if (d.modo === "cambio") actualizarFilaCalificacion(d, r);
        else agregarFilaCalificacion(d, r);
        filtrarCalificaciones();
        cerrar();
        avisar(d.modo === "cambio" ? "Tu calificación fue modificada: " + r.etiqueta : "¡Gracias por tu calificación: " + r.etiqueta + "!");
      } catch (err) {
        errorBox.textContent = "No se pudo conectar. Inténtalo de nuevo.";
        errorBox.hidden = false;
        btnGuardar.disabled = false;
      }
    });
  }

  filtrarCalificaciones();
})();