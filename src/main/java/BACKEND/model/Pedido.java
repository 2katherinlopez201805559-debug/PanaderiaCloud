package BACKEND.model;

import java.util.List;

/**
 * Pedido que envía una sucursal desde el sitio web.
 * Ejemplo JSON:
 * {
 *   "ID_SUCURSAL": 1,
 *   "FECHA_ENTREGA": "2026-10-02",
 *   "DETALLE": [ { "ID_PRODUCTO": 3, "CANTIDAD": 20 },
 *                { "ID_PRODUCTO": 2, "CANTIDAD": 10 } ]
 * }
 */
public class Pedido {
    public Integer ID_PEDIDO;
    public Integer ID_SUCURSAL;
    public String FECHA_ENTREGA;          // formato yyyy-MM-dd (opcional)
    public List<PedidoDetalle> DETALLE;
}
