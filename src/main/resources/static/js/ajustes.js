// Módulo ajustes del administrador: datos de la librería, política de préstamos y contraseña.

function inicializarAjustes() {
  const formDatos = document.getElementById("formDatos");
  const formPassword = document.getElementById("formPassword");
  const formPolitica = document.getElementById("formPolitica");
  if (!formDatos || !formPassword || !formPolitica) return;

  // El script puede ejecutarse dos veces sobre la misma pantalla (carga normal + navegación AJAX): esta marca evita
  // enganchar los eventos dos veces.
  if (formDatos.dataset.listo === "1") return;
  formDatos.dataset.listo = "1";

  function limpiarMensajes(form, idError, idOk) {
    form.querySelectorAll(".con-error").forEach((el) => el.classList.remove("con-error"));
    document.getElementById(idError).hidden = true;
    document.getElementById(idOk).hidden = true;
  }

  function mostrarError(form, idError, mensaje, campo) {
    const caja = document.getElementById(idError);
    caja.textContent = mensaje;
    caja.hidden = false;
    if (campo) {
      const input = form.querySelector('[data-campo="' + campo + '"]');
      if (input) {
        input.classList.add("con-error");
        input.focus();
      }
    }
  }

  function mostrarOk(idOk, mensaje) {
    const caja = document.getElementById(idOk);
    caja.textContent = mensaje;
    caja.hidden = false;
  }

  // Envía un JSON al servidor. Si la sesión caducó (el servidor devuelve el login en HTML) vuelve al login.
  async function enviar(url, datos) {
    const resp = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
      credentials: "same-origin",
      body: JSON.stringify(datos || {}),
    });
    const tipo = resp.headers.get("content-type") || "";
    if (!tipo.includes("application/json")) {
      window.location.href = "/login-admin";
      return null;
    }
    return resp.json();
  }

  // Conecta un formulario con su endpoint. "armarDatos" devuelve el JSON (o null si falla una validación local).
  function conectar(form, url, idError, idOk, armarDatos, alExito) {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      limpiarMensajes(form, idError, idOk);

      const datos = armarDatos();
      if (!datos) return; // ya se mostró el error local

      const boton = form.querySelector('button[type="submit"]');
      boton.disabled = true;
      try {
        const r = await enviar(url, datos);
        if (!r) return;
        if (r.ok) {
          mostrarOk(idOk, r.mensaje);
          if (alExito) alExito(r);
        } else {
          mostrarError(form, idError, r.error || "No se pudo guardar.", r.campo);
        }
      } catch (err) {
        console.error("Error en Ajustes:", err);
        mostrarError(form, idError, "No se pudo conectar con el servidor. Intenta nuevamente.", null);
      } finally {
        boton.disabled = false;
      }
    });
  }

  const soloDigitos = (v) => (v || "").replace(/\D/g, "");

  const inputRuc = document.getElementById("ajRuc");
  const inputTel = document.getElementById("ajTelefono");
  inputRuc.addEventListener("input", () => (inputRuc.value = soloDigitos(inputRuc.value).slice(0, 11)));
  inputTel.addEventListener("input", () => (inputTel.value = inputTel.value.replace(/[^\d ]/g, "")));

  conectar(
    formDatos,
    "/admin/ajustes/datos",
    "errorDatos",
    "okDatos",
    () => ({
      nombreComercial: document.getElementById("ajNombre").value,
      ruc: inputRuc.value,
      direccion: document.getElementById("ajDireccion").value,
      telefonoContacto: inputTel.value,
      correoContacto: document.getElementById("ajCorreo").value,
      horarioAtencion: document.getElementById("ajHorario").value,
      descripcion: document.getElementById("ajDescripcion").value,
    }),
    (r) => {
      document.getElementById("ajNombre").value = r.nombreComercial;
      if (typeof pintarCompletitud === "function") pintarCompletitud(r.completitud);
      // Tarjeta de la barra lateral (nombre e inicial)
      const nombreBarra = document.querySelector(".sidebar .user-name");
      if (nombreBarra) nombreBarra.textContent = r.nombreComercial;
      const avatarBarra = document.querySelector(".sidebar .avatar");
      if (avatarBarra) avatarBarra.textContent = r.nombreComercial.charAt(0).toUpperCase();
    }
  );

  // POLÍTICA DE PRÉSTAMOS (máximo de días y tarifa por categoría)
  const inputMaxDias = document.getElementById("ajMaxDias");
  inputMaxDias.addEventListener("input", () => (inputMaxDias.value = soloDigitos(inputMaxDias.value).slice(0, 2)));

  // Límite de préstamos activos (opcional): el interruptor activa o desactiva el campo numérico
  const chkLimitar = document.getElementById("ajLimitarActivos");
  const inputMaxActivos = document.getElementById("ajMaxActivos");
  const campoMaxActivos = document.getElementById("campoMaxActivos");
  function sincronizarLimite() {
    inputMaxActivos.disabled = !chkLimitar.checked;
    campoMaxActivos.classList.toggle("apagado", !chkLimitar.checked);
  }
  chkLimitar.addEventListener("change", sincronizarLimite);
  inputMaxActivos.addEventListener("input", () => (inputMaxActivos.value = soloDigitos(inputMaxActivos.value).slice(0, 2)));
  sincronizarLimite();

  const inputsTarifa = formPolitica.querySelectorAll(".aj-input-tarifa");

  // Categorías: al desmarcar una, su tarifa se deshabilita (y se oscurece la fila)
  const checksCategoria = formPolitica.querySelectorAll(".aj-input-categoria");
  checksCategoria.forEach((chk) => {
    const fila = chk.closest(".aj-tarifa");
    const tarifa = fila.querySelector(".aj-input-tarifa");
    chk.addEventListener("change", () => {
      limpiarMensajes(formPolitica, "errorPolitica", "okPolitica");
      fila.classList.toggle("apagada", !chk.checked);
      tarifa.disabled = !chk.checked;
    });
  });
  inputsTarifa.forEach((input) => {
    // Solo dígitos y un separador decimal (máx. 2 decimales)
    input.addEventListener("input", () => {
      let v = input.value.replace(/[^\d.,]/g, "").replace(",", ".");
      const partes = v.split(".");
      if (partes.length > 2) v = partes[0] + "." + partes.slice(1).join("");
      const [entero, decimal] = v.split(".");
      input.value = decimal !== undefined ? entero.slice(0, 2) + "." + decimal.slice(0, 2) : entero.slice(0, 2);
    });
    // Al salir del campo se muestra con 2 decimales
    input.addEventListener("blur", () => {
      const n = parseFloat(input.value);
      input.value = isNaN(n) ? "0.00" : n.toFixed(2);
    });
  });

  conectar(
    formPolitica,
    "/admin/ajustes/politica",
    "errorPolitica",
    "okPolitica",
    () => {
      const datos = {
        maxDias: inputMaxDias.value,
        limitarActivos: chkLimitar.checked ? "true" : "false",
        maxActivos: inputMaxActivos.value,
      };
      inputsTarifa.forEach((input) => {
        datos["t_" + input.dataset.clave] = input.value;
      });
      checksCategoria.forEach((chk) => {
        datos["a_" + chk.dataset.clave] = chk.checked ? "true" : "false";
      });
      return datos;
    },
    null
  );

  conectar(
    formPassword,
    "/admin/ajustes/password",
    "errorPassword",
    "okPassword",
    () => ({
      passwordActual: document.getElementById("ajPassActual").value,
      passwordNueva: document.getElementById("ajPassNueva").value,
      passwordConfirmar: document.getElementById("ajPassConfirmar").value,
    }),
    () => formPassword.reset()
  );

  formPassword.querySelectorAll(".aj-ojo").forEach((boton) => {
    boton.addEventListener("click", () => {
      const input = boton.parentElement.querySelector("input");
      const icono = boton.querySelector("i");
      const mostrar = input.type === "password";
      input.type = mostrar ? "text" : "password";
      icono.classList.toggle("fa-eye", !mostrar);
      icono.classList.toggle("fa-eye-slash", mostrar);
    });
  });

  // HORARIO DE ATENCIÓN (selector de días y horas)
  const DIAS_CORTO = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
  const DIAS_LARGO = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
  const inputHorario = document.getElementById("ajHorario");
  const filasHor = [];
  let horarioTocado = false;                 // false = todavía no se usó el selector (se conserva el texto guardado)
  const textoInicial = (inputHorario.value || "").trim();

  const minutosATexto = (m) => Math.floor(m / 60) + ":" + String(m % 60).padStart(2, "0");
  function crear(tag, clase, texto) {
    const e = document.createElement(tag);
    if (clase) e.className = clase;
    if (texto !== undefined) e.textContent = texto;
    return e;
  }
  function llenarHoras(sel, desde, hasta, valor) {
    sel.textContent = "";
    for (let m = desde; m <= hasta; m += 30) {
      const o = document.createElement("option");
      o.value = m; o.textContent = minutosATexto(m);
      if (m === valor) o.selected = true;
      sel.appendChild(o);
    }
  }

  function textoHorario() {
    const act = filasHor.map((f, i) => ({ i, on: f.sw.checked, a: Number(f.ap.value), c: Number(f.ci.value) })).filter((x) => x.on);
    const grupos = [];
    act.forEach((d) => {
      const g = grupos[grupos.length - 1];
      if (g && g.fin === d.i - 1 && g.a === d.a && g.c === d.c) g.fin = d.i;
      else grupos.push({ ini: d.i, fin: d.i, a: d.a, c: d.c });
    });
    return grupos.map((g) => {
      const dias = g.ini === g.fin ? DIAS_CORTO[g.ini]
        : g.fin === g.ini + 1 ? DIAS_CORTO[g.ini] + " y " + DIAS_CORTO[g.fin]
        : DIAS_CORTO[g.ini] + " a " + DIAS_CORTO[g.fin];
      return dias + " " + minutosATexto(g.a) + "–" + minutosATexto(g.c);
    }).join("; ");
  }

  // Lee un horario guardado con este mismo formato ("Lun a Vie 9:00–18:00; Sáb 9:00–13:00"). Devuelve false si es
  // texto libre.
  function cargarHorario(texto) {
    const partes = texto.split(";").map((p) => p.trim()).filter(Boolean);
    const dias = Array.from({ length: 7 }, () => null);
    const re = /^(Lun|Mar|Mié|Jue|Vie|Sáb|Dom)(?: (a|y) (Lun|Mar|Mié|Jue|Vie|Sáb|Dom))? (\d{1,2}):(\d{2})[–-](\d{1,2}):(\d{2})$/;
    for (const p of partes) {
      const m = re.exec(p);
      if (!m) return false;
      const i = DIAS_CORTO.indexOf(m[1]);
      const j = m[3] ? DIAS_CORTO.indexOf(m[3]) : i;
      const a = Number(m[4]) * 60 + Number(m[5]);
      const c = Number(m[6]) * 60 + Number(m[7]);
      if (c <= a || a < 300 || c > 1410 || a % 30 || c % 30) return false;
      const lista = m[2] === "y" ? [i, j] : Array.from({ length: Math.max(0, j - i) + 1 }, (_, k) => i + k);
      if (m[2] !== "y" && j < i) return false;
      lista.forEach((d) => (dias[d] = { a, c }));
    }
    filasHor.forEach((f, i) => {
      f.sw.checked = !!dias[i];
      if (dias[i]) { f.ap.value = dias[i].a; f.ci.value = dias[i].c; }
    });
    return true;
  }

  function refrescarHorario() {
    filasHor.forEach((f) => {
      const on = f.sw.checked;
      f.fila.classList.toggle("off", !on);
      f.horas.hidden = !on;
      f.cerrado.hidden = on;
    });
    const t = textoHorario();
    if (horarioTocado) inputHorario.value = t;
    const resumen = document.getElementById("ajHorResumen");
    if (horarioTocado) resumen.textContent = t ? "Se mostrará así: " + t : "";
  }

  function tocarHorario() {
    horarioTocado = true;
    refrescarHorario();
  }

  function aplicarPreset(p) {
    const base = filasHor.find((f) => f.sw.checked);
    const ap = base ? Number(base.ap.value) : 540, ci = base ? Number(base.ci.value) : 1080;
    const dias = p === "semana" ? [0, 1, 2, 3, 4] : p === "sabado" ? [0, 1, 2, 3, 4, 5] : p === "todos" ? [0, 1, 2, 3, 4, 5, 6] : [];
    filasHor.forEach((f, i) => {
      const on = dias.includes(i);
      f.sw.checked = on;
      if (on) { f.ap.value = ap; f.ci.value = ci; }
    });
    tocarHorario();
  }

  (function iniciarHorario() {
    const cont = document.getElementById("ajHorDias");
    DIAS_LARGO.forEach((nombre, i) => {
      const fila = crear("div", "aj-hor-dia off");
      const sw = crear("label", "aj-sw");
      const chk = document.createElement("input"); chk.type = "checkbox"; chk.setAttribute("aria-label", "Atiende el " + nombre);
      sw.appendChild(chk); sw.appendChild(crear("span"));
      fila.appendChild(sw);
      fila.appendChild(crear("b", "", nombre));
      const cerrado = crear("span", "aj-hor-cerrado", "Cerrado");
      const horas = crear("span", "aj-hor-horas");
      horas.hidden = true;
      const ap = document.createElement("select"); ap.setAttribute("aria-label", "Hora de apertura " + nombre);
      const ci = document.createElement("select"); ci.setAttribute("aria-label", "Hora de cierre " + nombre);
      llenarHoras(ap, 300, 1350, 540);
      llenarHoras(ci, 360, 1410, 1080);
      horas.appendChild(ap); horas.appendChild(document.createTextNode("a")); horas.appendChild(ci);
      fila.appendChild(cerrado); fila.appendChild(horas);
      cont.appendChild(fila);
      filasHor.push({ fila, sw: chk, ap, ci, horas, cerrado });

      chk.addEventListener("change", tocarHorario);
      ap.addEventListener("change", () => {
        if (Number(ci.value) <= Number(ap.value)) ci.value = Math.min(Number(ap.value) + 60, 1410);
        tocarHorario();
      });
      ci.addEventListener("change", () => {
        if (Number(ci.value) <= Number(ap.value)) ap.value = Math.max(Number(ci.value) - 60, 300);
        tocarHorario();
      });
    });
    document.getElementById("ajHorPresets").addEventListener("click", (e) => {
      const b = e.target.closest("button[data-p]");
      if (b) aplicarPreset(b.dataset.p);
    });

    // Horario ya guardado: si tiene el formato del selector se carga; si es texto libre se conserva hasta que lo
    // cambien
    if (textoInicial) {
      if (cargarHorario(textoInicial)) {
        horarioTocado = true;
      } else {
        document.getElementById("ajHorResumen").textContent = "Horario actual: " + textoInicial + ". Elige los días y horas para reemplazarlo.";
      }
    }
    refrescarHorario();
  })();

  // CONFIGURACIÓN DE LA LIBRERÍA (anillo de avance)
  const CIRC = 326.73;
  const cardCompleto = document.getElementById("cardCompleto");
  const IR_A = { "Teléfono de contacto": "ajTelefono", "Correo de contacto": "ajCorreo", "Horario de atención": "ajHorPresets", "Descripción": "ajDescripcion" };

  function pintarCompletitud(c) {
    if (!cardCompleto || !c) return;
    document.getElementById("ajAnilloValor").style.strokeDashoffset = String(CIRC * (100 - c.porcentaje) / 100);
    document.getElementById("ajAnilloPct").textContent = c.porcentaje + "%";
    document.getElementById("ajAnilloEtq").textContent = c.completo ? "completo" : "configurado";
    cardCompleto.classList.toggle("completo", !!c.completo);
    document.getElementById("ajFaltanOk").hidden = !c.completo;
    document.getElementById("ajFaltanLista").hidden = !!c.completo;
    const ul = document.getElementById("ajFaltanItems");
    ul.textContent = "";
    (c.faltan || []).forEach((f) => {
      const li = document.createElement("li");
      li.dataset.ir = IR_A[f] || "ajDescripcion";
      const i = document.createElement("i"); i.className = "fa-regular fa-circle";
      const sp = document.createElement("span"); sp.textContent = f;
      li.appendChild(i); li.appendChild(document.createTextNode(" ")); li.appendChild(sp);
      ul.appendChild(li);
    });
  }

  // Al pulsar lo que falta, se baja hasta ese campo
  document.getElementById("ajFaltanItems").addEventListener("click", (e) => {
    const li = e.target.closest("li[data-ir]");
    if (!li) return;
    const destino = document.getElementById(li.dataset.ir);
    if (!destino) return;
    destino.scrollIntoView({ behavior: "smooth", block: "center" });
    if (destino.focus && destino.tagName === "INPUT") destino.focus();
  });
}

// Ejecutar al cargar la página de forma tradicional (F5)
document.addEventListener("DOMContentLoaded", () => {
  inicializarAjustes();
});

// Ejecutar de inmediato si el script se inyecta por navegación AJAX y el DOM ya está listo
if (document.readyState === "complete" || document.readyState === "interactive") {
  inicializarAjustes();
}