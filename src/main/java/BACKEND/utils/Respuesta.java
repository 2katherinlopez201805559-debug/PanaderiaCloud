package BACKEND.utils;

import javax.ws.rs.core.Response;

/**
 * Respuestas HTTP uniformes para todos los controladores.
 *   200 -> datos o jsonResult OK
 *   400 -> regla de negocio (ReglaNegocio) o dato inválido
 *   500 -> error técnico (sin conexión a Firebase, credenciales, etc.)
 */
public final class Respuesta {

    private Respuesta() {
    }

    public static Response ok(Object datos) {
        return Response.ok(datos).build();
    }

    public static Response ok(int id, String mensaje) {
        return Response.ok(new jsonResult(id, "OK", mensaje)).build();
    }

    public static Response validacion(String mensaje) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new jsonResult(-1, "ERROR", mensaje))
                .build();
    }

    public static Response error(Exception e) {
        // Permisos (401/403) lanzados por Acceso: se devuelven tal cual
        if (e instanceof javax.ws.rs.WebApplicationException) {
            return ((javax.ws.rs.WebApplicationException) e).getResponse();
        }
        // Las transacciones de Firestore envuelven el error (ExecutionException):
        // se busca la regla de negocio dentro de la cadena de causas.
        Throwable actual = e;
        while (actual != null) {
            if (actual instanceof IllegalArgumentException) {
                return validacion(actual.getMessage());
            }
            actual = actual.getCause();
        }
        e.printStackTrace();
        String detalle = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new jsonResult(-1, "ERROR", "Error técnico: " + detalle))
                .build();
    }
}
