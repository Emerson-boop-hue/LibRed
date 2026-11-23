package com.libred.sistema.config;

import com.libred.sistema.service.ImagenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protege los paneles de administrador y lector, y aplica la restricción por membresía. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImagenService imagenService;
    private final MembresiaInterceptor membresiaInterceptor;

    public WebConfig(ImagenService imagenService, MembresiaInterceptor membresiaInterceptor) {
        this.imagenService = imagenService;
        this.membresiaInterceptor = membresiaInterceptor;
    }

    // Sirve las imágenes de los artículos desde la carpeta libred-uploads (fuera del proyecto)
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/articulos/**")
                .addResourceLocations(imagenService.getCarpetaArticulos().toUri().toString())
                .setCachePeriod(3600);
    }

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
        }).addPathPatterns("/admin/**", "/suscripcion/estado", "/suscripcion/pagar", "/suscripcion/yape/codigo", "/suscripcion/renovacion/**");

        registry.addInterceptor(membresiaInterceptor).addPathPatterns("/admin/**");

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