// Función centralizadora para inicializar los listeners de la vista de inventario
function inicializarInventario() {
    const inputBuscar = document.getElementById("inputBuscar");
    const selectCategoria = document.getElementById("selectCategoria");

    function ejecutarFiltro() {
        const keyword = inputBuscar ? inputBuscar.value.trim() : "";
        const categoria = selectCategoria ? selectCategoria.value : "";
        
        const url = `/admin/inventario?keyword=${encodeURIComponent(keyword)}&categoria=${encodeURIComponent(categoria)}`;

        fetch(url, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        })
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, "text/html");
            const nuevaTabla = doc.getElementById("tablaContenedor");
            
            if (nuevaTabla) {
                document.getElementById("tablaContenedor").replaceWith(nuevaTabla);
                window.history.pushState({}, "", url);
            }
        })
        .catch(err => console.error("Error al filtrar inventario:", err));
    }

    if (inputBuscar) {
        inputBuscar.removeEventListener("input", window.searchHandler);
        window.searchHandler = () => {
            clearTimeout(window.searchTimer);
            window.searchTimer = setTimeout(ejecutarFiltro, 300); 
        };
        inputBuscar.addEventListener("input", window.searchHandler);
    }

    if (selectCategoria) {
        selectCategoria.removeEventListener("change", ejecutarFiltro);
        selectCategoria.addEventListener("change", ejecutarFiltro);
    }

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
}

// Ejecutar al cargar la página de forma tradicional (F5)
document.addEventListener("DOMContentLoaded", () => {
    inicializarInventario();
});

// Ejecutar de inmediato si el script se inyecta dinámicamente mediante AJAX y el DOM ya está listo
if (document.readyState === "complete" || document.readyState === "interactive") {
    inicializarInventario();
}

// Funciones globales expuestas directamente a window para evitar errores de ámbito
window.abrirModalCrear = function() {
    const modal = document.getElementById("modalArticulo");
    if (modal) {
        // Limpiar formulario y ID
        document.getElementById("modalId").value = "";
        document.getElementById("modalTituloLabel").innerText = "Registrar Nuevo Artículo";
        document.getElementById("modalTitulo").value = "";
        document.getElementById("modalAutor").value = "";
        document.getElementById("modalStockTotal").value = "5";
        document.getElementById("modalStockDisponible").value = "5";
        
        // Habilitar categoría y cargar código sugerido
        const selectCat = document.getElementById("modalCategoria");
        selectCat.disabled = false;
        if (selectCat.value) {
            cargarCodigoSugerido(selectCat.value);
        }

        modal.style.display = "flex";
    }
};

window.abrirModalEditar = function(id, tipoRecurso, codigo, titulo, autor, stockTotal, stockDisponible) {
    const modal = document.getElementById("modalArticulo");
    if (modal) {
        document.getElementById("modalId").value = id;
        document.getElementById("modalTituloLabel").innerText = "Modificar Artículo";
        document.getElementById("modalCategoria").value = tipoRecurso;
        document.getElementById("modalCodigo").value = codigo;
        document.getElementById("modalTitulo").value = titulo;
        document.getElementById("modalAutor").value = autor;
        document.getElementById("modalStockTotal").value = stockTotal;
        document.getElementById("modalStockDisponible").value = stockDisponible;

        modal.style.display = "flex";
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