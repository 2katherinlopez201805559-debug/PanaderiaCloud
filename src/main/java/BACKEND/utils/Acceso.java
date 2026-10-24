package BACKEND.utils;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

/**
 * Reglas de permisos por rol, reutilizadas por todos los controladores.
 */
public final class Acceso {

    private Acceso() {
    }

    public static UsuarioSesion usuario(SecurityContext sc) {
        if (sc == null || !(sc.getUserPrincipal() instanceof UsuarioSesion)) {
            throw new WebApplicationException(respuesta(Response.Status.UNAUTHORIZED,
                    "Sesión no válida o expirada. Inicie sesión nuevamente."));
        }
        return (UsuarioSesion) sc.getUserPrincipal();
    }

    /** Solo el gerente puede continuar. */
    public static UsuarioSesion requerirGerente(SecurityContext sc) {
        UsuarioSesion u = usuario(sc);
        if (!u.esGerente()) {
            throw new WebApplicationException(respuesta(Response.Status.FORBIDDEN,
                    "Esta opción es solo para el gerente."));
        }
        return u;
    }

    /** El gerente puede todo; una sucursal solo lo de su propia sucursal. */
    public static void requerirSucursal(UsuarioSesion u, Integer idSucursalDelRecurso) {
        if (!u.esGerente() && (idSucursalDelRecurso == null || !idSucursalDelRecurso.equals(u.getIdSucursal()))) {
            throw new WebApplicationException(respuesta(Response.Status.FORBIDDEN,
                    "No tiene permiso sobre información de otra sucursal."));
        }
    }

    private static Response respuesta(Response.Status estado, String mensaje) {
        return Response.status(estado)
                .type(MediaType.APPLICATION_JSON)
                .entity(new jsonResult(-1, "ERROR", mensaje))
                .build();
    }
}
