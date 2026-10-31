package BACKEND.controller;

import BACKEND.manager.InsumoManager;
import BACKEND.model.CompraInsumo;
import BACKEND.utils.Respuesta;
import BACKEND.utils.UsuarioSesion;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import BACKEND.utils.Acceso;

/**
 * GET  /webapi/Insumos/ObtenerInsumos
 * POST /webapi/Insumos/RegistrarCompra      { "ID_INSUMO": 1, "CANTIDAD": 25,
 *                                             "ID_PROVEEDOR": 2, "PRECIO_UNITARIO": 7.25, "NO_FACTURA": "A-123" }  (los 3 últimos opcionales)
 * GET  /webapi/Insumos/ObtenerCompras?desde=2026-10-01&hasta=2026-10-31&idProveedor=2   (todo opcional)
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
            UsuarioSesion u = Acceso.requerirGerente(sc);
            int id = manager.RegistrarCompra(item, u.getIdUsuario());
            return Respuesta.ok(id, "Compra #" + id + " registrada: el insumo ya subió en inventario.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/ObtenerCompras")
    public Response ObtenerCompras(@Context SecurityContext sc, @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta, @QueryParam("idProveedor") String idProveedor) {
        try {
            Acceso.requerirGerente(sc);
            Integer prov = idProveedor == null || idProveedor.trim().isEmpty() ? null : Integer.valueOf(idProveedor.trim());
            return Respuesta.ok(manager.ObtenerCompras(desde, hasta, prov));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
