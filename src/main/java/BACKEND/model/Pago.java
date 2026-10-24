package BACKEND.model;

/**
 * Pago de un pedido.
 * { "ID_PEDIDO": 5, "METODO": "TRANSFERENCIA", "MONTO": 120.50,
 *   "BANCO": "Banrural", "REFERENCIA": "TRX123456", "OBSERVACION": "..." }
 * METODO: EFECTIVO | TRANSFERENCIA | DEPOSITO | TARJETA
 * REFERENCIA: No. de transferencia, boleta de depósito o autorización del voucher.
 * Nunca se envían ni guardan números de tarjeta.
 */
public class Pago {
    public Integer ID_PEDIDO;
    public String METODO;
    public Double MONTO;
    public String BANCO;
    public String REFERENCIA;
    public String OBSERVACION;
}
