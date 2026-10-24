package BACKEND.controller;

import BACKEND.manager.PagoManager;
import BACKEND.model.Pago;
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
 * GET  /webapi/Pagos/ObtenerPagos?estado=PENDIENTE   (sucursal: solo los suyos)
 * GET  /webapi/Pagos/EstadoCuenta?idSucursal=2       (sucursal: solo la suya)
 * POST /webapi/Pagos/RegistrarPago
 *      { "ID_PEDIDO": 5, "METODO": "TRANSFERENCIA", "MONTO": 120.50,
 *        "BANCO": "Banrural", "REFERENCIA": "TRX123456", "OBSERVACION": "" }
 * POST /webapi/Pagos/{id}/Confirmar    (solo gerente)
 * POST /webapi/Pagos/{id}/Rechazar     (solo gerente)
 */
@Path("Pagos")
@Produces(MediaType.APPLICATION_JSON)
public class PagoController {

    private static final List<String> METODOS = Arrays.asList("EFECTIVO", "TRANSFERENCIA", "DEPOSITO", "TARJETA");
    private final PagoManager manager = new PagoManager();

    @GET
    @Path("/ObtenerPagos")
    public Response ObtenerPagos(@Context SecurityContext sc, @QueryParam("estado") String estado) {
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            String filtro = (estado == null || estado.trim().isEmpty()) ? null : estado.trim().toUpperCase();
            return Respuesta.ok(manager.ObtenerPagos(u.esGerente() ? null : u.getIdSucursal(), filtro));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/EstadoCuenta")
    public Response EstadoCuenta(@Context SecurityContext sc, @QueryParam("idSucursal") Integer idSucursal) {
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            return Respuesta.ok(manager.EstadoCuenta(u.esGerente() ? idSucursal : u.getIdSucursal()));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/RegistrarPago")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Registrar(@Context SecurityContext sc, Pago p) {
        String error = validar(p);
        if (error != null) {
            return Respuesta.validacion(error);
        }
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            int id = manager.Registrar(p, u.getIdUsuario(), u.esGerente() ? null : u.getIdSucursal());
            String msj = u.esGerente()
                    ? "Pago #" + id + " registrado y confirmado."
                    : "Pago #" + id + " registrado. Queda en revisión hasta que gerencia lo confirme.";
            return Respuesta.ok(id, msj);
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/Confirmar")
    public Response Confirmar(@Context SecurityContext sc, @PathParam("id") int id) {
        return resolver(sc, id, true);
    }

    @POST
    @Path("/{id}/Rechazar")
    public Response Rechazar(@Context SecurityContext sc, @PathParam("id") int id) {
        return resolver(sc, id, false);
    }

    private Response resolver(SecurityContext sc, int id, boolean confirmar) {
        try {
            UsuarioSesion u = Acceso.requerirGerente(sc);
            manager.Resolver(id, confirmar, u.getIdUsuario());
            return Respuesta.ok(id, "Pago #" + id + (confirmar ? " confirmado." : " rechazado."));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    private String validar(Pago p) {
        if (p == null) {
            return "No se recibió el pago.";
        }
        if (p.ID_PEDIDO == null) {
            return "Seleccione el pedido que va a pagar.";
        }
        if (p.METODO == null || !METODOS.contains(p.METODO.trim().toUpperCase())) {
            return "Seleccione la forma de pago.";
        }
        if (p.MONTO == null || p.MONTO <= 0) {
            return "El monto debe ser mayor que 0.";
        }
        String metodo = p.METODO.trim().toUpperCase();
        if ((metodo.equals("TRANSFERENCIA") || metodo.equals("DEPOSITO"))
                && (p.BANCO == null || p.BANCO.trim().isEmpty())) {
            return "Seleccione el banco.";
        }
        if (!metodo.equals("EFECTIVO")) {
            if (p.REFERENCIA == null || p.REFERENCIA.trim().length() < 4) {
                return "Escriba el número de referencia (mínimo 4 caracteres).";
            }
            if (!p.REFERENCIA.trim().matches("[A-Za-z0-9-]{4,40}")) {
                return "La referencia solo admite letras, números y guion (4 a 40).";
            }
        }
        if (p.OBSERVACION != null && p.OBSERVACION.length() > 250) {
            return "La observación admite máximo 250 caracteres.";
        }
        return null;
    }
}
