package BACKEND.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;

/**
 * Creación y validación de tokens JWT (firma HMAC-SHA256).
 * La clave secreta se lee de la variable de entorno SECRETO (Ajustes.java).
 */
public final class Seguridad {

    private static final String EMISOR = "PanaderiaSanCarlos";
    private static final long DURACION_MS = 8L * 60 * 60 * 1000;   // 8 horas (un turno)
    private static final String SECRETO = leerSecreto();

    private Seguridad() {
    }

    private static String leerSecreto() {
        return Ajustes.secreto();   // variable de entorno SECRETO (ver Ajustes.java)
    }

    private static Algorithm algoritmo() {
        try {
            return Algorithm.HMAC256(SECRETO);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el algoritmo de firma.", e);
        }
    }

    public static String crearToken(UsuarioSesion u) {
        Date ahora = new Date();
        return JWT.create()
                .withIssuer(EMISOR)
                .withSubject(u.getName())
                .withClaim("id", u.getIdUsuario())
                .withClaim("nombre", u.getNombre())
                .withClaim("rol", u.getRol())
                .withClaim("suc", u.getIdSucursal() == null ? -1 : u.getIdSucursal())
                .withIssuedAt(ahora)
                .withExpiresAt(new Date(ahora.getTime() + DURACION_MS))
                .sign(algoritmo());
    }

    /** Devuelve el usuario del token, o null si es inválido o expiró. */
    public static UsuarioSesion validarToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            DecodedJWT jwt = JWT.require(algoritmo()).withIssuer(EMISOR).build().verify(token.trim());
            Integer suc = jwt.getClaim("suc").asInt();
            return new UsuarioSesion(
                    jwt.getClaim("id").asInt(),
                    jwt.getSubject(),
                    jwt.getClaim("nombre").asString(),
                    jwt.getClaim("rol").asString(),
                    (suc == null || suc < 0) ? null : suc);
        } catch (Exception e) {
            return null;
        }
    }
}
