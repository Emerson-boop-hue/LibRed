package com.libred.sistema.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) throws Exception {
                // Obtener la sesión actual sin crear una nueva si no existe
                HttpSession s = req.getSession(false);
                
                // Si no hay sesión o no se encuentra el ID del lector, denegar el acceso
                if (s == null || s.getAttribute("lectorId") == null) {
                    res.sendRedirect("/login");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/lector/**"); // Protege todas las rutas de los lectores automáticamente
    }
}
