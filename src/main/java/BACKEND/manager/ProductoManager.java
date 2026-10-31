package BACKEND.manager;

import BACKEND.model.Produccion;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panes (antes SP_LISTAR_PRODUCTOS, SP_LISTAR_RECETA y PKG_PANADERIA.SP_PRODUCIR_PAN).
 * En Firestore cada pan guarda su RECETA adentro: [{ID_INSUMO, CANTIDAD}, ...].
 */
public class ProductoManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerProductos() throws Exception {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : todos(PRODUCTOS, "ID_PRODUCTO")) {
            Map<String, Object> fila = sin(p, "RECETA", "LOTES");
            fila.put("ALERTA", d(p.get("STOCK_ACTUAL")) <= d(p.get("STOCK_MINIMO")) ? "S" : "N");
            salida.add(fila);
        }
        return salida;
    }

    /** Insumos y cantidades de un pan, con el nombre y la unidad de cada insumo. */
    public List<Map<String, Object>> ObtenerReceta(int idProducto) throws Exception {
        DocumentSnapshot p = doc(PRODUCTOS, idProducto).get().get();
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        if (!p.exists()) {
            return salida;
        }
        Map<Long, Map<String, Object>> insumos = new HashMap<Long, Map<String, Object>>();
        for (Map<String, Object> i : todos(INSUMOS, "ID_INSUMO")) {
            insumos.put(num(i.get("ID_INSUMO")).longValue(), i);
        }
        for (Map<String, Object> r : receta(p)) {
            Map<String, Object> ins = insumos.get(num(r.get("ID_INSUMO")).longValue());
            salida.add(fila(
                    "ID_INSUMO", r.get("ID_INSUMO"),
                    "INSUMO", ins == null ? "?" : ins.get("NOMBRE"),
                    "CANTIDAD", r.get("CANTIDAD"),
                    "UNIDAD_MEDIDA", ins == null ? "" : ins.get("UNIDAD_MEDIDA")));
        }
        return ordenar(salida, "ID_INSUMO", false);
    }

    /**
     * Produce pan en UNA transacción: revisa TODOS los insumos antes de tocar nada,
     * luego los descuenta y suma el pan. Si falta un insumo, no se guarda nada.
     */
    public void ProducirPan(final Produccion item) throws Exception {
        final double cantidad = item.CANTIDAD == null ? 0 : item.CANTIDAD;
        if (cantidad <= 0) {
            throw new ReglaNegocio(20005, "La cantidad debe ser mayor que 0.");
        }
        final DocumentReference refPan = doc(PRODUCTOS, item.ID_PRODUCTO);
        transaccion(tx -> {
            // a) LECTURAS (en Firestore todas las lecturas van antes que las escrituras)
            DocumentSnapshot pan = tx.get(refPan).get();
            if (!pan.exists() || !"ACTIVO".equals(pan.getString("ESTADO"))) {
                throw new ReglaNegocio(20002, "El producto " + item.ID_PRODUCTO + " no existe o está inactivo.");
            }
            List<Map<String, Object>> receta = receta(pan);
            if (receta.isEmpty()) {
                throw new ReglaNegocio(20006, "El producto " + item.ID_PRODUCTO + " no tiene receta registrada.");
            }
            List<DocumentReference> refs = new ArrayList<DocumentReference>();
            List<Double> nuevos = new ArrayList<Double>();
            for (Map<String, Object> r : receta) {
                DocumentReference refIns = doc(INSUMOS, num(r.get("ID_INSUMO")).longValue());
                DocumentSnapshot ins = tx.get(refIns).get();
                double necesita = redondear(d(r.get("CANTIDAD")) * cantidad, 3);
                if (!ins.exists()) {
                    throw new ReglaNegocio(20009, "El insumo " + r.get("ID_INSUMO") + " de la receta no existe.");
                }
                double hay = d(ins.get("STOCK_ACTUAL"));
                if (hay < necesita) {
                    throw new ReglaNegocio(20001, "Stock insuficiente de " + ins.getString("NOMBRE")
                            + ": hay " + txt(hay) + ", se necesitan " + txt(necesita) + ".");
                }
                refs.add(refIns);
                nuevos.add(redondear(hay - necesita, 3));
            }
            // b) ESCRITURAS: descontar insumos y sumar el pan
            for (int i = 0; i < refs.size(); i++) {
                tx.update(refs.get(i), fila("STOCK_ACTUAL", nuevos.get(i)));
            }
            // El pan nuevo queda registrado como un lote con la fecha de hoy (para saber cuándo se vence)
            List<Map<String, Object>> lotes = Lotes.agregar(Lotes.leer(pan), hoy().toString(), cantidad);
            tx.update(refPan, fila("STOCK_ACTUAL", redondear(d(pan.get("STOCK_ACTUAL")) + cantidad, 2),
                    "LOTES", lotes));
            return null;
        });
        Notificador.revisarEnSegundoPlano();   // ¿quedó algún insumo en el mínimo? -> correo al propietario
    }

    /** Lee la lista RECETA guardada dentro del documento del pan. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> receta(DocumentSnapshot pan) {
        Object r = pan.get("RECETA");
        return r instanceof List ? (List<Map<String, Object>>) r : new ArrayList<Map<String, Object>>();
    }
}
