package BACKEND.controller;

import BACKEND.manager.DevolucionManager;
import BACKEND.model.Devolucion;
import BACKEND.utils.Acceso;
import BACKEND.utils.Respuesta;
import BACKEND.utils.UsuarioSesion;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

/**
 * GET  /webapi/Devoluciones/ObtenerDevoluciones?estado=PENDIENTE
 * POST /webapi/Devoluciones/RegistrarDevolucion
 *      { "ID_PEDIDO": 5, "ID_PRODUCTO": 4, "CANTIDAD": 10, "MOTIVO": "NO_SOLICITADO", "OBSERVACION": "..." }
 * POST /webapi/Devoluciones/{id}/Aprobar     (solo gerente)
 * POST /webapi/Devoluciones/{id}/Rechazar    (solo gerente)
 */
@Path("Devoluciones")
@Produces(MediaType.APPLICATION_JSON)
public class DevolucionController {

    private static final List<String> MOTIVOS = Arrays.asList("NO_SOLICITADO", "EXCEDENTE", "DANADO");
    private final DevolucionManager manager = new DevolucionManager();

    @GET
    @Path("/ObtenerDevoluciones")
    public Response ObtenerDevoluciones(@Context SecurityContext sc, @QueryParam("estado") String estado) {
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            String filtro = (estado == null || estado.trim().isEmpty()) ? null : estado.trim().toUpperCase();
            // Una sucursal solo ve las suyas
            return Respuesta.ok(manager.ObtenerDevoluciones(u.esGerente() ? null : u.getIdSucursal(), filtro));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/RegistrarDevolucion")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Registrar(@Context SecurityContext sc, Devolucion d) {
        String error = validar(d);
        if (error != null) {
            return Respuesta.validacion(error);
        }
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            int id = manager.Registrar(d, u.getIdUsuario(), u.esGerente() ? null : u.getIdSucursal());
            return Respuesta.ok(id, "Devolución #" + id + " registrada. Queda pendiente de revisión del gerente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/Aprobar")
    public Response Aprobar(@Context SecurityContext sc, @PathParam("id") int id) {
        return resolver(sc, id, true);
    }

    @POST
    @Path("/{id}/Rechazar")
    public Response Rechazar(@Context SecurityContext sc, @PathParam("id") int id) {
        return resolver(sc, id, false);
    }

    private Response resolver(SecurityContext sc, int id, boolean aprobar) {
        try {
            UsuarioSesion u = Acceso.requerirGerente(sc);
            manager.Resolver(id, aprobar, u.getIdUsuario());
            return Respuesta.ok(id, "Devolución #" + id + (aprobar ? " aprobada." : " rechazada."));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    private String validar(Devolucion d) {
        if (d == null) {
            return "No se recibió la devolución.";
        }
        if (d.ID_PEDIDO == null) {
            return "Seleccione el pedido.";
        }
        if (d.ID_PRODUCTO == null) {
            return "Seleccione el pan que devuelve.";
        }
        if (d.CANTIDAD == null || d.CANTIDAD <= 0) {
            return "La cantidad debe ser mayor que 0.";
        }
        if (d.MOTIVO == null || !MOTIVOS.contains(d.MOTIVO)) {
            return "Seleccione un motivo válido.";
        }
        if (d.OBSERVACION != null && d.OBSERVACION.length() > 250) {
            return "La observación admite máximo 250 caracteres.";
        }
        return null;
    }
}
