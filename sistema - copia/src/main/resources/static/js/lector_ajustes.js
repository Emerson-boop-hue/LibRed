// =====================================================================
// MÓDULO AJUSTES DEL LECTOR
// Guarda los datos personales y la contraseña con fetch (JSON), muestra el resultado en la misma
// pantalla y mantiene al día el nombre de la barra lateral.
// =====================================================================
(function () {
  const formPerfil = document.getElementById("formPerfil");
  const formPassword = document.getElementById("formPassword");
  if (!formPerfil || !formPassword) return;

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
      body: JSON.stringify(datos),
    });
    const tipo = resp.headers.get("content-type") || "";
    if (!tipo.includes("application/json")) {
      window.location.href = "/login";
      return null;
    }
    return resp.json();
  }

  // Conecta un formulario: valida en el servidor, muestra mensajes y bloquea el botón mientras guarda
  function conectar(form, url, idError, idOk, armarDatos, alExito) {
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      limpiarMensajes(form, idError, idOk);

      const boton = form.querySelector('button[type="submit"]');
      boton.disabled = true;
      try {
        const r = await enviar(url, armarDatos());
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

  // ---------- datos personales ----------
  const inputTelefono = document.getElementById("ajTelefono");
  // Solo dígitos y espacios en el teléfono
  inputTelefono.addEventListener("input", () => {
    inputTelefono.value = inputTelefono.value.replace(/[^\d ]/g, "");
  });

  conectar(
    formPerfil,
    "/lector/ajustes/perfil",
    "errorPerfil",
    "okPerfil",
    () => ({
      nombres: document.getElementById("ajNombres").value,
      apellidos: document.getElementById("ajApellidos").value,
      telefono: inputTelefono.value,
    }),
    (r) => {
      // Reflejar el cambio en los campos, en la tarjeta lateral y en la barra lateral
      document.getElementById("ajNombres").value = r.nombres;
      document.getElementById("ajApellidos").value = r.apellidos;
      inputTelefono.value = r.telefono;
      document.getElementById("ajNombreCompleto").textContent = r.nombreCompleto;
      document.getElementById("ajAvatar").textContent = r.nombres.charAt(0).toUpperCase();

      const nombreBarra = document.querySelector(".sidebar .user-name");
      if (nombreBarra) nombreBarra.textContent = r.nombreCompleto;
      const avatarBarra = document.querySelector(".sidebar .avatar");
      if (avatarBarra) avatarBarra.textContent = r.nombreCompleto.charAt(0).toUpperCase();
    }
  );

  // ---------- contraseña ----------
  conectar(
    formPassword,
    "/lector/ajustes/password",
    "errorPassword",
    "okPassword",
    () => ({
      passwordActual: document.getElementById("ajPassActual").value,
      passwordNueva: document.getElementById("ajPassNueva").value,
      passwordConfirmar: document.getElementById("ajPassConfirmar").value,
    }),
    () => formPassword.reset()
  );

  // Ojito para mostrar / ocultar la contraseña
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
})();