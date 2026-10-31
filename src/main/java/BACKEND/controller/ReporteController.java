package BACKEND.controller;

import BACKEND.manager.Notificador;
import BACKEND.manager.ReporteManager;
import BACKEND.utils.Ajustes;
import BACKEND.utils.Correo;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.POST;
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
 * GET /webapi/Reportes/Estadisticas?desde=2026-10-01&hasta=2026-10-31  (panes más vendidos, sucursal top, ventas y compras)
 * GET /webapi/Reportes/CapacidadProduccion   (cuántos panes alcanzan con los insumos)
 * GET /webapi/Reportes/PanesPorVencer
 * GET /webapi/Reportes/Avisos                (lista de la campanita 🔔)
 * GET /webapi/Reportes/EstadoAvisos          (¿Telegram/correo configurados?)
 * POST /webapi/Reportes/ConectarTelegram     (guarda el chat de quien le escribió /start al bot)
 * POST /webapi/Reportes/EnviarAviso          (manda el aviso ahora, aunque ya se haya avisado hoy)
 * Todo solo gerente.
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

    @GET
    @Path("/Estadisticas")
    public Response Estadisticas(@Context SecurityContext sc, @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.Estadisticas(desde, hasta));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/CapacidadProduccion")
    public Response CapacidadProduccion(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.CapacidadProduccion());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/PanesPorVencer")
    public Response PanesPorVencer(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.PanesPorVencer());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/EstadoAvisos")
    public Response EstadoAvisos(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            Map<String, Object> r = new LinkedHashMap<String, Object>();
            r.put("CORREO_CONFIGURADO", Correo.configurado());
            r.put("PARA", Ajustes.correoPropietario());
            r.put("TELEGRAM", Notificador.EstadoTelegram());
            r.put("DIAS_VIDA_PAN", Ajustes.diasVidaPan());
            return Respuesta.ok(r);
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/EnviarAviso")
    public Response EnviarAviso(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(1, Notificador.revisarAhora(true));
        } catch (IllegalStateException e) {
            return Respuesta.validacion(e.getMessage());   // correo sin configurar o Resend lo rechazó
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/Avisos")
    public Response Avisos(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(Notificador.Avisos());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/ConectarTelegram")
    public Response ConectarTelegram(@Context SecurityContext sc) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(1, Notificador.ConectarTelegram());
        } catch (IllegalStateException e) {
            return Respuesta.validacion(e.getMessage());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }
}
