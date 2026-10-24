package BACKEND.manager;

import BACKEND.model.LineaReceta;
import BACKEND.model.NuevoPan;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Transaction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Panes nuevos y recetas (antes PKG_CATALOGO). Solo gerencia. */
public class CatalogoManager extends FirestoreBase {

    /** Crea el pan y toda su receta en UNA transacción. Devuelve el ID del pan. */
    public int CrearPan(final NuevoPan p) throws Exception {
        final String nombre = p.NOMBRE == null ? "" : p.NOMBRE.trim().replaceAll("\\s+", " ");
        if (nombre.length() < 3) {
            throw new ReglaNegocio(20018, "El nombre del pan debe tener al menos 3 letras.");
        }
        validarPrecioMinimo(p.PRECIO, p.STOCK_MINIMO);
        long id = transaccion(tx -> {
            // LECTURAS
            for (QueryDocumentSnapshot otro : tx.get(col(PRODUCTOS)).get().getDocuments()) {
                if (nombre.equalsIgnoreCase(otro.getString("NOMBRE"))) {
                    throw new ReglaNegocio(20018, "Ya existe un pan llamado " + nombre + ".");
                }
            }
            List<Map<String, Object>> receta = validarReceta(tx, p.RECETA);
            long nuevoId = siguienteId(tx, PRODUCTOS);
            // ESCRITURAS
            Map<String, Object> pan = new LinkedHashMap<String, Object>();
            pan.put("ID_PRODUCTO", nuevoId);
            pan.put("NOMBRE", nombre);
            pan.put("UNIDAD_MEDIDA", p.UNIDAD_MEDIDA == null || p.UNIDAD_MEDIDA.trim().isEmpty() ? "unidad" : p.UNIDAD_MEDIDA.trim());
            pan.put("PRECIO", redondear(p.PRECIO, 2));
            pan.put("STOCK_ACTUAL", 0.0);
            pan.put("STOCK_MINIMO", p.STOCK_MINIMO);
            pan.put("ESTADO", "ACTIVO");
            pan.put("RECETA", receta);
            tx.set(doc(PRODUCTOS, nuevoId), pan);
            guardarContador(tx, PRODUCTOS, nuevoId);
            return nuevoId;
        });
        return (int) id;
    }

    /** Reemplaza la receta completa del pan. */
    public void GuardarReceta(final int idProducto, final List<LineaReceta> lineas) throws Exception {
        final DocumentReference ref = doc(PRODUCTOS, idProducto);
        transaccion(tx -> {
            if (!tx.get(ref).get().exists()) {
                throw new ReglaNegocio(20002, "El producto " + idProducto + " no existe.");
            }
            List<Map<String, Object>> receta = validarReceta(tx, lineas);
            tx.update(ref, fila("RECETA", receta));
            return null;
        });
    }

    /** Cambia precio, existencia mínima y estado (ACTIVO / INACTIVO). */
    public void ActualizarPan(final int idProducto, final NuevoPan p) throws Exception {
        validarPrecioMinimo(p.PRECIO, p.STOCK_MINIMO);
        if (!"ACTIVO".equals(p.ESTADO) && !"INACTIVO".equals(p.ESTADO)) {
            throw new ReglaNegocio(20018, "El estado debe ser ACTIVO o INACTIVO.");
        }
        final DocumentReference ref = doc(PRODUCTOS, idProducto);
        transaccion(tx -> {
            if (!tx.get(ref).get().exists()) {
                throw new ReglaNegocio(20002, "El producto " + idProducto + " no existe.");
            }
            tx.update(ref, fila("PRECIO", redondear(p.PRECIO, 2), "STOCK_MINIMO", p.STOCK_MINIMO, "ESTADO", p.ESTADO));
            return null;
        });
    }

    private void validarPrecioMinimo(Double precio, Double minimo) {
        if (precio == null || precio <= 0) {
            throw new ReglaNegocio(20018, "El precio debe ser mayor que 0.");
        }
        if (minimo == null || minimo < 0) {
            throw new ReglaNegocio(20018, "La existencia mínima no puede ser negativa.");
        }
    }

    /** Revisa cada línea (insumo existe y activo, cantidad > 0, sin repetidos). Solo LEE. */
    private List<Map<String, Object>> validarReceta(Transaction tx, List<LineaReceta> lineas) throws Exception {
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaNegocio(20019, "La receta debe tener al menos un insumo.");
        }
        List<Map<String, Object>> receta = new ArrayList<Map<String, Object>>();
        Set<Integer> vistos = new HashSet<Integer>();
        for (LineaReceta l : lineas) {
            DocumentSnapshot ins = tx.get(doc(INSUMOS, l.ID_INSUMO)).get();
            if (!ins.exists()) {
                throw new ReglaNegocio(20009, "El insumo " + l.ID_INSUMO + " no existe.");
            }
            String nombre = ins.getString("NOMBRE");
            if (!"ACTIVO".equals(ins.getString("ESTADO"))) {
                throw new ReglaNegocio(20009, "El insumo " + nombre + " está inactivo.");
            }
            if (l.CANTIDAD == null || l.CANTIDAD <= 0) {
                throw new ReglaNegocio(20005, "La cantidad de " + nombre + " debe ser mayor que 0.");
            }
            if (!vistos.add(l.ID_INSUMO)) {
                throw new ReglaNegocio(20019, "El insumo " + nombre + " está repetido en la receta.");
            }
            receta.add(fila("ID_INSUMO", (long) l.ID_INSUMO, "CANTIDAD", redondear(l.CANTIDAD, 3)));
        }
        return receta;
    }
}
