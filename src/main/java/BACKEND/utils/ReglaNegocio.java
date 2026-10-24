package BACKEND.utils;

/**
 * Error de una regla del negocio (stock insuficiente, pedido ya despachado,
 * monto mayor al saldo...). Reemplaza a RAISE_APPLICATION_ERROR de Oracle:
 * el backend lo devuelve como 400 con el mensaje tal cual.
 */
public class ReglaNegocio extends IllegalArgumentException {

    private final int codigo;

    public ReglaNegocio(int codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    /** Mismo número que tenía el error en la versión Oracle (ej. 20001). */
    public int getCodigo() {
        return codigo;
    }
}
