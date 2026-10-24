package BACKEND.utils;

import java.security.Principal;

/**
 * Usuario que inició sesión (se obtiene del token en cada petición).
 */
public class UsuarioSesion implements Principal {

    public static final String GERENTE = "GERENTE";
    public static final String SUCURSAL = "SUCURSAL";

    private final int idUsuario;
    private final String usuario;
    private final String nombre;
    private final String rol;
    private final Integer idSucursal;

    public UsuarioSesion(int idUsuario, String usuario, String nombre, String rol, Integer idSucursal) {
        this.idUsuario = idUsuario;
        this.usuario = usuario;
        this.nombre = nombre;
        this.rol = rol;
        this.idSucursal = idSucursal;
    }

    @Override
    public String getName() {
        return usuario;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    /** null para el gerente. */
    public Integer getIdSucursal() {
        return idSucursal;
    }

    public boolean esGerente() {
        return GERENTE.equals(rol);
    }
}
