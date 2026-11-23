// Inventario: filtros: buscador + categoría + KPIs (stock crítico / agotados) + "Limpiar". Se combinan entre sí.

var peticionInventario = 0;
var STOCK = { baseTotal: 0, baseDisp: 0, enPrestamo: 0 };
var IMG = {
    ancho: 240, alto: 300,               // tamaño del marco en pantalla
    salidaAncho: 480, salidaAlto: 600,   // tamaño de la imagen que se guarda (4:5)
    imagen: null, escala: 1, x: 0, y: 0, // x, y = esquina superior izquierda de la imagen dentro del marco
    modo: "vacio",                       // "vacio" (sin imagen) | "actual" (la que ya tiene) | "nueva" (recién elegida)
    urlActual: null,
    arrastre: null
};

// Función centralizadora para inicializar los listeners de la vista de inventario
function inicializarInventario() {
    registrarFiltrosInventario();
    actualizarBotonLimpiarInv();

    // Listener para actualizar el código autogenerado cuando cambie la categoría en el modal (solo al crear)
    const modalCategoria = document.getElementById("modalCategoria");
    if (modalCategoria) {
        modalCategoria.removeEventListener("change", window.modalCatHandler);
        window.modalCatHandler = () => {
            const modalId = document.getElementById("modalId").value;
            if (!modalId) { // Solo si es nuevo registro
                cargarCodigoSugerido(modalCategoria.value);
            }
        };
        modalCategoria.addEventListener("change", window.modalCatHandler);
    }

    iniciarModalArticulo();
}

// Ejecutar al cargar la página de forma tradicional (F5)
document.addEventListener("DOMContentLoaded", () => {
    inicializarInventario();
});

// Ejecutar de inmediato si el script se inyecta dinámicamente mediante AJAX y el DOM ya está listo
if (document.readyState === "complete" || document.readyState === "interactive") {
    inicializarInventario();
}

// FILTROS (eventos delegados: se registran una sola vez)
function registrarFiltrosInventario() {
    if (window.__filtrosInventarioListos) return;
    window.__filtrosInventarioListos = true;

    document.addEventListener("input", (e) => {
        if (e.target && e.target.id === "inputBuscar") {
            clearTimeout(window.searchTimer);
            window.searchTimer = setTimeout(aplicarFiltrosInventario, 300);
        }
    });

    document.addEventListener("change", (e) => {
        if (e.target && e.target.id === "selectCategoria") {
            aplicarFiltrosInventario();
        }
    });

    document.addEventListener("click", (e) => {
        if (!e.target || !e.target.closest) return;
        const portadas = e.target.closest("#btnCompletarPortadas");
        if (portadas) { completarPortadas(portadas); return; }

        const objetivo = e.target.closest("#kpiInvTotal, #kpiInvCritico, #kpiInvAgotado, #btnLimpiarFiltrosInv");
        if (!objetivo) return;

        if (objetivo.id === "kpiInvTotal" || objetivo.id === "btnLimpiarFiltrosInv") {
            limpiarFiltrosInventario();
        } else {
            const estado = objetivo.dataset.estado;
            marcarKpisInventario(estadoInventario() === estado ? "" : estado);
        }
        aplicarFiltrosInventario();
    });
}

function estadoInventario() {
    const activo = document.querySelector("#kpiInvCritico.activo, #kpiInvAgotado.activo");
    return activo ? activo.dataset.estado : "";
}

function marcarKpisInventario(estado) {
    const total = document.getElementById("kpiInvTotal");
    const critico = document.getElementById("kpiInvCritico");
    const agotado = document.getElementById("kpiInvAgotado");
    if (total) total.classList.toggle("activo", estado === "");
    if (critico) critico.classList.toggle("activo", estado === "CRITICO");
    if (agotado) agotado.classList.toggle("activo", estado === "AGOTADO");
}

function limpiarFiltrosInventario() {
    const buscar = document.getElementById("inputBuscar");
    const categoria = document.getElementById("selectCategoria");
    if (buscar) buscar.value = "";
    if (categoria) categoria.value = "";
    marcarKpisInventario("");
}

// "Limpiar" se ve atenuado cuando no hay ningún filtro activo
function actualizarBotonLimpiarInv() {
    const boton = document.getElementById("btnLimpiarFiltrosInv");
    if (!boton) return;
    const buscar = document.getElementById("inputBuscar");
    const categoria = document.getElementById("selectCategoria");
    const hayFiltros = (buscar && buscar.value.trim() !== "") || (categoria && categoria.value !== "") || estadoInventario() !== "";
    boton.classList.toggle("inactivo", !hayFiltros);
}

function aplicarFiltrosInventario() {
    const buscar = document.getElementById("inputBuscar");
    const categoria = document.getElementById("selectCategoria");

    const params = new URLSearchParams();
    const keyword = buscar ? buscar.value.trim() : "";
    const cat = categoria ? categoria.value : "";
    const estado = estadoInventario();
    if (keyword) params.set("keyword", keyword);
    if (cat) params.set("categoria", cat);
    if (estado) params.set("estado", estado);

    const consulta = params.toString();
    const url = "/admin/inventario" + (consulta ? "?" + consulta : "");
    const turno = ++peticionInventario;

    actualizarBotonLimpiarInv();

    fetch(url, { headers: { "X-Requested-With": "XMLHttpRequest" } })
        .then((respuesta) => respuesta.text())
        .then((html) => {
            if (turno !== peticionInventario) return; // llegó una respuesta más nueva: esta se descarta
            const doc = new DOMParser().parseFromString(html, "text/html");
            const nueva = doc.getElementById("tablaContenedor");
            const actual = document.getElementById("tablaContenedor");
            if (nueva && actual) {
                actual.replaceWith(nueva);
                window.history.replaceState({}, "", url);
            }
        })
        .catch((err) => console.error("Error al filtrar inventario:", err));
}

// MODAL DE ARTÍCULO: stock + imagen
function iniciarModalArticulo() {
    const form = document.getElementById("formArticulo");
    if (!form || form.dataset.listo === "1") return;
    form.dataset.listo = "1";

    document.getElementById("modalStockTotal").addEventListener("input", stockAlCambiarTotal);
    document.getElementById("modalStockDisponible").addEventListener("input", stockAlCambiarDisponible);
    form.addEventListener("submit", (e) => alEnviarArticulo(e, form));
    const campoTitulo = document.getElementById("modalTitulo");
    campoTitulo.addEventListener("input", () => { form.dataset.tituloOk = ""; mostrarErrorTitulo(""); });
    campoTitulo.addEventListener("change", () => verificarTitulo().then(mostrarErrorTitulo));

    iniciarMarcoImagen();
}

function esArticuloNuevo() {
    return !document.getElementById("modalId").value;
}

function numeroDe(campo) {
    const v = parseInt(campo.value, 10);
    return isNaN(v) ? null : v;
}

// Deja el modal listo para crear (todo el stock disponible) o para editar (el disponible se ajusta con el total)
function prepararStock(esNuevo, total, disponible) {
    STOCK.baseTotal = total;
    STOCK.baseDisp = disponible;
    STOCK.enPrestamo = esNuevo ? 0 : Math.max(0, total - disponible); // unidades que están prestadas ahora

    const disp = document.getElementById("modalStockDisponible");
    disp.readOnly = esNuevo; // en un artículo nuevo es igual al total y no se escribe
    validarStock();
}

function stockAlCambiarTotal() {
    const total = document.getElementById("modalStockTotal");
    const disp = document.getElementById("modalStockDisponible");
    const t = numeroDe(total);
    if (esArticuloNuevo()) {
        disp.value = total.value;
    } else if (t !== null) {
        // el disponible se mueve la misma cantidad que el total (partiendo de los valores base)
        disp.value = String(Math.max(0, STOCK.baseDisp + (t - STOCK.baseTotal)));
    }
    validarStock();
}

function stockAlCambiarDisponible() {
    // corrección manual: el ajuste automático se cuenta desde estos valores
    const t = numeroDe(document.getElementById("modalStockTotal"));
    const d = numeroDe(document.getElementById("modalStockDisponible"));
    if (t !== null && d !== null) {
        STOCK.baseTotal = t;
        STOCK.baseDisp = d;
    }
    validarStock();
}

// Devuelve true si el stock es válido. Si no, muestra el mensaje y deja "Guardar" con aspecto de bloqueado.
function validarStock() {
    const total = document.getElementById("modalStockTotal");
    const disp = document.getElementById("modalStockDisponible");
    const msg = document.getElementById("msgStock");
    const boton = document.getElementById("btnGuardarArticulo");
    const t = numeroDe(total);
    const d = numeroDe(disp);

    let mensaje = "";
    if ((t !== null && t < 0) || (d !== null && d < 0)) {
        mensaje = "El stock no puede ser negativo.";
    } else if (t !== null && d !== null && d > t) {
        mensaje = "El stock disponible no puede ser mayor al stock total.";
    } else if (!esArticuloNuevo() && t !== null && t < STOCK.enPrestamo) {
        mensaje = "No puedes dejar el stock total en " + t + ": hay " + STOCK.enPrestamo + " unidades prestadas en este momento.";
    }

    total.classList.toggle("invalido", mensaje !== "");
    disp.classList.toggle("invalido", mensaje !== "");
    msg.textContent = mensaje;
    msg.hidden = mensaje === "";
    if (boton) boton.classList.toggle("bloqueado", mensaje !== "");
    return mensaje === "";
}

// TÍTULO REPETIDO: aviso en rojo bajo el campo (al salir del campo y al guardar), sin cerrar el modal
function mostrarErrorTitulo(texto) {
    const msg = document.getElementById("msgTitulo");
    const campo = document.getElementById("modalTitulo");
    if (msg) {
        msg.textContent = texto || "";
        msg.hidden = !texto;
    }
    if (campo) campo.style.borderColor = texto ? "#ef4444" : "";
}

function verificarTitulo() {
    const titulo = document.getElementById("modalTitulo").value.trim();
    if (!titulo) return Promise.resolve("");
    const params = new URLSearchParams({ titulo: titulo });
    const id = document.getElementById("modalId").value;
    if (id) params.set("id", id);
    return fetch("/admin/inventario/verificar-titulo?" + params.toString(), { headers: { "X-Requested-With": "XMLHttpRequest" } })
        .then((r) => (r.ok ? r.json() : {}))
        .then((d) => d.titulo || "")
        .catch(() => ""); // sin conexión: el servidor igual vuelve a validar al guardar
}

function alEnviarArticulo(e, form) {
    // Primero se comprueba el título; si está libre se vuelve a enviar y sigue el flujo normal (stock, imagen)
    if (form.dataset.tituloOk !== "1") {
        e.preventDefault();
        verificarTitulo().then((texto) => {
            mostrarErrorTitulo(texto);
            if (texto) return;
            form.dataset.tituloOk = "1";
            if (typeof form.requestSubmit === "function") form.requestSubmit();
            else form.submit();
        });
        return;
    }

    if (!validarStock()) {
        e.preventDefault();
        const msg = document.getElementById("msgStock");
        msg.classList.remove("sacude");
        void msg.offsetWidth; // reinicia la animación
        msg.classList.add("sacude");
        return;
    }

    document.getElementById("quitarImagen").value = (IMG.modo === "vacio" && IMG.urlActual) ? "true" : "false";

    if (IMG.modo === "nueva" && form.dataset.imagenLista !== "1") {
        e.preventDefault();
        generarImagenFinal((blob) => {
            if (!blob) {
                avisoImagen("No se pudo preparar la imagen. Prueba con otra.");
                return;
            }
            const archivo = new File([blob], "articulo.jpg", { type: "image/jpeg" });
            const dt = new DataTransfer();
            dt.items.add(archivo);
            document.getElementById("inputImagenFinal").files = dt.files;
            form.dataset.imagenLista = "1";
            if (typeof form.requestSubmit === "function") {
                form.requestSubmit();
            } else {
                form.submit();
            }
        });
    }
}

// IMAGEN: marco 4:5
function iniciarMarcoImagen() {
    const marco = document.getElementById("marcoImagen");
    if (!marco || marco.dataset.listo === "1") return;
    marco.dataset.listo = "1";

    const canvas = document.getElementById("canvasImagen");
    const inputElegir = document.getElementById("inputImagenElegir");
    const zoom = document.getElementById("zoomImagen");

    // Tamaño real del lienzo: nítido también en pantallas de alta densidad
    const dpr = window.devicePixelRatio || 1;
    canvas.width = Math.round(IMG.ancho * dpr);
    canvas.height = Math.round(IMG.alto * dpr);

    document.getElementById("btnImagenElegir").addEventListener("click", () => inputElegir.click());
    marco.addEventListener("click", () => {
        if (IMG.modo === "vacio") inputElegir.click();
    });
    inputElegir.addEventListener("change", () => {
        if (inputElegir.files && inputElegir.files[0]) cargarArchivoImagen(inputElegir.files[0]);
        inputElegir.value = ""; // permite elegir otra vez el mismo archivo
    });

    // Arrastrar y soltar un archivo sobre el marco
    marco.addEventListener("dragover", (e) => { e.preventDefault(); marco.classList.add("sobre"); });
    marco.addEventListener("dragleave", () => marco.classList.remove("sobre"));
    marco.addEventListener("drop", (e) => {
        e.preventDefault();
        marco.classList.remove("sobre");
        const archivo = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
        if (archivo) cargarArchivoImagen(archivo);
    });

    // Mover la imagen con el mouse o el dedo
    canvas.addEventListener("pointerdown", (e) => {
        if (IMG.modo !== "nueva") return;
        IMG.arrastre = { px: e.clientX, py: e.clientY, x: IMG.x, y: IMG.y };
        canvas.setPointerCapture(e.pointerId);
        marco.classList.add("arrastrando");
    });
    canvas.addEventListener("pointermove", (e) => {
        if (!IMG.arrastre) return;
        IMG.x = IMG.arrastre.x + (e.clientX - IMG.arrastre.px);
        IMG.y = IMG.arrastre.y + (e.clientY - IMG.arrastre.py);
        limitarPosicion();
        dibujarMarco();
    });
    const soltar = () => {
        IMG.arrastre = null;
        marco.classList.remove("arrastrando");
    };
    canvas.addEventListener("pointerup", soltar);
    canvas.addEventListener("pointercancel", soltar);

    // Zoom: rueda del mouse (sobre el punto del cursor) y deslizador (sobre el centro del marco)
    canvas.addEventListener("wheel", (e) => {
        if (IMG.modo !== "nueva") return;
        e.preventDefault();
        const r = canvas.getBoundingClientRect();
        cambiarEscala(IMG.escala * (e.deltaY < 0 ? 1.08 : 1 / 1.08), e.clientX - r.left, e.clientY - r.top);
    }, { passive: false });
    zoom.addEventListener("input", () => {
        cambiarEscala(escalaDeDeslizador(parseFloat(zoom.value)), IMG.ancho / 2, IMG.alto / 2);
    });

    document.getElementById("btnImagenAuto").addEventListener("click", portadaAutomatica);
    document.getElementById("btnImagenAjustar").addEventListener("click", ajustarImagen);
    document.getElementById("btnImagenQuitar").addEventListener("click", quitarImagenActual);

    dibujarMarco();
    actualizarControlesImagen();
}

// Escalas posibles: "cover" (la imagen llena el marco), mínimo (se aleja hasta que sobre espacio) y máximo (x4)
function escalasDeImagen() {
    const iw = IMG.imagen.naturalWidth;
    const ih = IMG.imagen.naturalHeight;
    const cover = Math.max(IMG.ancho / iw, IMG.alto / ih);
    const contain = Math.min(IMG.ancho / iw, IMG.alto / ih);
    return { cover: cover, min: contain * 0.6, max: cover * 4 };
}

// Si la imagen es más grande que el marco no deja huecos; si es más chica se puede mover libremente dentro del marco
function limitarEje(pos, tam, marco) {
    if (tam >= marco) return Math.min(0, Math.max(marco - tam, pos));
    return Math.min(marco - tam, Math.max(0, pos));
}

function limitarPosicion() {
    IMG.x = limitarEje(IMG.x, IMG.imagen.naturalWidth * IMG.escala, IMG.ancho);
    IMG.y = limitarEje(IMG.y, IMG.imagen.naturalHeight * IMG.escala, IMG.alto);
}

function cambiarEscala(nueva, cx, cy) {
    if (!IMG.imagen || IMG.modo !== "nueva") return;
    const e = escalasDeImagen();
    nueva = Math.min(e.max, Math.max(e.min, nueva));
    const px = (cx - IMG.x) / IMG.escala; // punto de la imagen que está bajo (cx, cy): debe quedarse ahí
    const py = (cy - IMG.y) / IMG.escala;
    IMG.escala = nueva;
    IMG.x = cx - px * nueva;
    IMG.y = cy - py * nueva;
    limitarPosicion();
    sincronizarDeslizador();
    dibujarMarco();
}

function escalaDeDeslizador(valor) {
    const e = escalasDeImagen();
    return e.min * Math.pow(e.max / e.min, valor / 100);
}

function sincronizarDeslizador() {
    const e = escalasDeImagen();
    const valor = 100 * Math.log(IMG.escala / e.min) / Math.log(e.max / e.min);
    document.getElementById("zoomImagen").value = String(Math.round(valor));
}

// "Ajustar": la imagen llena el marco y queda centrada
function ajustarImagen() {
    if (!IMG.imagen) return;
    const e = escalasDeImagen();
    IMG.escala = e.cover;
    IMG.x = (IMG.ancho - IMG.imagen.naturalWidth * e.cover) / 2;
    IMG.y = (IMG.alto - IMG.imagen.naturalHeight * e.cover) / 2;
    limitarPosicion();
    sincronizarDeslizador();
    dibujarMarco();
}

// Pinta el marco (fondo plomo del admin + imagen). k = factor de escala del lienzo respecto al marco.
function pintarImagenEn(ctx, k) {
    const w = IMG.ancho * k;
    const h = IMG.alto * k;
    const fondo = ctx.createLinearGradient(0, 0, w, h);
    fondo.addColorStop(0, "#334155");
    fondo.addColorStop(1, "#1e293b");
    ctx.fillStyle = fondo;
    ctx.fillRect(0, 0, w, h);
    if (IMG.imagen) {
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(IMG.imagen, IMG.x * k, IMG.y * k, IMG.imagen.naturalWidth * IMG.escala * k, IMG.imagen.naturalHeight * IMG.escala * k);
    }
}

function dibujarMarco() {
    const canvas = document.getElementById("canvasImagen");
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    pintarImagenEn(ctx, canvas.width / IMG.ancho);
}

// Genera el JPEG final (480 x 600) con exactamente lo que se ve dentro del marco
function generarImagenFinal(callback) {
    const lienzo = document.createElement("canvas");
    lienzo.width = IMG.salidaAncho;
    lienzo.height = IMG.salidaAlto;
    pintarImagenEn(lienzo.getContext("2d"), IMG.salidaAncho / IMG.ancho);
    lienzo.toBlob(callback, "image/jpeg", 0.9);
}

function cargarArchivoImagen(archivo, contener) {
    if (!archivo.type || archivo.type.indexOf("image/") !== 0) {
        avisoImagen("El archivo debe ser una imagen (JPG o PNG).");
        return;
    }
    if (archivo.size > 15 * 1024 * 1024) {
        avisoImagen("La imagen pesa más de 15 MB. Elige una más liviana.");
        return;
    }
    const url = URL.createObjectURL(archivo);
    const img = new Image();
    img.onload = () => {
        URL.revokeObjectURL(url);
        IMG.imagen = img;
        IMG.modo = "nueva";
        if (contener) contenerImagen(); else ajustarImagen();
        actualizarControlesImagen();
    };
    img.onerror = () => {
        URL.revokeObjectURL(url);
        avisoImagen("No se pudo leer esa imagen. Prueba con un archivo JPG o PNG.");
    };
    img.src = url;
}

// "Contener": la imagen se ve ENTERA dentro del marco (sin recortar el título de la portada), centrada
function contenerImagen() {
    if (!IMG.imagen) return;
    const iw = IMG.imagen.naturalWidth;
    const ih = IMG.imagen.naturalHeight;
    IMG.escala = Math.min(IMG.ancho / iw, IMG.alto / ih);
    IMG.x = (IMG.ancho - iw * IMG.escala) / 2;
    IMG.y = (IMG.alto - ih * IMG.escala) / 2;
    limitarPosicion();
    sincronizarDeslizador();
    dibujarMarco();
}

// Botón "Automática" del modal: busca una portada y la deja como vista previa (se guarda solo al pulsar Guardar)
function portadaAutomatica() {
    const titulo = document.getElementById("modalTitulo").value.trim();
    if (!titulo) {
        avisoImagen("Escribe primero el título del artículo.");
        return;
    }
    // Si ya hay una imagen (guardada o recién elegida), se pide confirmación antes de reemplazarla
    if (IMG.modo !== "vacio" && !window.confirm("Este artículo ya tiene una imagen. ¿Reemplazarla por una portada automática?")) {
        return;
    }
    const boton = document.getElementById("btnImagenAuto");
    const icono = boton.querySelector("i");
    boton.disabled = true;
    icono.className = "fa-solid fa-spinner fa-spin";

    const params = new URLSearchParams({
        titulo: titulo,
        autor: document.getElementById("modalAutor").value.trim(),
        categoria: document.getElementById("modalCategoria").value
    });
    fetch("/admin/inventario/portada-auto?" + params.toString())
        .then((r) => {
            if (!r.ok) throw new Error("sin portada");
            return r.blob();
        })
        .then((blob) => cargarArchivoImagen(new File([blob], "portada.jpg", { type: blob.type || "image/jpeg" }), true))
        .catch(() => avisoImagen("No se pudo obtener una portada. Revisa tu conexión a internet."))
        .finally(() => {
            boton.disabled = false;
            icono.className = "fa-solid fa-wand-magic-sparkles";
        });
}

// Botón "Completar portadas" de la barra superior: rellena SOLO los artículos sin imagen (nunca pisa las existentes)
function completarPortadas(boton) {
    if (boton.disabled) return;
    const etiqueta = boton.querySelector("span");
    const textoOriginal = etiqueta.innerHTML;
    boton.disabled = true;
    boton.classList.remove("error");

    const terminar = (resto, fallo) => {
        boton.disabled = false;
        if (fallo) {
            boton.classList.add("error");
            etiqueta.textContent = "No se pudieron obtener portadas";
            setTimeout(() => { boton.classList.remove("error"); etiqueta.innerHTML = textoOriginal; }, 4000);
        } else if (resto <= 0) {
            boton.remove();
        } else {
            etiqueta.innerHTML = 'Completar portadas (<b id="portadasPendientes">' + resto + "</b>)";
        }
        aplicarFiltrosInventario(); // vuelve a pintar la tabla con las portadas nuevas
    };

    fetch("/admin/inventario/portadas-pendientes")
        .then((r) => r.json())
        .then((ids) => {
            if (!ids.length) { terminar(0, false); return; }
            let siguiente = 0, hechos = 0, puestas = 0;
            const total = ids.length;
            const trabajar = () => {
                if (siguiente >= total) return Promise.resolve();
                const id = ids[siguiente++];
                return fetch("/admin/inventario/portada-auto/" + id, { method: "POST" })
                    .then((r) => r.json())
                    .then((d) => { if (d && d.ok) puestas++; })
                    .catch(() => {})
                    .then(() => {
                        hechos++;
                        etiqueta.textContent = "Completando " + hechos + " / " + total;
                        return trabajar();
                    });
            };
            etiqueta.textContent = "Completando 0 / " + total;
            // 3 búsquedas a la vez para que sea más rápido
            return Promise.all([trabajar(), trabajar(), trabajar()])
                .then(() => terminar(total - puestas, puestas === 0));
        })
        .catch(() => terminar(1, true));
}

// Muestra en el marco la imagen que ya tiene el artículo (o el marco vacío si no tiene)
function mostrarImagenActual(url) {
    IMG.urlActual = url || null;
    IMG.imagen = null;
    IMG.modo = "vacio";
    IMG.arrastre = null;
    if (url) {
        const img = new Image();
        img.onload = () => {
            if (IMG.urlActual !== url) return; // se abrió otro artículo mientras cargaba
            IMG.imagen = img;
            IMG.modo = "actual";
            ajustarImagen();
            actualizarControlesImagen();
        };
        img.onerror = () => actualizarControlesImagen();
        img.src = url;
    }
    dibujarMarco();
    actualizarControlesImagen();
}

function quitarImagenActual() {
    IMG.imagen = null;
    IMG.modo = "vacio";
    dibujarMarco();
    actualizarControlesImagen();
}

function avisoImagen(texto) {
    const ayuda = document.getElementById("ayudaImagen");
    if (!ayuda) return;
    ayuda.textContent = texto;
    ayuda.hidden = false;
    clearTimeout(window.__avisoImg);
    window.__avisoImg = setTimeout(() => { ayuda.hidden = true; }, 3500);
}

function actualizarControlesImagen() {
    const marco = document.getElementById("marcoImagen");
    if (!marco) return;
    document.getElementById("marcoVacio").hidden = IMG.modo !== "vacio";
    marco.classList.toggle("vacio", IMG.modo === "vacio");
    document.getElementById("zoomFila").hidden = IMG.modo !== "nueva";
    document.getElementById("btnImagenAjustar").hidden = IMG.modo !== "nueva";
    document.getElementById("btnImagenQuitar").hidden = IMG.modo === "vacio";
    document.getElementById("btnImagenAuto").classList.toggle("solo-icono", IMG.modo !== "vacio");
    document.querySelector("#btnImagenElegir span").textContent = IMG.modo === "vacio" ? "Elegir imagen" : "Cambiar";
}

// FUNCIONES GLOBALES DE LOS MODALES (expuestas a window para evitar errores de ámbito)
window.abrirModalCrear = function() {
    const modal = document.getElementById("modalArticulo");
    if (modal) {
        // Limpiar formulario y ID
        document.getElementById("modalId").value = "";
        mostrarErrorTitulo("");
        const formNuevo = document.getElementById("formArticulo");
        if (formNuevo) formNuevo.dataset.tituloOk = "";
        document.getElementById("modalTituloLabel").innerText = "Registrar Nuevo Artículo";
        document.getElementById("modalTitulo").value = "";
        document.getElementById("modalAutor").value = "";
        document.getElementById("modalStockTotal").value = "5";
        document.getElementById("modalStockDisponible").value = "5";
        prepararStock(true, 5, 5);

        // Imagen: marco vacío
        const form = document.getElementById("formArticulo");
        if (form) form.dataset.imagenLista = "";
        document.getElementById("inputImagenFinal").value = "";
        document.getElementById("quitarImagen").value = "false";
        document.getElementById("modalCodigo").value = "";

        const selectCat = document.getElementById("modalCategoria");
        selectCat.disabled = false;

        // El modal se muestra primero; lo demás se hace en el siguiente cuadro
        modal.style.display = "flex";
        requestAnimationFrame(() => {
            mostrarImagenActual(null);
            if (selectCat.value) cargarCodigoSugerido(selectCat.value);
        });
    }
};

window.abrirModalEditar = function(id, tipoRecurso, codigo, titulo, autor, stockTotal, stockDisponible, imagenUrl) {
    const modal = document.getElementById("modalArticulo");
    if (modal) {
        document.getElementById("modalId").value = id;
        mostrarErrorTitulo("");
        const formEdit = document.getElementById("formArticulo");
        if (formEdit) formEdit.dataset.tituloOk = "";
        document.getElementById("modalTituloLabel").innerText = "Modificar Artículo";
        document.getElementById("modalCategoria").value = tipoRecurso;
        document.getElementById("modalCodigo").value = codigo;
        document.getElementById("modalTitulo").value = titulo;
        document.getElementById("modalAutor").value = autor;
        document.getElementById("modalStockTotal").value = stockTotal;
        document.getElementById("modalStockDisponible").value = stockDisponible;
        prepararStock(false, Number(stockTotal), Number(stockDisponible));

        // Imagen: la que ya tiene el artículo (o el marco vacío)
        const form = document.getElementById("formArticulo");
        if (form) form.dataset.imagenLista = "";
        document.getElementById("inputImagenFinal").value = "";
        document.getElementById("quitarImagen").value = "false";
        modal.style.display = "flex";
        requestAnimationFrame(() => mostrarImagenActual(imagenUrl));
    }
};

window.cerrarModalArticulo = function() {
    const modal = document.getElementById("modalArticulo");
    if (modal) {
        modal.style.display = "none";
    }
};

window.abrirModalEliminar = function(id, titulo, codigo) {
    const modal = document.getElementById("modalEliminar");
    if (modal) {
        document.getElementById("eliminarTitulo").innerText = titulo;
        document.getElementById("eliminarCodigo").innerText = codigo;
        document.getElementById("btnConfirmarEliminar").href = `/admin/inventario/eliminar/${id}`;
        modal.style.display = "flex";
    }
};

window.cerrarModalEliminar = function() {
    const modal = document.getElementById("modalEliminar");
    if (modal) {
        modal.style.display = "none";
    }
};

// Función AJAX para obtener el código secuencial del backend
function cargarCodigoSugerido(tipoRecurso) {
    fetch(`/admin/inventario/codigo-sugerido?tipoRecurso=${encodeURIComponent(tipoRecurso)}`)
        .then(response => response.text())
        .then(codigo => {
            const inputCodigo = document.getElementById("modalCodigo");
            if (inputCodigo) {
                inputCodigo.value = codigo;
            }
        })
        .catch(err => console.error("Error al obtener código sugerido:", err));
}