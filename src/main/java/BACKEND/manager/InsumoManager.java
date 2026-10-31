package BACKEND.manager;

import BACKEND.model.CompraInsumo;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Insumos (antes SP_LISTAR_INSUMOS y PKG_PANADERIA.SP_REGISTRAR_COMPRA_INSUMO).
 * Cada compra queda registrada en la colección "compras" (registro de facturas).
 */
public class InsumoManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerInsumos() throws Exception {
        List<Map<String, Object>> lista = todos(INSUMOS, "ID_INSUMO");
        for (Map<String, Object> i : lista) {
            i.put("ALERTA", d(i.get("STOCK_ACTUAL")) <= d(i.get("STOCK_MINIMO")) ? "S" : "N");
        }
        return lista;
    }

    /**
     * Registra una compra de materia prima en UNA transacción:
     *  sube el stock del insumo, guarda la compra (factura) y, si se indicó proveedor y precio,
     *  actualiza el precio de ese proveedor para comparar. Devuelve el ID de la compra.
     */
    public int RegistrarCompra(final CompraInsumo item, final int idUsuario) throws Exception {
        if (item.CANTIDAD == null || item.CANTIDAD <= 0) {
            throw new ReglaNegocio(20005, "La cantidad debe ser mayor que 0.");
        }
        if (item.PRECIO_UNITARIO != null && item.PRECIO_UNITARIO <= 0) {
            throw new ReglaNegocio(20021, "El precio unitario debe ser mayor que 0.");
        }
        final String factura = item.NO_FACTURA == null || item.NO_FACTURA.trim().isEmpty() ? null
                : item.NO_FACTURA.trim().toUpperCase();
        final DocumentReference ref = doc(INSUMOS, item.ID_INSUMO);
        long id = transaccion(tx -> {
            // LECTURAS
            DocumentSnapshot ins = tx.get(ref).get();
            if (!ins.exists() || !"ACTIVO".equals(ins.getString("ESTADO"))) {
                throw new ReglaNegocio(20009, "El insumo " + item.ID_INSUMO + " no existe o está inactivo.");
            }
            DocumentReference refProv = null;
            DocumentSnapshot prov = null;
            if (item.ID_PROVEEDOR != null) {
                refProv = doc(PROVEEDORES, item.ID_PROVEEDOR);
                prov = tx.get(refProv).get();
                if (!prov.exists()) {
                    throw new ReglaNegocio(20025, "El proveedor " + item.ID_PROVEEDOR + " no existe.");
                }
            }
            String registradoPor = nombreUsuario(tx, idUsuario);
            long nuevoId = siguienteId(tx, COMPRAS);

            // ESCRITURAS
            double nuevo = redondear(d(ins.get("STOCK_ACTUAL")) + item.CANTIDAD, 3);
            tx.update(ref, fila("STOCK_ACTUAL", nuevo));

            Double precio = item.PRECIO_UNITARIO == null ? null : redondear(item.PRECIO_UNITARIO, 2);
            Map<String, Object> compra = new LinkedHashMap<String, Object>();
            compra.put("ID_COMPRA", nuevoId);
            compra.put("FECHA", ahora());
            compra.put("ID_INSUMO", (long) item.ID_INSUMO);
            compra.put("INSUMO", ins.getString("NOMBRE"));
            compra.put("UNIDAD_MEDIDA", ins.getString("UNIDAD_MEDIDA"));
            compra.put("CANTIDAD", redondear(item.CANTIDAD, 3));
            compra.put("ID_PROVEEDOR", prov == null ? null : Long.valueOf(item.ID_PROVEEDOR.longValue()));
            compra.put("PROVEEDOR", prov == null ? null : prov.getString("NOMBRE"));
            compra.put("PRECIO_UNITARIO", precio);
            compra.put("TOTAL", precio == null ? 0.0 : redondear(precio * item.CANTIDAD, 2));
            compra.put("NO_FACTURA", factura);
            compra.put("OBSERVACION", item.OBSERVACION == null ? null
                    : item.OBSERVACION.trim().substring(0, Math.min(250, item.OBSERVACION.trim().length())));
            compra.put("ID_USUARIO_REGISTRA", (long) idUsuario);
            compra.put("REGISTRADO_POR", registradoPor);
            tx.set(doc(COMPRAS, nuevoId), compra);
            guardarContador(tx, COMPRAS, nuevoId);

            if (refProv != null && precio != null) {      // el último precio pagado queda en la lista del proveedor
                tx.update(refProv, fila("PRECIOS", ProveedorManager.conPrecio(prov, item.ID_INSUMO, precio)));
            }
            return nuevoId;
        });
        return (int) id;
    }

    /** Registro de compras (facturas), de la más nueva a la más vieja. Fechas opcionales AAAA-MM-DD. */
    public List<Map<String, Object>> ObtenerCompras(String desdeTxt, String hastaTxt, Integer idProveedor) throws Exception {
        LocalDate desde = fecha(desdeTxt), hasta = fecha(hastaTxt);
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> c : filas(col(COMPRAS).get().get())) {
            String f = String.valueOf(c.get("FECHA"));
            String dia = f.length() >= 10 ? f.substring(0, 10) : f;
            if ((desde != null && dia.compareTo(desde.toString()) < 0) || (hasta != null && dia.compareTo(hasta.toString()) > 0)) {
                continue;
            }
            if (idProveedor != null && (c.get("ID_PROVEEDOR") == null || num(c.get("ID_PROVEEDOR")).intValue() != idProveedor)) {
                continue;
            }
            salida.add(c);
        }
        return ordenar(salida, "ID_COMPRA", true);
    }
}
