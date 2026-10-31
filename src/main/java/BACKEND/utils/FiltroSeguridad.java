package BACKEND.utils;

import java.security.Principal;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Filtro que se ejecuta ANTES de cada servicio REST.
 * Exige "Authorization: Bearer <token>" excepto en las rutas públicas.
 */
@Provider
public class FiltroSeguridad implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        String metodo = ctx.getMethod();
        String ruta = ctx.getUriInfo().getPath();
        if (ruta.startsWith("/")) {
            ruta = ruta.substring(1);
        }

        if ("OPTIONS".equals(metodo) || esPublica(metodo, ruta)) {
            return;
        }

        String encabezado = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        String token = (encabezado != null && encabezado.startsWith("Bearer ")) ? encabezado.substring(7) : null;
        final UsuarioSesion usuario = Seguridad.validarToken(token);

        if (usuario == null) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new jsonResult(-1, "ERROR", "Sesión no válida o expirada. Inicie sesión nuevamente."))
                    .build());
            return;
        }

        // Una unidad de reparto solo usa su app de ruta (no ve pedidos, pagos ni devoluciones de las sucursales)
        if (usuario.esVendedor() && !permitidaVendedor(ruta)) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new jsonResult(-1, "ERROR", "Esta opción no está disponible para las unidades de reparto."))
                    .build());
            return;
        }

        final boolean seguro = ctx.getSecurityContext() != null && ctx.getSecurityContext().isSecure();
        ctx.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return usuario;
            }

            @Override
            public boolean isUserInRole(String rol) {
                return rol != null && rol.equals(usuario.getRol());
            }

            @Override
            public boolean isSecure() {
                return seguro;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        });
    }

    /** Lo que puede usar una unidad de reparto. */
    private boolean permitidaVendedor(String ruta) {
        return ruta.startsWith("Auth/") || ruta.startsWith("Ruta/Mis") || ruta.startsWith("Ruta/Entrega/")
                || ruta.startsWith("Ruta/Reporte/") || ruta.equals("Productos/ObtenerProductos")
                || ruta.equals("Sucursales/ObtenerSucursales");
    }

    /** Rutas que no requieren sesión: login y catálogo público de la portada. */
    private boolean esPublica(String metodo, String ruta) {
        if (ruta.startsWith("Auth/Login") || ruta.startsWith("myresource")) {
            return true;
        }
        return "GET".equals(metodo)
                && (ruta.equals("Productos/ObtenerProductos") || ruta.equals("Sucursales/ObtenerSucursales"));
    }
}
