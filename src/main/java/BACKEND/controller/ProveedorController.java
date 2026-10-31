package BACKEND.controller;

import BACKEND.manager.ProveedorManager;
import BACKEND.model.PrecioInsumo;
import BACKEND.model.Proveedor;
import BACKEND.utils.Acceso;
import BACKEND.utils.Respuesta;
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
 * Todo solo gerente:
 * GET  /webapi/Proveedores/ObtenerProveedores
 * GET  /webapi/Proveedores/CompararPrecios
 * POST /webapi/Proveedores/CrearProveedor            { "NOMBRE": "...", "TELEFONO": "...", "CORREO": "...", "NIT": "..." }
 * POST /webapi/Proveedores/{id}/ActualizarProveedor  { ... , "ESTADO": "ACTIVO" }
 * POST /webapi/Proveedores/{id}/GuardarPrecio        { "ID_INSUMO": 1, "PRECIO": 7.25 }
 */
@Path("Proveedores")
@Produces(MediaType.APPLICATION_JSON)
public class ProveedorController {

    private final ProveedorManager manager = new ProveedorManager();

    @GET
    @Path("/ObtenerProveedores")
    public Response ObtenerProveedores(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerProveedores());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/CompararPrecios")
    public Response CompararPrecios(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.CompararPrecios());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/CrearProveedor")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response CrearProveedor(@Context SecurityContext sc, Proveedor p) {
        if (p == null) {
            return Respuesta.validacion("No se recibieron datos.");
        }
        try {
            Acceso.requerirGerente(sc);
            int id = manager.GuardarProveedor(null, p);
            return Respuesta.ok(id, "Proveedor creado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/ActualizarProveedor")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ActualizarProveedor(@Context SecurityContext sc, @PathParam("id") int id, Proveedor p) {
        if (p == null) {
            return Respuesta.validacion("No se recibieron datos.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.GuardarProveedor(id, p);
            return Respuesta.ok(id, "Proveedor actualizado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/GuardarPrecio")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response GuardarPrecio(@Context SecurityContext sc, @PathParam("id") int id, PrecioInsumo p) {
        if (p == null) {
            return Respuesta.validacion("No se recibieron datos.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.GuardarPrecio(id, p);
            return Respuesta.ok(id, p.PRECIO == null ? "Precio quitado." : "Precio guardado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
