package BACKEND.controller;

import BACKEND.manager.SucursalManager;
import BACKEND.utils.Respuesta;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * GET /webapi/Sucursales/ObtenerSucursales
 */
@Path("Sucursales")
@Produces(MediaType.APPLICATION_JSON)
public class SucursalController {

    private final SucursalManager manager = new SucursalManager();

    @GET
    @Path("/ObtenerSucursales")
    public Response ObtenerSucursales() {
        try {
            return Respuesta.ok(manager.ObtenerSucursales());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
