package BACKEND.manager;

import BACKEND.model.Devolucion;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Transaction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Devoluciones (antes PKG_DEVOLUCION).
 *   NO_SOLICITADO: llegó un pan que NO estaba en el pedido.
 *   EXCEDENTE    : llegó más cantidad de la pedida.
 *   DANADO       : llegó en mal estado.
 * Al APROBAR: NO_SOLICITADO y EXCEDENTE regresan al inventario; DANADO es merma.
 */
public class DevolucionManager extends FirestoreBase {

    /** idSucursal null = todas (gerente). */
    public List<Map<String, Object>> ObtenerDevoluciones(Integer idSucursal, String estado) throws Exception {
        Query q = col(DEVOLUCIONES);
        if (idSucursal != null) {
            q = q.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> d : filas(q.get().get())) {
            if (estado == null || estado.equals(d.get("ESTADO"))) {
                salida.add(d);
            }
        }
        return ordenar(salida, "ID_DEVOLUCION", true);
    }

    /** Registra la devolución en una transacción. Devuelve el ID. */
    public int Registrar(final Devolucion d, final int idUsuario, final Integer idSucursal) throws Exception {
        if (d.CANTIDAD == null || d.CANTIDAD <= 0) {
            throw new ReglaNegocio(20005, "La cantidad debe ser mayor que 0.");
        }
        final String motivo = d.MOTIVO;
        if (motivo == null || !(motivo.equals("NO_SOLICITADO") || motivo.equals("EXCEDENTE") || motivo.equals("DANADO"))) {
            throw new ReglaNegocio(20017, "Motivo inválido. Use NO_SOLICITADO, EXCEDENTE o DANADO.");
        }
        long id = transaccion(tx -> {
            // LECTURAS
            DocumentSnapshot p = tx.get(doc(PEDIDOS, d.ID_PEDIDO)).get();
            if (!p.exists()) {
                throw new ReglaNegocio(20004, "El pedido " + d.ID_PEDIDO + " no existe.");
            }
            long sucPedido = num(p.get("ID_SUCURSAL")).longValue();
            if (idSucursal != null && sucPedido != idSucursal) {
                throw new ReglaNegocio(20012, "El pedido " + d.ID_PEDIDO + " no pertenece a su sucursal.");
            }
            String estado = p.getString("ESTADO");
            if (!"DESPACHADO".equals(estado) && !"ENTREGADO".equals(estado)) {
                throw new ReglaNegocio(20013, "Solo se pueden devolver panes de pedidos DESPACHADOS o ENTREGADOS (este está "
                        + estado + ").");
            }
            DocumentSnapshot pan = tx.get(doc(PRODUCTOS, d.ID_PRODUCTO)).get();
            if (!pan.exists()) {
                throw new ReglaNegocio(20002, "El producto " + d.ID_PRODUCTO + " no existe.");
            }
            String nombre = pan.getString("NOMBRE");

            double pedida = 0;
            for (Map<String, Object> linea : PedidoManager.detalle(p)) {
                if (num(linea.get("ID_PRODUCTO")).longValue() == d.ID_PRODUCTO) {
                    pedida += d(linea.get("CANTIDAD"));
                }
            }
            double yaDevuelta = 0;
            for (QueryDocumentSnapshot o : tx.get(col(DEVOLUCIONES).whereEqualTo("ID_PEDIDO", (long) d.ID_PEDIDO)).get().getDocuments()) {
                boolean mismoPan = num(o.get("ID_PRODUCTO")).longValue() == d.ID_PRODUCTO;
                boolean vigente = !"RECHAZADA".equals(o.getString("ESTADO"));
                boolean mismoGrupo = "NO_SOLICITADO".equals(motivo)
                        ? "NO_SOLICITADO".equals(o.getString("MOTIVO"))
                        : !"NO_SOLICITADO".equals(o.getString("MOTIVO"));
                if (mismoPan && vigente && mismoGrupo) {
                    yaDevuelta += d(o.get("CANTIDAD"));
                }
            }
            if ("NO_SOLICITADO".equals(motivo)) {
                if (pedida > 0) {
                    throw new ReglaNegocio(20014, nombre + " sí estaba en el pedido. Use EXCEDENTE o DAÑADO.");
                }
                if (yaDevuelta > 0) {
                    throw new ReglaNegocio(20015, "Ya existe una devolución de " + nombre + " no solicitado para este pedido.");
                }
            } else {
                if (pedida == 0) {
                    throw new ReglaNegocio(20014, nombre + " no estaba en el pedido. Use NO SOLICITADO.");
                }
                if (yaDevuelta + d.CANTIDAD > pedida) {
                    throw new ReglaNegocio(20015, "Se pidieron " + txt(pedida) + " de " + nombre + " y ya hay "
                            + txt(yaDevuelta) + " en devolución; no puede devolver " + txt(d.CANTIDAD) + " más.");
                }
            }
            String registradoPor = nombreUsuario(tx, idUsuario);
            long nuevoId = siguienteId(tx, DEVOLUCIONES);

            // ESCRITURAS
            Map<String, Object> dev = new LinkedHashMap<String, Object>();
            dev.put("ID_DEVOLUCION", nuevoId);
            dev.put("ID_PEDIDO", (long) d.ID_PEDIDO);
            dev.put("ID_SUCURSAL", sucPedido);
            dev.put("SUCURSAL", p.getString("SUCURSAL"));
            dev.put("ID_PRODUCTO", (long) d.ID_PRODUCTO);
            dev.put("PRODUCTO", nombre);
            dev.put("CANTIDAD", d.CANTIDAD);
            dev.put("MOTIVO", motivo);
            dev.put("OBSERVACION", d.OBSERVACION == null ? null
                    : d.OBSERVACION.trim().substring(0, Math.min(250, d.OBSERVACION.trim().length())));
            dev.put("MONTO", redondear(d.CANTIDAD * d(pan.get("PRECIO")), 2));
            dev.put("ESTADO", "PENDIENTE");
            dev.put("FECHA_REGISTRO", ahora());
            dev.put("ID_USUARIO_REGISTRA", (long) idUsuario);
            dev.put("REGISTRADO_POR", registradoPor);
            dev.put("FECHA_RESOLUCION", null);
            tx.set(doc(DEVOLUCIONES, nuevoId), dev);
            guardarContador(tx, DEVOLUCIONES, nuevoId);
            return nuevoId;
        });
        return (int) id;
    }

    /** Aprobar (true) o rechazar (false). Si se aprueba y el pan está bueno, regresa al inventario. */
    public void Resolver(final int idDevolucion, final boolean aprobar, final int idUsuario) throws Exception {
        final DocumentReference ref = doc(DEVOLUCIONES, idDevolucion);
        transaccion(tx -> {
            DocumentSnapshot d = tx.get(ref).get();
            if (!d.exists()) {
                throw new ReglaNegocio(20016, "La devolución " + idDevolucion + " no existe.");
            }
            if (!"PENDIENTE".equals(d.getString("ESTADO"))) {
                throw new ReglaNegocio(20016, "La devolución " + idDevolucion + " ya fue " + d.getString("ESTADO") + ".");
            }
            String motivo = d.getString("MOTIVO");
            DocumentReference refPan = null;
            double nuevoStock = 0;
            List<Map<String, Object>> lotes = null;
            if (aprobar && ("NO_SOLICITADO".equals(motivo) || "EXCEDENTE".equals(motivo))) {
                refPan = doc(PRODUCTOS, num(d.get("ID_PRODUCTO")).longValue());
                DocumentSnapshot pan = tx.get(refPan).get();
                nuevoStock = redondear(d(pan.get("STOCK_ACTUAL")) + d(d.get("CANTIDAD")), 2);
                // Regresa como lote con la fecha del pedido (es pan de ese día, no de hoy)
                String fechaPan = hoy().toString();
                DocumentSnapshot pedido = tx.get(doc(PEDIDOS, num(d.get("ID_PEDIDO")).longValue())).get();
                if (pedido.exists() && pedido.getString("FECHA_ENTREGA") != null
                        && pedido.getString("FECHA_ENTREGA").compareTo(fechaPan) < 0) {
                    fechaPan = pedido.getString("FECHA_ENTREGA");
                }
                lotes = Lotes.agregar(Lotes.leer(pan), fechaPan, d(d.get("CANTIDAD")));
            }
            // ESCRITURAS
            if (refPan != null) {
                tx.update(refPan, fila("STOCK_ACTUAL", nuevoStock, "LOTES", lotes));   // el pan vuelve al inventario
            }
            tx.update(ref, fila("ESTADO", aprobar ? "APROBADA" : "RECHAZADA",
                    "FECHA_RESOLUCION", ahora(), "ID_USUARIO_RESUELVE", (long) idUsuario));
            return null;
        });
    }
}
