// =====================================================================
// MÓDULO PRÉSTAMOS DEL LECTOR
// Categoría -> Artículo -> Días. La tarifa, la fecha límite y el total se calculan solos y se
// muestran en el comprobante. El servidor vuelve a validar y a calcular todo (esto es solo vista previa).
// Se ejecuta cada vez que el layout carga el módulo (navegación AJAX), por eso es una función autocontenida.
// =====================================================================
(function () {
  const form = document.getElementById("plForm");
  if (!form) return;

  const root = document.getElementById("plRoot");
  const selCategoria = document.getElementById("plCategoria");
  const selArticulo = document.getElementById("plArticulo");
  const selDias = document.getElementById("plDias");
  const btnEnviar = document.getElementById("plEnviar");
  const btnLimpiar = document.getElementById("plLimpiar");
  const cajaError = document.getElementById("plError");
  const modal = document.getElementById("plModalOk");

  const maxDias = parseInt(root.dataset.maxDias || "7", 10);

  // ---------- utilidades ----------
  const moneda = (n) => "S/ " + Number(n).toFixed(2);
  const dosDig = (n) => String(n).padStart(2, "0");
  const formatoFecha = (f) => dosDig(f.getDate()) + "/" + dosDig(f.getMonth() + 1) + "/" + f.getFullYear();
  const fechaMas = (dias) => {
    const f = new Date();
    f.setHours(0, 0, 0, 0);
    f.setDate(f.getDate() + dias);
    return f;
  };
  const ponerTexto = (id, valor) => {
    const el = document.getElementById(id);
    if (el) el.textContent = valor;
  };

  // Tarifa de cada categoría: se lee del texto del desplegable ("... · S/ 1.50 por día · ...")
  function tarifaDeCategoria(valor) {
    const op = Array.from(selCategoria.options).find((o) => o.value === valor);
    if (!op) return 0;
    const m = /S\/\s*([\d.]+)\s+por día/.exec(op.textContent);
    return m ? parseFloat(m[1]) : 0;
  }

  function etiquetaDeCategoria(valor) {
    const op = Array.from(selCategoria.options).find((o) => o.value === valor);
    return op ? op.textContent.split("·")[0].trim() : "--";
  }

  const bloqueado = () => {
    const b = document.getElementById("plBanner");
    return !!b && b.dataset.bloqueado === "true";
  };

  function articulosBase() {
    return Array.from(document.querySelectorAll("#plBase li")).map((li) => ({
      id: li.dataset.id,
      categoria: li.dataset.categoria,
      titulo: li.dataset.titulo,
      codigo: li.dataset.codigo,
      autor: li.dataset.autor,
      stock: li.dataset.stock,
    }));
  }

  function mostrarError(msg, campo) {
    form.querySelectorAll(".con-error").forEach((e) => e.classList.remove("con-error"));
    cajaError.textContent = msg;
    cajaError.hidden = false;
    if (campo) {
      const el = form.querySelector('[data-campo="' + campo + '"]');
      if (el) el.classList.add("con-error");
    }
  }

  function limpiarError() {
    cajaError.hidden = true;
    form.querySelectorAll(".con-error").forEach((e) => e.classList.remove("con-error"));
  }

  // ---------- desplegables ----------
  function llenarDias() {
    selDias.innerHTML = "";
    for (let d = 1; d <= maxDias; d++) {
      const op = document.createElement("option");
      op.value = String(d);
      op.textContent = d + (d === 1 ? " día" : " días");
      selDias.appendChild(op);
    }
    selDias.value = String(maxDias);
  }

  function llenarArticulos(preseleccion) {
    const cat = selCategoria.value;
    selArticulo.innerHTML = "";
    if (!cat) {
      selArticulo.innerHTML = '<option value="">-- Primero elige una categoría --</option>';
      selArticulo.disabled = true;
      return;
    }
    const base = document.createElement("option");
    base.value = "";
    base.textContent = "-- Selecciona un artículo --";
    selArticulo.appendChild(base);

    articulosBase()
      .filter((a) => a.categoria === cat)
      .forEach((a) => {
        const op = document.createElement("option");
        op.value = a.id;
        op.textContent = a.titulo + " · " + a.autor + " (" + a.stock + " disp.)";
        selArticulo.appendChild(op);
      });
    selArticulo.disabled = false;
    if (preseleccion) selArticulo.value = String(preseleccion);
  }

  // ---------- comprobante en vivo ----------
  function recalcular() {
    const cat = selCategoria.value;
    const idArt = selArticulo.value;
    const dias = parseInt(selDias.value || "0", 10);
    const art = idArt ? articulosBase().find((a) => a.id === idArt) : null;

    const tarifa = cat ? tarifaDeCategoria(cat) : null;
    const limite = dias > 0 ? fechaMas(dias) : null;

    // Panel de valores calculados
    ponerTexto("plTarifa", tarifa !== null ? moneda(tarifa) : "--");
    ponerTexto("plFechaPrestamo", formatoFecha(new Date()));
    ponerTexto("plFechaLimite", limite ? formatoFecha(limite) : "--");
    ponerTexto("plTotal", tarifa !== null && dias > 0 ? moneda(tarifa * dias) : "--");

    // Comprobante
    ponerTexto("tkArticulo", art ? art.titulo : "--");
    ponerTexto("tkCodigoArticulo", art ? art.codigo : "--");
    ponerTexto("tkCategoria", cat ? etiquetaDeCategoria(cat) : "--");
    ponerTexto("tkFechaPrestamo", formatoFecha(new Date()));
    ponerTexto("tkFechaLimite", limite ? formatoFecha(limite) : "--");
    ponerTexto("tkDias", dias > 0 ? dias + (dias === 1 ? " día" : " días") : "--");
    ponerTexto("tkTarifa", tarifa !== null ? moneda(tarifa) : "--");
    ponerTexto("tkTotal", tarifa !== null && dias > 0 ? moneda(tarifa * dias) : "--");

    btnEnviar.disabled = bloqueado() || !art || dias < 1;
  }

  function limpiarFormulario() {
    selCategoria.value = "";
    llenarArticulos();
    selDias.value = String(maxDias);
    recalcular();
  }

  // ---------- envío ----------
  async function enviar(e) {
    e.preventDefault();
    if (btnEnviar.disabled) return;
    limpiarError();
    btnEnviar.disabled = true;

    try {
      const resp = await fetch("/lector/prestamos/solicitar", {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
        credentials: "same-origin",
        body: JSON.stringify({ recursoId: selArticulo.value, dias: selDias.value }),
      });
      const tipo = resp.headers.get("content-type") || "";
      if (!tipo.includes("application/json")) {
        window.location.href = "/login"; // la sesión caducó
        return;
      }
      const r = await resp.json();

      if (r.ok) {
        ponerTexto("okNumero", "#" + r.numero);
        ponerTexto("okArticulo", r.articulo);
        ponerTexto("okFechaLimite", r.fechaLimite);
        ponerTexto("okMonto", moneda(r.monto));
        modal.classList.add("active");
        await refrescarPantalla();
        limpiarFormulario();
      } else {
        mostrarError(r.error || "No se pudo registrar el préstamo.", r.campo);
        await refrescarPantalla(); // por si el stock cambió mientras tanto
      }
    } catch (err) {
      console.error("Error al solicitar el préstamo:", err);
      mostrarError("No se pudo conectar con el servidor. Intenta nuevamente.", null);
    } finally {
      recalcular();
    }
  }

  // Vuelve a pedir la pantalla y cambia solo las zonas que dependen de los datos (stock, lista, avisos)
  async function refrescarPantalla() {
    try {
      const resp = await fetch("/lector/prestamos", {
        headers: { "X-Requested-With": "XMLHttpRequest" },
        credentials: "same-origin",
      });
      if (!resp.ok) return;
      const doc = new DOMParser().parseFromString(await resp.text(), "text/html");
      ["plBanner", "plBase", "misPrestamos"].forEach((id) => {
        const nuevo = doc.getElementById(id);
        const actual = document.getElementById(id);
        if (nuevo && actual) {
          actual.innerHTML = nuevo.innerHTML;
          Array.from(nuevo.attributes).forEach((a) => actual.setAttribute(a.name, a.value));
        }
      });
      const nuevaCat = doc.getElementById("plCategoria");
      if (nuevaCat) {
        const previa = selCategoria.value;
        selCategoria.innerHTML = nuevaCat.innerHTML;
        selCategoria.value = previa;
      }
    } catch (err) {
      console.error("No se pudo refrescar la pantalla:", err);
    }
  }

  // ---------- eventos ----------
  selCategoria.addEventListener("change", () => {
    limpiarError();
    llenarArticulos();
    recalcular();
  });
  selArticulo.addEventListener("change", () => {
    limpiarError();
    recalcular();
  });
  selDias.addEventListener("change", () => {
    limpiarError();
    recalcular();
  });
  btnLimpiar.addEventListener("click", () => {
    limpiarError();
    limpiarFormulario();
  });
  form.addEventListener("submit", enviar);

  document.getElementById("plModalCerrar").addEventListener("click", () => modal.classList.remove("active"));
  modal.addEventListener("click", (e) => {
    if (e.target === modal) modal.classList.remove("active");
  });

  // ---------- arranque ----------
  llenarDias();
  const pre = root.dataset.pre; // artículo elegido desde "Solicitar" del catálogo
  if (pre) {
    const a = articulosBase().find((x) => x.id === pre);
    if (a) {
      selCategoria.value = a.categoria;
      llenarArticulos(pre);
    }
  }
  recalcular();
})();