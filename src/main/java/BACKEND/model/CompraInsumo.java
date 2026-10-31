package BACKEND.model;

/**
 * Compra de materia prima (factura del proveedor).
 * { "ID_INSUMO": 1, "CANTIDAD": 25, "ID_PROVEEDOR": 2, "PRECIO_UNITARIO": 7.25, "NO_FACTURA": "A-1234",
 *   "OBSERVACION": "..." }
 * Solo ID_INSUMO y CANTIDAD son obligatorios.
 */
public class CompraInsumo {
    public Integer ID_INSUMO;
    public Double CANTIDAD;
    public Integer ID_PROVEEDOR;
    public Double PRECIO_UNITARIO;
    public String NO_FACTURA;
    public String OBSERVACION;
}
