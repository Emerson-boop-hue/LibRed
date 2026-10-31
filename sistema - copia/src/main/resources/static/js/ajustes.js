// =====================================================================
// MÓDULO AJUSTES DEL ADMINISTRADOR
// Datos de la librería, contraseña y método de pago. Todo se guarda con fetch (JSON), se muestra el
// resultado en la misma pantalla y se mantiene al día el nombre de la tarjeta de la barra lateral.
// =====================================================================

function inicializarAjustes() {
  const formDatos = document.getElementById("formDatos");
  const formPassword = document.getElementById("formPassword");
  const formTarjeta = document.getElementById("formTarjeta");
  if (!formDatos || !formPassword || !formTarjeta) return;

  // El script puede ejecutarse dos veces sobre la misma pantalla (carga normal + navegación AJAX):
  // esta marca evita enganchar los eventos dos veces.
  if (formDatos.dataset.listo === "1") return;
  formDatos.dataset.listo = "1";

  // ---------- utilidades ----------
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

  // ---------- DATOS DE LA LIBRERÍA ----------
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
    }),
    (r) => {
      document.getElementById("ajNombre").value = r.nombreComercial;
      // Tarjeta de la barra lateral (nombre e inicial)
      const nombreBarra = document.querySelector(".sidebar .user-name");
      if (nombreBarra) nombreBarra.textContent = r.nombreComercial;
      const avatarBarra = document.querySelector(".sidebar .avatar");
      if (avatarBarra) avatarBarra.textContent = r.nombreComercial.charAt(0).toUpperCase();
    }
  );

  // ---------- CONTRASEÑA ----------
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

  // ---------- MÉTODO DE PAGO (TARJETA) ----------
  const inputNumero = document.getElementById("ajNumero");
  const inputTitular = document.getElementById("ajTitular");
  const inputVence = document.getElementById("ajVence");
  const inputCvv = document.getElementById("ajCvv");

  // Formato mientras se escribe: grupos de 4 dígitos, MM/AA y CVV de 3 dígitos (igual que el registro)
  inputNumero.addEventListener("input", () => {
    inputNumero.value = soloDigitos(inputNumero.value).slice(0, 16).replace(/(.{4})(?=.)/g, "$1 ");
  });
  inputVence.addEventListener("input", () => {
    const d = soloDigitos(inputVence.value).slice(0, 4);
    inputVence.value = d.length > 2 ? d.slice(0, 2) + "/" + d.slice(2) : d;
  });
  inputCvv.addEventListener("input", () => (inputCvv.value = soloDigitos(inputCvv.value).slice(0, 3)));

  function detectarMarca(numero) {
    if (/^4/.test(numero)) return "VISA";
    if (/^5[1-5]/.test(numero) || /^2(22[1-9]|2[3-9]\d|[3-6]\d\d|7[01]\d|720)/.test(numero)) return "MASTERCARD";
    return "TARJETA";
  }

  // Validación en el navegador. El número completo y el CVV NUNCA se envían al servidor.
  function armarTarjeta() {
    const falla = (mensaje, campo) => {
      mostrarError(formTarjeta, "errorTarjeta", mensaje, campo);
      return null;
    };

    const numero = soloDigitos(inputNumero.value);
    if (numero.length !== 16) return falla("Ingresa los 16 dígitos de la tarjeta.", "numero");

    if (inputTitular.value.trim().length < 3) return falla("Ingresa el nombre del titular.", "titular");

    const m = /^(\d{2})\/(\d{2})$/.exec(inputVence.value);
    if (!m) return falla("Ingresa el vencimiento como MM/AA.", "vence");
    const mes = parseInt(m[1], 10);
    const anio = 2000 + parseInt(m[2], 10);
    const hoy = new Date();
    const vencida = anio < hoy.getFullYear() || (anio === hoy.getFullYear() && mes < hoy.getMonth() + 1);
    if (mes < 1 || mes > 12 || vencida) return falla("Fecha inválida o tarjeta vencida (MM/AA).", "vence");

    if (!/^\d{3}$/.test(inputCvv.value)) return falla("El CVV tiene 3 dígitos.", "cvv");

    return {
      titular: inputTitular.value,
      marca: detectarMarca(numero),
      ultimosDigitos: numero.slice(-4),
      venceMes: String(mes),
      venceAnio: String(anio),
    };
  }

  const tarjetaVista = document.getElementById("tarjetaVista");
  const tarjetaVacia = document.getElementById("tarjetaVacia");
  const btnQuitar = document.getElementById("btnQuitarTarjeta");
  const tituloForm = document.getElementById("tituloFormTarjeta");

  function mostrarTarjeta(t) {
    const hay = !!t;
    tarjetaVista.hidden = !hay;
    tarjetaVacia.hidden = hay;
    btnQuitar.hidden = !hay;
    tituloForm.textContent = hay ? "Cambiar tarjeta" : "Agregar tarjeta";
    if (hay) {
      document.getElementById("tMarca").textContent = t.marca;
      document.getElementById("tNumero").textContent = "•••• •••• •••• " + t.ultimosDigitos;
      document.getElementById("tTitular").textContent = t.titular;
      document.getElementById("tVence").textContent = t.vencimiento;
    }
  }

  conectar(formTarjeta, "/admin/ajustes/tarjeta", "errorTarjeta", "okTarjeta", armarTarjeta, (r) => {
    mostrarTarjeta(r);
    formTarjeta.reset(); // se borran número y CVV de la pantalla
  });

  // Quitar tarjeta (con confirmación)
  const modal = document.getElementById("modalQuitarTarjeta");
  const btnConfirmar = document.getElementById("btnConfirmarQuitar");

  btnQuitar.addEventListener("click", () => modal.classList.add("active"));
  document.getElementById("btnCancelarQuitar").addEventListener("click", () => modal.classList.remove("active"));
  modal.addEventListener("click", (e) => {
    if (e.target === modal) modal.classList.remove("active");
  });

  btnConfirmar.addEventListener("click", async () => {
    limpiarMensajes(formTarjeta, "errorTarjeta", "okTarjeta");
    btnConfirmar.disabled = true;
    try {
      const r = await enviar("/admin/ajustes/tarjeta/eliminar", {});
      if (!r) return;
      modal.classList.remove("active");
      if (r.ok) {
        mostrarTarjeta(null);
        mostrarOk("okTarjeta", r.mensaje);
      } else {
        mostrarError(formTarjeta, "errorTarjeta", r.error || "No se pudo quitar la tarjeta.", null);
      }
    } catch (err) {
      console.error("Error al quitar la tarjeta:", err);
      modal.classList.remove("active");
      mostrarError(formTarjeta, "errorTarjeta", "No se pudo conectar con el servidor.", null);
    } finally {
      btnConfirmar.disabled = false;
    }
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