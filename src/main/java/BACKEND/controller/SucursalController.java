package BACKEND.controller;

import BACKEND.manager.SucursalManager;
import BACKEND.model.NuevaSucursal;
import BACKEND.utils.Acceso;
import BACKEND.utils.Respuesta;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

/**
 * GET  /webapi/Sucursales/ObtenerSucursales                 (pública: la usa la portada)
 * POST /webapi/Sucursales/CrearSucursal       (solo gerente) { "NOMBRE": "Mercado", "DIRECCION": "...", "TELEFONO": "...", "CLAVE": "..." }
 * POST /webapi/Sucursales/{id}/ActualizarSucursal (gerente)  { "DIRECCION": "...", "TELEFONO": "...", "ESTADO": "ACTIVO" }
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

    @POST
    @Path("/CrearSucursal")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response CrearSucursal(@Context SecurityContext sc, NuevaSucursal s) {
        if (s == null || s.NOMBRE == null || s.NOMBRE.trim().isEmpty()) {
            return Respuesta.validacion("Escriba el nombre de la sucursal.");
        }
        try {
            Acceso.requerirGerente(sc);
            Map<String, Object> r = manager.CrearSucursal(s);
            r.put("result", "OK");
            r.put("msj", "Sucursal creada. Usuario para entrar: " + r.get("USUARIO"));
            return Respuesta.ok(r);
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/ActualizarSucursal")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ActualizarSucursal(@Context SecurityContext sc, @PathParam("id") int id, NuevaSucursal s) {
        if (s == null) {
            return Respuesta.validacion("No se recibieron datos.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.ActualizarSucursal(id, s);
            return Respuesta.ok(id, "Sucursal actualizada.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
