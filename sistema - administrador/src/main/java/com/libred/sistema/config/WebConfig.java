package com.libred.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Protege todo el panel de administración: si no hay una librería en la sesión
 * (atributo "libreriaId", que guarda AuthController al iniciar sesión como admin),
 * no se deja entrar y se manda al login.
 *
 *  - Rutas /admin/...  -> redirige a /login-admin
 *  - Endpoints JSON de suscripción (estado y migrar) -> responde 401
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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
    }
}