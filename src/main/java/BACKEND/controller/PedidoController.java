package BACKEND.controller;

import BACKEND.manager.PedidoManager;
import BACKEND.model.Pedido;
import BACKEND.model.PedidoDetalle;
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
 * GET  /webapi/Pedidos/ObtenerPedidos?idSucursal=1&estado=PENDIENTE   (sucursal: solo los suyos)
 * GET  /webapi/Pedidos/{id}/ObtenerDetalle                              (sucursal: solo los suyos)
 * POST /webapi/Pedidos/RegistrarPedido                                  (sucursal: a su nombre)
 *      { "ID_SUCURSAL": 1, "FECHA_ENTREGA": "2026-10-02",
 *        "DETALLE": [ {"ID_PRODUCTO": 3, "CANTIDAD": 20}, {"ID_PRODUCTO": 2, "CANTIDAD": 10} ] }
 * POST /webapi/Pedidos/{id}/Despachar      (solo gerente)
 * POST /webapi/Pedidos/{id}/Entregar       (gerente o la sucursal dueña: confirma que recibió)
 * POST /webapi/Pedidos/{id}/Cancelar       (gerente o la sucursal dueña)
 */
@Path("Pedidos")
@Produces(MediaType.APPLICATION_JSON)
public class PedidoController {

    private final PedidoManager manager = new PedidoManager();

    @GET
    @Path("/ObtenerPedidos")
    public Response ObtenerPedidos(@Context SecurityContext sc,
                                   @QueryParam("idSucursal") Integer idSucursal,
                                   @QueryParam("estado") String estado) {
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            Integer filtroSucursal = u.esGerente() ? idSucursal : u.getIdSucursal();
            String filtroEstado = (estado == null || estado.trim().isEmpty()) ? null : estado.trim().toUpperCase();
            return Respuesta.ok(manager.ObtenerPedidos(filtroSucursal, filtroEstado));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/{id}/ObtenerDetalle")
    public Response ObtenerDetalle(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            validarDueno(sc, id);
            return Respuesta.ok(manager.ObtenerDetalle(id));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/RegistrarPedido")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response RegistrarPedido(@Context SecurityContext sc, Pedido item) {
        try {
            UsuarioSesion u = Acceso.usuario(sc);
            if (item != null && !u.esGerente()) {
                item.ID_SUCURSAL = u.getIdSucursal();   // una sucursal solo pide para sí misma
            }
            String error = validarPedido(item);
            if (error != null) {
                return Respuesta.validacion(error);
            }
            int id = manager.RegistrarPedido(item);
            return Respuesta.ok(id, "Pedido #" + id + " registrado correctamente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/Despachar")
    public Response Despachar(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            Acceso.requerirGerente(sc);
            manager.Despachar(id);
            return Respuesta.ok(id, "Pedido #" + id + " despachado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/Entregar")
    public Response Entregar(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            validarDueno(sc, id);
            manager.Entregar(id);
            return Respuesta.ok(id, "Pedido #" + id + " entregado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/Cancelar")
    public Response Cancelar(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            validarDueno(sc, id);
            manager.Cancelar(id);
            return Respuesta.ok(id, "Pedido #" + id + " cancelado.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    /** El gerente puede todo; una sucursal solo sus propios pedidos. */
    private void validarDueno(SecurityContext sc, int idPedido) throws Exception {
        UsuarioSesion u = Acceso.usuario(sc);
        if (!u.esGerente()) {
            Acceso.requerirSucursal(u, manager.SucursalDePedido(idPedido));
        }
    }

    /** Validaciones antes de ir a la base de datos. Devuelve null si todo está bien. */
    private String validarPedido(Pedido item) {
        if (item == null) {
            return "No se recibió el pedido.";
        }
        if (item.ID_SUCURSAL == null) {
            return "Seleccione la sucursal.";
        }
        if (item.DETALLE == null || item.DETALLE.isEmpty()) {
            return "Agregue al menos un pan al pedido.";
        }
        for (PedidoDetalle d : item.DETALLE) {
            if (d == null || d.ID_PRODUCTO == null) {
                return "Hay una línea del pedido sin pan seleccionado.";
            }
            if (d.CANTIDAD == null || d.CANTIDAD <= 0) {
                return "Todas las cantidades deben ser mayores que 0.";
            }
        }
        return null;
    }
}
