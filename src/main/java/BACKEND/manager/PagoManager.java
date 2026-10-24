package BACKEND.manager;

import BACKEND.model.Pago;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pagos de pedidos (antes PKG_PAGO).
 *   EFECTIVO | TRANSFERENCIA (banco + No.) | DEPOSITO (banco + boleta) | TARJETA (No. de autorización)
 * Saldo de un pedido = TOTAL - devoluciones APROBADAS - pagos CONFIRMADOS.
 * Nunca se guardan números de tarjeta.
 */
public class PagoManager extends FirestoreBase {

    /** idSucursal null = todas (gerente). */
    public List<Map<String, Object>> ObtenerPagos(Integer idSucursal, String estado) throws Exception {
        Query q = col(PAGOS);
        if (idSucursal != null) {
            q = q.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : filas(q.get().get())) {
            if (estado == null || estado.equals(p.get("ESTADO"))) {
                salida.add(p);
            }
        }
        return ordenar(salida, "ID_PAGO", true);
    }

    /** Por pedido (no cancelado): total, crédito por devoluciones, pagado, en revisión, saldo y estado del pago. */
    public List<Map<String, Object>> EstadoCuenta(Integer idSucursal) throws Exception {
        Query qp = col(PEDIDOS), qd = col(DEVOLUCIONES), qg = col(PAGOS);
        if (idSucursal != null) {
            qp = qp.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
            qd = qd.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
            qg = qg.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
        }
        Map<Long, Double> credito = new HashMap<Long, Double>();
        for (Map<String, Object> d : filas(qd.get().get())) {
            if ("APROBADA".equals(d.get("ESTADO"))) {
                sumar(credito, num(d.get("ID_PEDIDO")).longValue(), d(d.get("MONTO")));
            }
        }
        Map<Long, Double> pagado = new HashMap<Long, Double>(), revision = new HashMap<Long, Double>();
        for (Map<String, Object> g : filas(qg.get().get())) {
            long idPedido = num(g.get("ID_PEDIDO")).longValue();
            if ("CONFIRMADO".equals(g.get("ESTADO"))) {
                sumar(pagado, idPedido, d(g.get("MONTO")));
            } else if ("PENDIENTE".equals(g.get("ESTADO"))) {
                sumar(revision, idPedido, d(g.get("MONTO")));
            }
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : filas(qp.get().get())) {
            if ("CANCELADO".equals(p.get("ESTADO"))) {
                continue;
            }
            long id = num(p.get("ID_PEDIDO")).longValue();
            double total = d(p.get("TOTAL"));
            double cred = valor(credito, id), pag = valor(pagado, id), rev = valor(revision, id);
            double saldo = redondear(total - cred - pag, 2);
            double porPagar = redondear(saldo - rev, 2);
            String estadoPago = saldo <= 0 ? "PAGADO" : porPagar <= 0 ? "EN_REVISION" : (pag + rev > 0) ? "PARCIAL" : "SIN_PAGO";
            Map<String, Object> f = new LinkedHashMap<String, Object>();
            f.put("ID_PEDIDO", id);
            f.put("ID_SUCURSAL", p.get("ID_SUCURSAL"));
            f.put("SUCURSAL", p.get("SUCURSAL"));
            f.put("ESTADO", p.get("ESTADO"));
            f.put("FECHA_ENTREGA", p.get("FECHA_ENTREGA"));
            f.put("TOTAL", total);
            f.put("CREDITO", cred);
            f.put("PAGADO", pag);
            f.put("EN_REVISION", rev);
            f.put("SALDO", saldo);
            f.put("POR_PAGAR", porPagar);
            f.put("ESTADO_PAGO", estadoPago);
            salida.add(f);
        }
        return ordenar(salida, "ID_PEDIDO", true);
    }

    /**
     * Registra el pago en una transacción. idSucursal null = lo registra gerencia (queda CONFIRMADO);
     * con sucursal queda PENDIENTE hasta que gerencia lo revise. Devuelve el ID.
     */
    public int Registrar(final Pago pg, final int idUsuario, final Integer idSucursal) throws Exception {
        final String metodo = pg.METODO == null ? "" : pg.METODO.trim().toUpperCase();
        if (!(metodo.equals("EFECTIVO") || metodo.equals("TRANSFERENCIA") || metodo.equals("DEPOSITO") || metodo.equals("TARJETA"))) {
            throw new ReglaNegocio(20020, "Forma de pago inválida. Use EFECTIVO, TRANSFERENCIA, DEPOSITO o TARJETA.");
        }
        final double monto = pg.MONTO == null ? 0 : pg.MONTO;
        if (monto <= 0) {
            throw new ReglaNegocio(20021, "El monto debe ser mayor que 0.");
        }
        if (redondear(monto, 2) != monto) {
            throw new ReglaNegocio(20021, "El monto admite máximo 2 decimales.");
        }
        final boolean efectivo = metodo.equals("EFECTIVO");
        final String banco = efectivo || pg.BANCO == null || pg.BANCO.trim().isEmpty() ? null : pg.BANCO.trim();
        final String referencia = efectivo || pg.REFERENCIA == null ? null : pg.REFERENCIA.trim().toUpperCase();
        if ((metodo.equals("TRANSFERENCIA") || metodo.equals("DEPOSITO")) && banco == null) {
            throw new ReglaNegocio(20022, "Seleccione el banco.");
        }
        if (!efectivo && (referencia == null || referencia.length() < 4)) {
            throw new ReglaNegocio(20022, "Escriba el número de referencia (mínimo 4 caracteres).");
        }

        long id = transaccion(tx -> {
            // LECTURAS
            if (!efectivo) {
                // La misma transferencia / boleta / autorización no se puede usar dos veces
                for (QueryDocumentSnapshot o : tx.get(col(PAGOS).whereEqualTo("REFERENCIA", referencia)).get().getDocuments()) {
                    boolean mismoBanco = banco == null ? o.getString("BANCO") == null : banco.equals(o.getString("BANCO"));
                    if (metodo.equals(o.getString("METODO")) && mismoBanco && !"RECHAZADO".equals(o.getString("ESTADO"))) {
                        throw new ReglaNegocio(20022, "La referencia " + referencia + " ya fue registrada en otro pago.");
                    }
                }
            }
            DocumentSnapshot p = tx.get(doc(PEDIDOS, pg.ID_PEDIDO)).get();
            if (!p.exists()) {
                throw new ReglaNegocio(20004, "El pedido " + pg.ID_PEDIDO + " no existe.");
            }
            long sucPedido = num(p.get("ID_SUCURSAL")).longValue();
            if (idSucursal != null && sucPedido != idSucursal) {
                throw new ReglaNegocio(20012, "El pedido " + pg.ID_PEDIDO + " no pertenece a su sucursal.");
            }
            if ("CANCELADO".equals(p.getString("ESTADO"))) {
                throw new ReglaNegocio(20023, "El pedido " + pg.ID_PEDIDO + " está cancelado; no se puede pagar.");
            }
            double usado = 0;
            for (QueryDocumentSnapshot dv : tx.get(col(DEVOLUCIONES).whereEqualTo("ID_PEDIDO", (long) pg.ID_PEDIDO)).get().getDocuments()) {
                if ("APROBADA".equals(dv.getString("ESTADO"))) {
                    usado += d(dv.get("MONTO"));
                }
            }
            for (QueryDocumentSnapshot o : tx.get(col(PAGOS).whereEqualTo("ID_PEDIDO", (long) pg.ID_PEDIDO)).get().getDocuments()) {
                String e = o.getString("ESTADO");
                if ("PENDIENTE".equals(e) || "CONFIRMADO".equals(e)) {
                    usado += d(o.get("MONTO"));
                }
            }
            double disponible = redondear(d(p.get("TOTAL")) - usado, 2);
            if (disponible <= 0) {
                throw new ReglaNegocio(20023, "El pedido " + pg.ID_PEDIDO
                        + " ya está pagado (o tiene pagos en revisión por el total).");
            }
            if (monto > disponible + 0.001) {
                throw new ReglaNegocio(20021, "El monto (" + dinero(monto) + ") es mayor al saldo pendiente ("
                        + dinero(disponible) + ").");
            }
            String registradoPor = nombreUsuario(tx, idUsuario);
            long nuevoId = siguienteId(tx, PAGOS);

            // ESCRITURAS
            boolean gerente = idSucursal == null;
            String momento = ahora();
            Map<String, Object> pago = new LinkedHashMap<String, Object>();
            pago.put("ID_PAGO", nuevoId);
            pago.put("ID_PEDIDO", (long) pg.ID_PEDIDO);
            pago.put("ID_SUCURSAL", sucPedido);
            pago.put("SUCURSAL", p.getString("SUCURSAL"));
            pago.put("METODO", metodo);
            pago.put("MONTO", monto);
            pago.put("BANCO", banco);
            pago.put("REFERENCIA", referencia);
            pago.put("OBSERVACION", pg.OBSERVACION == null ? null
                    : pg.OBSERVACION.trim().substring(0, Math.min(250, pg.OBSERVACION.trim().length())));
            pago.put("ESTADO", gerente ? "CONFIRMADO" : "PENDIENTE");
            pago.put("FECHA_REGISTRO", momento);
            pago.put("ID_USUARIO_REGISTRA", (long) idUsuario);
            pago.put("REGISTRADO_POR", registradoPor);
            pago.put("FECHA_RESOLUCION", gerente ? momento : null);
            pago.put("ID_USUARIO_RESUELVE", gerente ? (Object) (long) idUsuario : null);
            tx.set(doc(PAGOS, nuevoId), pago);
            guardarContador(tx, PAGOS, nuevoId);
            return nuevoId;
        });
        return (int) id;
    }

    /** Confirmar (true) o rechazar (false) un pago pendiente. */
    public void Resolver(final int idPago, final boolean confirmar, final int idUsuario) throws Exception {
        final DocumentReference ref = doc(PAGOS, idPago);
        transaccion(tx -> {
            DocumentSnapshot g = tx.get(ref).get();
            if (!g.exists()) {
                throw new ReglaNegocio(20024, "El pago " + idPago + " no existe.");
            }
            if (!"PENDIENTE".equals(g.getString("ESTADO"))) {
                throw new ReglaNegocio(20024, "El pago " + idPago + " ya fue " + g.getString("ESTADO") + ".");
            }
            tx.update(ref, fila("ESTADO", confirmar ? "CONFIRMADO" : "RECHAZADO",
                    "FECHA_RESOLUCION", ahora(), "ID_USUARIO_RESUELVE", (long) idUsuario));
            return null;
        });
    }

    private static void sumar(Map<Long, Double> m, long k, double v) {
        Double previo = m.get(k);
        m.put(k, (previo == null ? 0 : previo) + v);
    }

    private static double valor(Map<Long, Double> m, long k) {
        Double v = m.get(k);
        return v == null ? 0 : redondear(v, 2);
    }
}
