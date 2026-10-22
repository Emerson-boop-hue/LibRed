// =========================================================
// 1. LÓGICA DE INICIO Y TRANSICIÓN DE CARGA
// =========================================================
document.addEventListener("DOMContentLoaded", function() {
    
    const loader = document.getElementById('page-loader');
    
    if (loader) {
        const dashboardLector = document.getElementById('vista-dashboard-lector');
        const dashboardAdmin = document.getElementById('vista-dashboard-admin');
        const dashboardActivo = dashboardLector || dashboardAdmin;

        if(dashboardActivo) {
            setTimeout(() => {
                loader.style.display = 'none';
                dashboardActivo.style.display = 'flex';
                
                if(dashboardLector) renderizarGraficoLector();
                if(dashboardAdmin) renderizarGraficosAdmin(); 
            }, 1500);
        }
    }
});

// =========================================================
// 2. FUNCIONES DEL LOGIN (INDEX.HTML)
// =========================================================
function cambiarModoLogin(e) {
    e.preventDefault();
    const vistaLector = document.getElementById('vista-lector');
    const vistaLibreria = document.getElementById('vista-libreria');
    const modeLink = document.getElementById('mode-switch-link');

    if (vistaLector.style.display === 'none') {
        vistaLibreria.style.display = 'none';
        vistaLector.style.display = 'block';
        modeLink.innerText = '¿Eres administrador de sede? Ingresa aquí';
    } else {
        vistaLector.style.display = 'none';
        vistaLibreria.style.display = 'block';
        modeLink.innerText = '← Volver al acceso para lectores';
    }
}

function togglePassword(idInput, btnElement) {
    const inputField = document.getElementById(idInput);
    const svgIcon = btnElement.querySelector('svg');

    if (inputField.type === 'password') {
        inputField.type = 'text';
        svgIcon.innerHTML = `<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>`;
    } else {
        inputField.type = 'password';
        svgIcon.innerHTML = `<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>`;
    }
}

function iniciarSesionLector(e) {
    if(e) e.preventDefault();
    window.open('lector.html', '_blank');
}

function ejecutarLoginApp(e) {
    if(e) e.preventDefault();
    window.open('admin.html', '_blank');
}

let slideIndex = 0;
let timerCarrusel;

function mostrarSlide(index) {
    const slides = document.querySelectorAll(".bg-slide");
    if(slides.length === 0) return; 
    slides.forEach(slide => slide.classList.remove("active"));
    if (index >= slides.length) slideIndex = 0;
    if (index < 0) slideIndex = slides.length - 1;
    slides[slideIndex].classList.add("active");
}

function moverSlider(direccion) {
    clearInterval(timerCarrusel);
    slideIndex += direccion;
    mostrarSlide(slideIndex);
    timerCarrusel = setInterval(() => { slideIndex++; mostrarSlide(slideIndex); }, 8000);
}

if(document.querySelector(".bg-slide")) {
    timerCarrusel = setInterval(() => { slideIndex++; mostrarSlide(slideIndex); }, 5000);
}

// =========================================================
// 3. FUNCIONES GENERALES (DASHBOARD)
// =========================================================
function abrirModalLogout() {
    const modal = document.getElementById('logout-modal');
    if(modal) modal.style.display = 'flex';
}

function cerrarModalLogout() {
    const modal = document.getElementById('logout-modal');
    if(modal) modal.style.display = 'none';
}

function confirmarLogout() {
    if (window.opener) {
        window.close(); 
    } else {
        window.location.href = 'index.html'; 
    }
}

function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    if(sidebar) sidebar.classList.toggle('collapsed');
}

function toggleTheme() {
    const body = document.body;
    const btnSun = document.getElementById('icon-sun');
    const btnMoon = document.getElementById('icon-moon');

    body.classList.toggle('dark-mode');

    if (body.classList.contains('dark-mode')) {
        if(btnSun) btnSun.classList.remove('active');
        if(btnMoon) btnMoon.classList.add('active');
    } else {
        if(btnMoon) btnMoon.classList.remove('active');
        if(btnSun) btnSun.classList.add('active');
    }
    
    const textColor = body.classList.contains('dark-mode') ? '#94A3B8' : '#64748B';
    const gridColor = body.classList.contains('dark-mode') ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)';

    if(window.trafficChartObj) {
        window.trafficChartObj.options.scales.x.ticks.color = textColor;
        window.trafficChartObj.options.scales.y.ticks.color = textColor;
        window.trafficChartObj.options.scales.y.grid.color = gridColor;
        window.trafficChartObj.update();
    }
    if(window.adminTrafficChartObj) {
        window.adminTrafficChartObj.options.scales.x.ticks.color = textColor;
        window.adminTrafficChartObj.options.scales.y.ticks.color = textColor;
        window.adminTrafficChartObj.options.scales.y.grid.color = gridColor;
        window.adminTrafficChartObj.update();
    }
}

function cambiarSeccion(seccionId, event) {
    const paneles = document.querySelectorAll('.seccion-panel');
    paneles.forEach(panel => panel.style.display = 'none');

    const secActiva = document.getElementById('sec-' + seccionId);
    if(secActiva) secActiva.style.display = 'flex';

    const items = document.querySelectorAll('.sidebar-menu li');
    items.forEach(item => item.classList.remove('active'));
    
    if(event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    }

    const titulos = {
        'inicio': 'Panel de Control', // Nombre profesional para el Admin
        'usuarios': 'Gestión de Lectores y Usuarios',
        'inventario': 'Control e Inventario',
        'prestamos-dev': 'Gestión de Préstamos y Devoluciones',
        'reservas': 'Administración de Reservas',
        'finanzas': 'Control de Multas e Ingresos',
        'salas': 'Gestión de Salas de Estudio',
        'reportes': 'Reportes y Estadísticas del Sistema',
        'configuracion': 'Ajustes del Sistema',
        'soporte': 'Ayuda y Soporte Técnico',
        'catalogo': 'Catálogo Principal',
        'prestamos': 'Gestión de Préstamos',
        'devoluciones': 'Historial de Devoluciones',
        'red': 'Conoce Otras Librerias'
    };
    
    const subtitulos = {
        'inicio': 'Resumen operativo general de la sucursal.',
        'usuarios': 'Administra membresías, bloqueos y datos de usuarios activos.',
        'inventario': 'Añade, edita o retira libros, tesis y revistas del sistema.',
        'prestamos-dev': 'Controla la salida, renovación y retorno de material bibliográfico.',
        'reservas': 'Supervisa las reservas de turnos y cabinas de estudio.',
        'finanzas': 'Auditoría de pagos, cobros de servicios y multas pendientes.',
        'salas': 'Monitorea la disponibilidad y aforo de las salas grupales e individuales.',
        'reportes': 'Genera y exporta métricas de uso e inventario en PDF/Excel.',
        'configuracion': 'Actualiza configuraciones de la sede y preferencias.',
        'soporte': 'Centro de asistencia técnica y bitácora de incidencias.',
        'catalogo': 'Explora y busca entre todos los recursos bibliográficos disponibles.',
        'prestamos': 'Revisa el estado de los libros y fechas de vencimiento.',
        'devoluciones': 'Consulta el historial de libros retornados.',
        'red': 'Descubre nuevas librerías y solicita ejemplares de toda nuestra red.'
    };

    const headerTit = document.getElementById('header-titulo');
    const headerSub = document.getElementById('header-subtitle');
    
    if(headerTit) headerTit.innerText = titulos[seccionId] || 'Panel de Gestión';
    if(headerSub) headerSub.innerText = subtitulos[seccionId] || '';
}

// =========================================================
// 4. LÓGICA DE GRÁFICOS (CHART.JS)
// =========================================================

function renderizarGraficoLector() {
    const canvas = document.getElementById('trafficChart');
    if(!canvas) return; 
    
    const ctx = canvas.getContext('2d');
    let gradient = ctx.createLinearGradient(0, 0, 0, 400);
    gradient.addColorStop(0, 'rgba(22, 163, 74, 0.2)'); 
    gradient.addColorStop(1, 'rgba(22, 163, 74, 0)');

    window.trafficChartObj = new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'],
            datasets: [{
                label: 'Afluencia Estimada',
                data: [180, 300, 230, 80, 200, 220, 170], 
                borderColor: '#166534',
                backgroundColor: gradient,
                borderWidth: 2,
                pointBackgroundColor: '#FFFFFF',
                pointBorderColor: '#166534',
                pointBorderWidth: 2,
                pointRadius: 4,
                fill: true,
                tension: 0.4 
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false }, ticks: { color: '#94A3B8', font: { size: 10 } } },
                y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#94A3B8', font: { size: 10 } } }
            }
        }
    });
}
function renderizarGraficosAdmin() {
    const barCanvas = document.getElementById('adminTrafficChart');
    const donutCanvas = document.getElementById('revenueChart');
    
    if(barCanvas) {
        const ctxBar = barCanvas.getContext('2d');
        window.adminTrafficChartObj = new Chart(ctxBar, {
            type: 'bar',
            data: {
                labels: ['8am', '10am', '12pm', '2pm', '4pm', '6pm', '8pm'],
                datasets: [{
                    label: 'Préstamos procesados', // Corregido: ya no dice visitas
                    data: [45, 120, 180, 150, 250, 310, 190],
                    backgroundColor: '#475569', // AQUÍ ESTÁ EL COLOR PLOMO CLARO
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { display: false }, ticks: { color: '#94A3B8' } },
                    y: { grid: { color: 'rgba(0,0,0,0.05)' }, ticks: { color: '#94A3B8' } }
                }
            }
        });
    }

   if(donutCanvas) {
        const ctxDonut = donutCanvas.getContext('2d');
        new Chart(ctxDonut, {
            type: 'doughnut',
            data: {
                labels: ['Préstamos', 'Servicios', 'Multas'],
                datasets: [{
                    data: [65, 15, 20],
                    backgroundColor: ['#334155', '#3B82F6', '#B91C1C'],
                    borderWidth: 0,
                    routerHoverOffset: 0
                }]
            },
            options: {
                responsive: true, 
                maintainAspectRatio: false,
                plugins: { 
                    legend: { display: false },
                    tooltip: { enabled: false } // <-- ESTO DESACTIVA EL RECUADRO FLOTANTE AL PASAR EL CURSOR
                },
                animation: {
                    animateRotate: false // <-- ESTO DESACTIVA LA ANIMACIÓN DE APERTURA AL CARGAR
                },
                hover: {
                    mode: null // <-- ESTO EVITA QUE SE RESALTE O SE MUEVA AL PASAR EL MOUSE
                }
            }
        });
    }
}