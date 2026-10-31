package BACKEND.manager;

import BACKEND.model.PrecioInsumo;
import BACKEND.model.Proveedor;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proveedores de insumos y sus precios.
 * Cada proveedor guarda adentro PRECIOS: [{ID_INSUMO, PRECIO, FECHA}, ...]
 * para poder COMPARAR cuál vende más barato cada insumo.
 */
public class ProveedorManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerProveedores() throws Exception {
        cargarEjemplosSiHaceFalta();
        return todos(PROVEEDORES, "ID_PROVEEDOR");
    }

    /** Crea (idProveedor null) o actualiza un proveedor. Devuelve el ID. */
    public int GuardarProveedor(final Integer idProveedor, final Proveedor p) throws Exception {
        final String nombre = p.NOMBRE == null ? "" : p.NOMBRE.trim();
        if (nombre.length() < 3) {
            throw new ReglaNegocio(20025, "El nombre del proveedor debe tener al menos 3 letras.");
        }
        cargarEjemplosSiHaceFalta();   // para que los números de los ejemplos no choquen con el nuevo
        final String estado = p.ESTADO == null || p.ESTADO.trim().isEmpty() ? "ACTIVO" : p.ESTADO.trim().toUpperCase();
        if (!estado.equals("ACTIVO") && !estado.equals("INACTIVO")) {
            throw new ReglaNegocio(20025, "El estado debe ser ACTIVO o INACTIVO.");
        }
        long id = transaccion(tx -> {
            for (DocumentSnapshot o : tx.get(col(PROVEEDORES)).get().getDocuments()) {
                boolean otro = idProveedor == null || num(o.get("ID_PROVEEDOR")).intValue() != idProveedor;
                if (otro && nombre.equalsIgnoreCase(String.valueOf(o.getString("NOMBRE")).trim())) {
                    throw new ReglaNegocio(20025, "Ya existe un proveedor llamado " + nombre + ".");
                }
            }
            long nuevoId;
            DocumentSnapshot actual = null;
            if (idProveedor == null) {
                nuevoId = siguienteId(tx, PROVEEDORES);
            } else {
                nuevoId = idProveedor;
                actual = tx.get(doc(PROVEEDORES, nuevoId)).get();
                if (!actual.exists()) {
                    throw new ReglaNegocio(20025, "El proveedor " + idProveedor + " no existe.");
                }
            }
            Map<String, Object> datos = fila("ID_PROVEEDOR", nuevoId, "NOMBRE", nombre,
                    "TELEFONO", limpio(p.TELEFONO), "CORREO", limpio(p.CORREO), "NIT", limpio(p.NIT),
                    "DIRECCION", limpio(p.DIRECCION), "ESTADO", estado);
            if (actual == null) {
                datos.put("PRECIOS", new ArrayList<Object>());
                tx.set(doc(PROVEEDORES, nuevoId), datos);
                guardarContador(tx, PROVEEDORES, nuevoId);
            } else {
                tx.update(doc(PROVEEDORES, nuevoId), datos);
            }
            return nuevoId;
        });
        return (int) id;
    }

    /** Guarda (o quita, si PRECIO es null) el precio de un insumo para un proveedor. */
    public void GuardarPrecio(final int idProveedor, final PrecioInsumo pr) throws Exception {
        if (pr.ID_INSUMO == null) {
            throw new ReglaNegocio(20009, "Seleccione el insumo.");
        }
        if (pr.PRECIO != null && pr.PRECIO <= 0) {
            throw new ReglaNegocio(20021, "El precio debe ser mayor que 0.");
        }
        final DocumentReference ref = doc(PROVEEDORES, idProveedor);
        transaccion(tx -> {
            DocumentSnapshot prov = tx.get(ref).get();
            if (!prov.exists()) {
                throw new ReglaNegocio(20025, "El proveedor " + idProveedor + " no existe.");
            }
            DocumentSnapshot ins = tx.get(doc(INSUMOS, pr.ID_INSUMO)).get();
            if (!ins.exists()) {
                throw new ReglaNegocio(20009, "El insumo " + pr.ID_INSUMO + " no existe.");
            }
            tx.update(ref, fila("PRECIOS", pr.PRECIO == null ? sinPrecio(prov, pr.ID_INSUMO)
                    : conPrecio(prov, pr.ID_INSUMO, redondear(pr.PRECIO, 2))));
            return null;
        });
    }

    /**
     * Comparación de precios: por cada insumo, los precios de todos los proveedores activos
     * (del más barato al más caro), el mejor precio, el último precio pagado y cuánto se ahorra.
     */
    public List<Map<String, Object>> CompararPrecios() throws Exception {
        List<Map<String, Object>> proveedores = ObtenerProveedores();
        Map<Long, Map<String, Object>> ultimaCompra = new HashMap<Long, Map<String, Object>>();
        for (Map<String, Object> c : ordenar(filas(col(COMPRAS).get().get()), "ID_COMPRA", false)) {
            if (c.get("PRECIO_UNITARIO") != null) {
                ultimaCompra.put(num(c.get("ID_INSUMO")).longValue(), c);   // queda la más reciente
            }
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> ins : todos(INSUMOS, "ID_INSUMO")) {
            long idIns = num(ins.get("ID_INSUMO")).longValue();
            List<Map<String, Object>> ofertas = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> pv : proveedores) {
                if (!"ACTIVO".equals(pv.get("ESTADO"))) {
                    continue;
                }
                for (Map<String, Object> pr : precios(pv.get("PRECIOS"))) {
                    if (num(pr.get("ID_INSUMO")).longValue() == idIns) {
                        ofertas.add(fila("ID_PROVEEDOR", pv.get("ID_PROVEEDOR"), "PROVEEDOR", pv.get("NOMBRE"),
                                "PRECIO", d(pr.get("PRECIO")), "FECHA", pr.get("FECHA")));
                    }
                }
            }
            Collections.sort(ofertas, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> a, Map<String, Object> b) {
                    return Double.compare(d(a.get("PRECIO")), d(b.get("PRECIO")));
                }
            });
            Map<String, Object> f = fila("ID_INSUMO", idIns, "INSUMO", ins.get("NOMBRE"), "UNIDAD_MEDIDA", ins.get("UNIDAD_MEDIDA"),
                    "OFERTAS", ofertas, "MEJOR", ofertas.isEmpty() ? null : ofertas.get(0));
            Map<String, Object> u = ultimaCompra.get(idIns);
            f.put("ULTIMA_COMPRA", u == null ? null : fila("FECHA", u.get("FECHA"), "PROVEEDOR", u.get("PROVEEDOR"),
                    "PRECIO", d(u.get("PRECIO_UNITARIO"))));
            if (u != null && !ofertas.isEmpty()) {
                f.put("AHORRO_POR_UNIDAD", redondear(Math.max(d(u.get("PRECIO_UNITARIO")) - d(ofertas.get(0).get("PRECIO")), 0), 2));
            }
            if (ofertas.size() > 1) {
                f.put("DIFERENCIA", redondear(d(ofertas.get(ofertas.size() - 1).get("PRECIO")) - d(ofertas.get(0).get("PRECIO")), 2));
            }
            salida.add(f);
        }
        return salida;
    }

    /* ---------------- Ayudas ---------------- */

    static List<Map<String, Object>> conPrecio(DocumentSnapshot prov, long idInsumo, double precio) {
        List<Map<String, Object>> lista = sinPrecio(prov, idInsumo);
        lista.add(fila("ID_INSUMO", idInsumo, "PRECIO", precio, "FECHA", hoy().toString()));
        return lista;
    }

    static List<Map<String, Object>> sinPrecio(DocumentSnapshot prov, long idInsumo) {
        List<Map<String, Object>> lista = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> pr : precios(prov.get("PRECIOS"))) {
            if (num(pr.get("ID_INSUMO")).longValue() != idInsumo) {
                lista.add(new LinkedHashMap<String, Object>(pr));
            }
        }
        return lista;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> precios(Object v) {
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<Map<String, Object>>();
    }

    private static String limpio(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim().substring(0, Math.min(150, s.trim().length()));
    }

    /**
     * La primera vez crea 3 proveedores DE EJEMPLO con precios por unidad (Q), para que se
     * pueda probar la comparación. Se pueden editar o desactivar desde la página de compras.
     */
    private void cargarEjemplosSiHaceFalta() throws Exception {
        DocumentReference marca = db().collection("sistema").document("proveedores");
        if (marca.get().get().exists()) {
            return;
        }
        synchronized (ProveedorManager.class) {
            if (marca.get().get().exists()) {
                return;
            }
            // Precios de ejemplo por: kg, kg, litro, litro, litro, kg, kg, kg, kg, unidad (insumos 1 a 10)
            Object[][] ejemplos = {
                {"Distribuidora La Espiga (ejemplo)", "5555-0101", new double[]{7.50, 6.80, 9.00, 11.00, 12.50, 32.00, 62.00, 6.50, 3.00, 1.10}},
                {"Abarrotes El Trigal (ejemplo)", "5555-0202", new double[]{7.20, 7.00, 8.50, 11.50, 12.00, 30.00, 65.00, 6.25, 3.25, 1.05}},
                {"Proveedora Doña Rosa (ejemplo)", "5555-0303", new double[]{7.90, 6.60, 9.50, 10.75, 13.00, 31.50, 60.00, 6.80, 2.90, 1.15}}
            };
            WriteBatch lote = db().batch();
            String hoy = hoy().toString();
            for (int i = 0; i < ejemplos.length; i++) {
                List<Map<String, Object>> precios = new ArrayList<Map<String, Object>>();
                double[] p = (double[]) ejemplos[i][2];
                for (int j = 0; j < p.length; j++) {
                    precios.add(fila("ID_INSUMO", (long) (j + 1), "PRECIO", p[j], "FECHA", hoy));
                }
                lote.set(doc(PROVEEDORES, i + 1), fila("ID_PROVEEDOR", (long) (i + 1), "NOMBRE", ejemplos[i][0],
                        "TELEFONO", ejemplos[i][1], "CORREO", null, "NIT", null, "DIRECCION", null,
                        "ESTADO", "ACTIVO", "PRECIOS", precios));
            }
            lote.set(doc(CONTADORES, PROVEEDORES), fila("valor", (long) ejemplos.length));
            lote.set(marca, fila("cargado", ahora()));
            lote.commit().get();
        }
    }
}
