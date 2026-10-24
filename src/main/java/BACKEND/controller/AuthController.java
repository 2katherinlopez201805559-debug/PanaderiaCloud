package BACKEND.controller;

import BACKEND.manager.AuthManager;
import BACKEND.model.Login;
import BACKEND.utils.Acceso;
import BACKEND.utils.Respuesta;
import BACKEND.utils.Seguridad;
import BACKEND.utils.UsuarioSesion;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

/**
 * POST /webapi/Auth/Login   { "USUARIO": "kgalvez", "CLAVE": "Panaderia2026" }  (público)
 * GET  /webapi/Auth/Sesion  datos del usuario del token
 */
@Path("Auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    private final AuthManager manager = new AuthManager();

    @POST
    @Path("/Login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response Login(Login item) {
        if (item == null || item.USUARIO == null || item.USUARIO.trim().isEmpty()) {
            return Respuesta.validacion("Ingrese su usuario.");
        }
        if (item.CLAVE == null || item.CLAVE.isEmpty()) {
            return Respuesta.validacion("Ingrese su contraseña.");
        }
        if (item.USUARIO.length() > 30 || item.CLAVE.length() > 100) {
            return Respuesta.validacion("Usuario o contraseña incorrectos.");
        }
        try {
            UsuarioSesion u = manager.Login(item.USUARIO, item.CLAVE);
            Map<String, Object> salida = datosSesion(u);
            salida.put("TOKEN", Seguridad.crearToken(u));
            return Respuesta.ok(salida);
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/Sesion")
    public Response Sesion(@Context SecurityContext sc) {
        try {
            return Respuesta.ok(datosSesion(Acceso.usuario(sc)));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    private Map<String, Object> datosSesion(UsuarioSesion u) throws Exception {
        Map<String, Object> salida = new LinkedHashMap<String, Object>();
        salida.put("ID_USUARIO", u.getIdUsuario());
        salida.put("USUARIO", u.getName());
        salida.put("NOMBRE", u.getNombre());
        salida.put("ROL", u.getRol());
        salida.put("ID_SUCURSAL", u.getIdSucursal());
        salida.put("SUCURSAL", manager.NombreSucursal(u.getIdSucursal()));
        return salida;
    }
}
