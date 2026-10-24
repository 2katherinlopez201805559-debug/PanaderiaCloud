package BACKEND.model;

/**
 * Devolución de pan a la fábrica.
 * { "ID_PEDIDO": 5, "ID_PRODUCTO": 4, "CANTIDAD": 10,
 *   "MOTIVO": "NO_SOLICITADO", "OBSERVACION": "Llegó Alemán y no lo pedimos" }
 * MOTIVO: NO_SOLICITADO | EXCEDENTE | DANADO
 */
public class Devolucion {
    public Integer ID_PEDIDO;
    public Integer ID_PRODUCTO;
    public Double CANTIDAD;
    public String MOTIVO;
    public String OBSERVACION;
}
