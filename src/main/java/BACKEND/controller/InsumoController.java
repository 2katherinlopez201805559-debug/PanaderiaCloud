package BACKEND.controller;

import BACKEND.manager.InsumoManager;
import BACKEND.model.CompraInsumo;
import BACKEND.utils.Respuesta;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import BACKEND.utils.Acceso;

/**
 * GET  /webapi/Insumos/ObtenerInsumos
 * POST /webapi/Insumos/RegistrarCompra      { "ID_INSUMO": 1, "CANTIDAD": 25 }
 */
@Path("Insumos")
@Produces(MediaType.APPLICATION_JSON)
public class InsumoController {

    private final InsumoManager manager = new InsumoManager();

    @GET
    @Path("/ObtenerInsumos")
    public Response ObtenerInsumos(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerInsumos());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/RegistrarCompra")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response RegistrarCompra(@Context SecurityContext sc, CompraInsumo item) {
        if (item == null || item.ID_INSUMO == null) {
            return Respuesta.validacion("Seleccione el insumo comprado.");
        }
        if (item.CANTIDAD == null || item.CANTIDAD <= 0) {
            return Respuesta.validacion("La cantidad comprada debe ser mayor que 0.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.RegistrarCompra(item);
            return Respuesta.ok(item.ID_INSUMO, "Compra de insumo registrada correctamente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
