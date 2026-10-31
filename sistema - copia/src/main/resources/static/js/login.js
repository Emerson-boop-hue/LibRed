// Funcionalidad para mostrar/ocultar la contraseña con el ojito
function togglePass(inputId, iconId) {
    const input = document.getElementById(inputId);
    const icon = document.getElementById(iconId);
    if (input.type === "password") {
        input.type = "text";
        icon.classList.remove("fa-eye");
        icon.classList.add("fa-eye-slash");
    } else {
        input.type = "password";
        icon.classList.remove("fa-eye-slash");
        icon.classList.add("fa-eye");
    }
}

// Configuración de los textos del carrusel dinámico
const slides = [
    { title: 'Conectando el inventario de librerías locales', desc: 'Una sola plataforma para consultar libros físicos, tesis y espacios académicos en tu distrito.' },
    { title: 'Aparta tus ejemplares favoritos', desc: 'Asegura libros, revistas y tesis desde la plataforma y recógelos en tu librería local de manera rápida y segura.' },
    { title: 'Digitaliza tu sucursal desde S/ 29/mes', desc: 'Punto de venta (POS), control de stock atómico y aislamiento total de datos garantizado.' }
];

let currentSlide = 0;
const bgs = document.querySelectorAll('.slide-bg');
const titleElement = document.getElementById('sliderTitle');
const descElement = document.getElementById('sliderDesc');
const textContainer = document.getElementById('sliderText');
let slideInterval;

function updateSlide() {
    textContainer.style.opacity = '0';
    bgs.forEach((bg, index) => {
        if (index === currentSlide) {
            bg.classList.add('active');
        } else {
            bg.classList.remove('active');
        }
    });
    setTimeout(() => {
        titleElement.innerText = slides[currentSlide].title;
        descElement.innerText = slides[currentSlide].desc;
        textContainer.style.opacity = '1';
    }, 500); 
}

function nextSlide() {
    currentSlide = (currentSlide + 1) % slides.length;
    updateSlide();
    resetInterval();
}

function prevSlide() {
    currentSlide = (currentSlide - 1 + slides.length) % slides.length;
    updateSlide();
    resetInterval();
}

function resetInterval() {
    clearInterval(slideInterval);
    slideInterval = setInterval(nextSlide, 6000); 
}

resetInterval();

// Cambio dinámico de formularios y manejo de errores sin recargar la página (SPA)
function switchForm(event, url) {
    event.preventDefault();
    fetch(url, {
        headers: { "X-Requested-With": "XMLHttpRequest" }
    })
    .then(response => response.text())
    .then(html => {
        document.getElementById('cardContent').outerHTML = html;
        window.history.pushState({}, '', url);
    })
    .catch(err => console.error("Error:", err));
}

function submitForm(event, url) {
    event.preventDefault();
    const form = event.target;
    const formData = new URLSearchParams(new FormData(form));

    fetch(url, {
        method: 'POST',
        headers: {
            "X-Requested-With": "XMLHttpRequest",
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: formData
    })
    .then(response => {
        // Si el login es exitoso, el servidor hará un redirect
        if (response.redirected) {
            window.location.href = response.url;
        } else {
            return response.text();
        }
    })
    .then(html => {
        if (html) {
            document.getElementById('cardContent').outerHTML = html;
        }
    })
    .catch(err => console.error("Error en el login:", err));
}