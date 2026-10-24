package BACKEND.manager;

import BACKEND.model.Pedido;
import BACKEND.model.PedidoDetalle;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Transaction;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pedidos (antes PKG_PANADERIA_WEB.SP_REGISTRAR_PEDIDO / SP_LISTAR_PEDIDOS y
 * PKG_PANADERIA.SP_DESPACHAR / ENTREGAR / CANCELAR).
 * Cada pedido guarda su DETALLE adentro: [{ID_PEDIDO_DETALLE, ID_PRODUCTO, PRODUCTO,
 * CANTIDAD, PRECIO_UNITARIO, SUBTOTAL}, ...], así encabezado y panes se guardan juntos.
 */
public class PedidoManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerPedidos(Integer idSucursal, String estado) throws Exception {
        Query q = col(PEDIDOS);
        if (idSucursal != null) {
            q = q.whereEqualTo("ID_SUCURSAL", (long) idSucursal);
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : filas(q.get().get())) {
            if (estado == null || estado.equals(p.get("ESTADO"))) {
                salida.add(sin(p, "DETALLE"));
            }
        }
        return ordenar(salida, "ID_PEDIDO", true);
    }

    public List<Map<String, Object>> ObtenerDetalle(int idPedido) throws Exception {
        DocumentSnapshot p = doc(PEDIDOS, idPedido).get().get();
        if (!p.exists()) {
            throw new ReglaNegocio(20004, "El pedido " + idPedido + " no existe.");
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> d : detalle(p)) {
            Map<String, Object> f = new LinkedHashMap<String, Object>(d);
            f.put("ID_PEDIDO", idPedido);
            salida.add(f);
        }
        return salida;
    }

    /** Registra el pedido completo (encabezado + panes) en UNA transacción. Devuelve el ID creado. */
    public int RegistrarPedido(final Pedido item) throws Exception {
        LocalDate entrega = fecha(item.FECHA_ENTREGA);
        if (entrega != null && entrega.isBefore(hoy())) {
            throw new ReglaNegocio(20010, "La fecha de entrega no puede ser anterior a hoy.");
        }
        final String fechaEntrega = (entrega == null ? hoy().plusDays(1) : entrega).toString();
        if (item.DETALLE == null || item.DETALLE.isEmpty()) {
            throw new ReglaNegocio(20008, "El pedido no tiene productos.");
        }
        long id = transaccion(tx -> {
            // LECTURAS
            DocumentSnapshot suc = tx.get(doc(SUCURSALES, item.ID_SUCURSAL)).get();
            if (!suc.exists() || !"ACTIVO".equals(suc.getString("ESTADO"))) {
                throw new ReglaNegocio(20007, "La sucursal " + item.ID_SUCURSAL + " no existe o está inactiva.");
            }
            List<Map<String, Object>> lineas = new ArrayList<Map<String, Object>>();
            double total = 0;
            int n = 0;
            for (PedidoDetalle d : item.DETALLE) {
                if (d.CANTIDAD == null || d.CANTIDAD <= 0) {
                    throw new ReglaNegocio(20005, "La cantidad debe ser mayor que 0.");
                }
                DocumentSnapshot pan = tx.get(doc(PRODUCTOS, d.ID_PRODUCTO)).get();
                if (!pan.exists() || !"ACTIVO".equals(pan.getString("ESTADO"))) {
                    throw new ReglaNegocio(20002, "El producto " + d.ID_PRODUCTO + " no existe o está inactivo.");
                }
                double precio = d(pan.get("PRECIO"));
                double subtotal = redondear(d.CANTIDAD * precio, 2);
                lineas.add(fila("ID_PEDIDO_DETALLE", (long) ++n, "ID_PRODUCTO", (long) d.ID_PRODUCTO,
                        "PRODUCTO", pan.getString("NOMBRE"), "CANTIDAD", d.CANTIDAD,
                        "PRECIO_UNITARIO", precio, "SUBTOTAL", subtotal));
                total += subtotal;
            }
            long nuevoId = siguienteId(tx, PEDIDOS);
            // ESCRITURAS
            Map<String, Object> pedido = new LinkedHashMap<String, Object>();
            pedido.put("ID_PEDIDO", nuevoId);
            pedido.put("ID_SUCURSAL", (long) item.ID_SUCURSAL);
            pedido.put("SUCURSAL", suc.getString("NOMBRE"));
            pedido.put("FECHA_PEDIDO", ahora());
            pedido.put("FECHA_ENTREGA", fechaEntrega);
            pedido.put("ESTADO", "PENDIENTE");
            pedido.put("TOTAL", redondear(total, 2));
            pedido.put("DETALLE", lineas);
            tx.set(doc(PEDIDOS, nuevoId), pedido);
            guardarContador(tx, PEDIDOS, nuevoId);
            return nuevoId;
        });
        return (int) id;
    }

    /** Sucursal dueña del pedido (para validar permisos). */
    public Integer SucursalDePedido(int idPedido) throws Exception {
        DocumentSnapshot p = doc(PEDIDOS, idPedido).get().get();
        if (!p.exists()) {
            throw new ReglaNegocio(20004, "El pedido " + idPedido + " no existe.");
        }
        return num(p.get("ID_SUCURSAL")).intValue();
    }

    /** Despachar: revisa que haya pan para todo el pedido, lo descuenta y cambia el estado. */
    public void Despachar(final int idPedido) throws Exception {
        final DocumentReference ref = doc(PEDIDOS, idPedido);
        transaccion(tx -> {
            DocumentSnapshot p = pedidoEnEstado(tx, ref, idPedido, "PENDIENTE");
            // Sumar por pan (por si un pan viene en dos líneas)
            Map<Long, Double> porPan = new LinkedHashMap<Long, Double>();
            for (Map<String, Object> d : detalle(p)) {
                long idPan = num(d.get("ID_PRODUCTO")).longValue();
                Double previo = porPan.get(idPan);
                porPan.put(idPan, (previo == null ? 0 : previo) + d(d.get("CANTIDAD")));
            }
            if (porPan.isEmpty()) {
                throw new ReglaNegocio(20008, "El pedido " + idPedido + " no tiene productos.");
            }
            List<DocumentReference> refs = new ArrayList<DocumentReference>();
            List<Double> nuevos = new ArrayList<Double>();
            for (Map.Entry<Long, Double> e : porPan.entrySet()) {
                DocumentReference refPan = doc(PRODUCTOS, e.getKey());
                DocumentSnapshot pan = tx.get(refPan).get();
                double hay = d(pan.get("STOCK_ACTUAL"));
                if (hay < e.getValue()) {
                    throw new ReglaNegocio(20003, "No hay suficiente " + pan.getString("NOMBRE") + " producido: hay "
                            + txt(hay) + ", el pedido pide " + txt(e.getValue()) + ".");
                }
                refs.add(refPan);
                nuevos.add(redondear(hay - e.getValue(), 2));
            }
            for (int i = 0; i < refs.size(); i++) {
                tx.update(refs.get(i), fila("STOCK_ACTUAL", nuevos.get(i)));
            }
            tx.update(ref, fila("ESTADO", "DESPACHADO"));
            return null;
        });
    }

    public void Entregar(final int idPedido) throws Exception {
        cambiarEstado(idPedido, "DESPACHADO", "ENTREGADO");
    }

    public void Cancelar(final int idPedido) throws Exception {
        cambiarEstado(idPedido, "PENDIENTE", "CANCELADO");
    }

    private void cambiarEstado(final int idPedido, final String debeEstar, final String nuevo) throws Exception {
        final DocumentReference ref = doc(PEDIDOS, idPedido);
        transaccion(tx -> {
            pedidoEnEstado(tx, ref, idPedido, debeEstar);
            tx.update(ref, fila("ESTADO", nuevo));
            return null;
        });
    }

    /** Lee el pedido dentro de la transacción y verifica que esté en el estado esperado. */
    private DocumentSnapshot pedidoEnEstado(Transaction tx, DocumentReference ref, int idPedido, String esperado)
            throws Exception {
        DocumentSnapshot p = tx.get(ref).get();
        if (!p.exists()) {
            throw new ReglaNegocio(20004, "El pedido " + idPedido + " no existe.");
        }
        String estado = p.getString("ESTADO");
        if (!esperado.equals(estado)) {
            throw new ReglaNegocio(20004, "El pedido " + idPedido + " está " + estado
                    + "; para esta operación debe estar " + esperado + ".");
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> detalle(DocumentSnapshot pedido) {
        Object d = pedido.get("DETALLE");
        return d instanceof List ? (List<Map<String, Object>>) d : new ArrayList<Map<String, Object>>();
    }
}
