package BACKEND.controller;

import BACKEND.manager.RutaManager;
import BACKEND.model.AsignacionRuta;
import BACKEND.model.ReporteRuta;
import BACKEND.utils.Acceso;
import BACKEND.utils.Respuesta;
import BACKEND.utils.UsuarioSesion;
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
 * Vendedores en ruta (unidades de reparto).
 *
 * Unidad de reparto (rol VENDEDOR, app ruta.html):
 *   GET  /webapi/Ruta/MisPedidos                 pedidos asignados que siguen en ruta
 *   GET  /webapi/Ruta/MisReportes                sus problemas reportados
 *   POST /webapi/Ruta/Entrega/{idPedido}         marcar ENTREGADO
 *   POST /webapi/Ruta/Reporte/{idPedido}         reportar problema  { "TIPO": "VEHICULO", "DESCRIPCION": "..." }
 *
 * Gerente:
 *   GET  /webapi/Ruta/Unidades                   unidades y cuántos pedidos llevan
 *   GET  /webapi/Ruta/PedidosEnRuta              pedidos DESPACHADOS con su unidad
 *   GET  /webapi/Ruta/Incidentes?estado=ABIERTO  problemas reportados
 *   POST /webapi/Ruta/Asignar/{idPedido}         { "ID_UNIDAD": 2 }
 *   POST /webapi/Ruta/Incidentes/{id}/Resolver   { "ID_UNIDAD": 3, "NOTA": "..." }  (ID_UNIDAD = otra unidad lo despacha)
 */
@Path("Ruta")
@Produces(MediaType.APPLICATION_JSON)
public class RutaController {

    private final RutaManager manager = new RutaManager();

    /* ---------------- Unidad de reparto ---------------- */

    @GET
    @Path("/MisPedidos")
    public Response MisPedidos(@Context SecurityContext sc) {
        try {
            UsuarioSesion u = Acceso.requerirVendedor(sc);
            return Respuesta.ok(manager.MisPedidos(u.getIdUsuario()));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/MisReportes")
    public Response MisReportes(@Context SecurityContext sc) {
        try {
            UsuarioSesion u = Acceso.requerirVendedor(sc);
            return Respuesta.ok(manager.ObtenerIncidentes(null, u.getIdUsuario()));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/Entrega/{id}")
    public Response Entregar(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            UsuarioSesion u = Acceso.requerirVendedor(sc);
            manager.Entregar(id, u.getIdUsuario());
            return Respuesta.ok(id, "Pedido #" + id + " entregado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/Reporte/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Reportar(@Context SecurityContext sc, @PathParam("id") int id, ReporteRuta r) {
        if (r == null) {
            return Respuesta.validacion("No se recibió el reporte.");
        }
        try {
            UsuarioSesion u = Acceso.requerirVendedor(sc);
            int idInc = manager.ReportarProblema(id, u.getIdUsuario(), r.TIPO, r.DESCRIPCION);
            return Respuesta.ok(idInc, "Problema reportado. Gerencia ya fue avisada.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    /* ---------------- Gerente ---------------- */

    @GET
    @Path("/Unidades")
    public Response Unidades(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerUnidades());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/PedidosEnRuta")
    public Response PedidosEnRuta(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.PedidosEnRuta());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/Incidentes")
    public Response Incidentes(@Context SecurityContext sc, @QueryParam("estado") String estado) {
        try {
            Acceso.requerirGerente(sc);
            String e = estado == null || estado.trim().isEmpty() ? null : estado.trim().toUpperCase();
            return Respuesta.ok(manager.ObtenerIncidentes(e, null));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/Asignar/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Asignar(@Context SecurityContext sc, @PathParam("id") int id, AsignacionRuta a) {
        if (a == null || a.ID_UNIDAD == null) {
            return Respuesta.validacion("Seleccione la unidad.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.AsignarUnidad(id, a.ID_UNIDAD);
            return Respuesta.ok(id, "Pedido #" + id + " asignado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/Incidentes/{id}/Resolver")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Resolver(@Context SecurityContext sc, @PathParam("id") int id, AsignacionRuta a) {
        try {
            UsuarioSesion u = Acceso.requerirGerente(sc);
            String msj = manager.ResolverIncidente(id, a == null ? null : a.ID_UNIDAD, a == null ? null : a.NOTA,
                    u.getIdUsuario());
            return Respuesta.ok(id, msj);
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
