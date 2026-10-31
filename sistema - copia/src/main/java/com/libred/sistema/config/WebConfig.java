package com.libred.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Protege los dos paneles de la aplicación:
 *
 *  1) ADMINISTRADOR: si no hay una librería en la sesión (atributo "libreriaId", que guarda
 *     AuthController al iniciar sesión como admin), no se deja entrar.
 *       - Rutas /admin/...  -> redirige a /login-admin
 *       - Endpoints JSON de suscripción (estado y migrar) -> responde 401
 *
 *  2) LECTOR: si no hay un lector en la sesión (atributo "lectorId", que guarda AuthController
 *     al iniciar sesión como lector), no se deja entrar.
 *       - Rutas /lector/...  -> redirige a /login
 *
 * Son atributos de sesión DISTINTOS a propósito: un lector nunca recibe "libreriaId",
 * así que jamás puede abrir el panel de administración.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ---------- ADMINISTRADOR (sin cambios) ----------
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                HttpSession session = request.getSession(false);
                Object id = (session == null) ? null : session.getAttribute("libreriaId");
                if (id instanceof Integer) {
                    return true;
                }
                String ruta = request.getRequestURI();
                if (ruta.startsWith(request.getContextPath() + "/admin")) {
                    response.sendRedirect(request.getContextPath() + "/login-admin");
                } else {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                }
                return false;
            }
        }).addPathPatterns("/admin/**", "/suscripcion/estado", "/suscripcion/migrar-anual");

        // ---------- LECTOR (NUEVO) ----------
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                HttpSession session = request.getSession(false);
                Object id = (session == null) ? null : session.getAttribute("lectorId");
                if (id instanceof Integer) {
                    return true;
                }
                response.sendRedirect(request.getContextPath() + "/login");
                return false;
            }
        }).addPathPatterns("/lector/**").excludePathPatterns("/lector/logout");
    }
}