package BACKEND.controller;

import BACKEND.manager.ReporteManager;
import BACKEND.utils.Respuesta;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import BACKEND.utils.Acceso;

/**
 * GET /webapi/Reportes/ObtenerAlertas
 * GET /webapi/Reportes/ResumenProduccion?fecha=2026-10-02   (fecha opcional)
 */
@Path("Reportes")
@Produces(MediaType.APPLICATION_JSON)
public class ReporteController {

    private final ReporteManager manager = new ReporteManager();

    @GET
    @Path("/ObtenerAlertas")
    public Response ObtenerAlertas(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerAlertas());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/ResumenProduccion")
    public Response ResumenProduccion(@Context SecurityContext sc, @QueryParam("fecha") String fecha) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerResumenProduccion(fecha));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
