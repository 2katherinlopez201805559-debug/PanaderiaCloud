package BACKEND.manager;

import BACKEND.model.CompraInsumo;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.List;
import java.util.Map;

/** Insumos (antes SP_LISTAR_INSUMOS y PKG_PANADERIA.SP_REGISTRAR_COMPRA_INSUMO). */
public class InsumoManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerInsumos() throws Exception {
        List<Map<String, Object>> lista = todos(INSUMOS, "ID_INSUMO");
        for (Map<String, Object> i : lista) {
            i.put("ALERTA", d(i.get("STOCK_ACTUAL")) <= d(i.get("STOCK_MINIMO")) ? "S" : "N");
        }
        return lista;
    }

    /** Registra una compra de materia prima (sube el stock del insumo) en una transacción. */
    public void RegistrarCompra(final CompraInsumo item) throws Exception {
        if (item.CANTIDAD == null || item.CANTIDAD <= 0) {
            throw new ReglaNegocio(20005, "La cantidad debe ser mayor que 0.");
        }
        final DocumentReference ref = doc(INSUMOS, item.ID_INSUMO);
        transaccion(tx -> {
            DocumentSnapshot ins = tx.get(ref).get();
            if (!ins.exists() || !"ACTIVO".equals(ins.getString("ESTADO"))) {
                throw new ReglaNegocio(20009, "El insumo " + item.ID_INSUMO + " no existe o está inactivo.");
            }
            double nuevo = redondear(d(ins.get("STOCK_ACTUAL")) + item.CANTIDAD, 3);
            tx.update(ref, fila("STOCK_ACTUAL", nuevo));
            return null;
        });
    }
}
