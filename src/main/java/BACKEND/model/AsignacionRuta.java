package BACKEND.model;

/**
 * Asignar un pedido a una unidad, o resolver un problema.
 * { "ID_UNIDAD": 2, "NOTA": "La unidad 2 lo lleva" }   (ID_UNIDAD null al resolver = sigue la misma unidad)
 */
public class AsignacionRuta {
    public Integer ID_UNIDAD;
    public String NOTA;
}
