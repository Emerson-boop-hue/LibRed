package com.libred.sistema.config;
import com.libred.sistema.service.MembresiaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;
import java.util.Set;
/** Interceptor para controlar el acceso según el estado de la membresía.
 * 
 * MembresiaInterceptor
*/
@Component
public class MembresiaInterceptor implements HandlerInterceptor {

    static final String MENSAJE = "Tu membresía está suspendida. Renuévala para volver a registrar préstamos, artículos "
            + "y lectores.";

    private static final Set<String> PERMITIDAS = Set.of(
            "/admin/devoluciones/procesar", "/admin/ajustes/password", "/admin/ayuda/solicitud");

    private static final Set<String> PREFIJOS_PERMITIDOS = Set.of("/admin/multas/", "/admin/prestamos/cancelar/");

    private static final Set<String> MODULOS_CON_AJAX = Set.of("/admin/ajustes", "/admin/inventario/portada-auto");

    private final MembresiaService membresiaService;

    public MembresiaInterceptor(MembresiaService membresiaService) {
        this.membresiaService = membresiaService;
    }
/**
     * Intercepta las solicitudes HTTP antes de que lleguen al controlador.
     *
     * @param request  la solicitud HTTP
     * @param response la respuesta HTTP
     * @param handler  el manejador del controlador
     * @return true si la solicitud debe continuar, false si se bloquea
     * @throws Exception si ocurre un error durante la interceptación
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!modifica(request)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute("libreriaId");
        if (!(id instanceof Integer libreriaId) || !membresiaService.estadoDe(libreriaId).escrituraBloqueada()) {
            return true;
        }

        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (PERMITIDAS.contains(ruta) || PREFIJOS_PERMITIDOS.stream().anyMatch(ruta::startsWith)) {
            return true;
        }

        if (MODULOS_CON_AJAX.stream().anyMatch(ruta::startsWith)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":false,\"campo\":null,\"mensaje\":\"" + MENSAJE + "\"}");
            return false;
        }

        String modulo = ruta.split("/").length > 2 ? "/admin/" + ruta.split("/")[2] : "/admin/panel";
        FlashMap flash = new FlashMap();
        flash.put("error", MENSAJE);
        flash.put("errorEntrega", MENSAJE);
        flash.setTargetRequestPath(request.getContextPath() + modulo);
        RequestContextUtils.getFlashMapManager(request).saveOutputFlashMap(flash, request, response);
        response.sendRedirect(request.getContextPath() + modulo);
        return false;
    }

    private boolean modifica(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri.contains("/eliminar/");
    }
}