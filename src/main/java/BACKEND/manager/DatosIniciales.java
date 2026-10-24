package BACKEND.manager;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos iniciales (lo que hacían los INSERT de PanaderiaSanCarlos.sql y
 * 03_seguridad_devoluciones.sql). Se cargan SOLO la primera vez, cuando el
 * documento sistema/inicial todavía no existe.
 *
 * Para empezar de cero: en la consola de Firebase borre todas las
 * colecciones (incluida "sistema") y vuelva a abrir la página.
 */
public final class DatosIniciales {

    public static final String CLAVE_INICIAL = "Panaderia2026";

    private DatosIniciales() {
    }

    public static synchronized void cargarSiHaceFalta(Firestore db) throws Exception {
        DocumentReference marca = db.collection("sistema").document("inicial");
        if (marca.get().get().exists()) {
            return;
        }
        WriteBatch lote = db.batch();   // todo en un solo lote: se guarda completo o nada

        // ---- Insumos: nombre, unidad, stock, mínimo ----
        Object[][] insumos = {
            {"Harina de fuerza", "kg", 100, 20}, {"Harina suave", "kg", 100, 20},
            {"Vinagre", "litro", 20, 5}, {"Leche", "litro", 50, 10},
            {"Jugo de fruta", "litro", 20, 5}, {"Levadura fresca", "kg", 10, 2},
            {"Levadura seca", "kg", 10, 2}, {"Azúcar", "kg", 50, 10},
            {"Sal", "kg", 20, 5}, {"Huevo", "unidad", 200, 50}
        };
        for (int i = 0; i < insumos.length; i++) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ID_INSUMO", i + 1);
            m.put("NOMBRE", insumos[i][0]);
            m.put("UNIDAD_MEDIDA", insumos[i][1]);
            m.put("STOCK_ACTUAL", ((Integer) insumos[i][2]).doubleValue());
            m.put("STOCK_MINIMO", ((Integer) insumos[i][3]).doubleValue());
            m.put("ESTADO", "ACTIVO");
            lote.set(db.collection("insumos").document(String.valueOf(i + 1)), m);
        }

        // ---- Panes: nombre, precio, mínimo, receta {idInsumo, cantidad por pan} ----
        Object[][] panes = {
            {"Cubos", 1.00, 30, new double[][]{{1, 0.400}, {6, 0.010}, {8, 0.030}, {9, 0.005}}},
            {"Especial", 1.00, 50, new double[][]{{1, 0.300}, {2, 0.150}, {4, 0.100}, {10, 1}}},
            {"Francés", 1.00, 30, new double[][]{{1, 0.350}, {7, 0.008}, {9, 0.006}, {3, 0.005}}},
            {"Alemán", 1.50, 50, new double[][]{{1, 0.250}, {2, 0.150}, {8, 0.040}, {10, 1}}},
            {"Cachos", 1.00, 30, new double[][]{{1, 0.300}, {8, 0.025}, {4, 0.080}}},
            {"Cortadas", 1.25, 15, new double[][]{{2, 0.280}, {8, 0.050}, {10, 1}}},
            {"Batidas", 1.25, 15, new double[][]{{2, 0.250}, {4, 0.120}, {6, 0.008}}},
            {"Campechanas", 1.50, 50, new double[][]{{1, 0.200}, {2, 0.200}, {8, 0.060}, {10, 1}}},
            {"Shecas", 1.50, 50, new double[][]{{1, 0.220}, {9, 0.005}, {7, 0.007}, {5, 0.030}}}
        };
        for (int i = 0; i < panes.length; i++) {
            List<Map<String, Object>> receta = new ArrayList<Map<String, Object>>();
            for (double[] linea : (double[][]) panes[i][3]) {
                Map<String, Object> r = new LinkedHashMap<String, Object>();
                r.put("ID_INSUMO", (long) linea[0]);
                r.put("CANTIDAD", linea[1]);
                receta.add(r);
            }
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ID_PRODUCTO", i + 1);
            m.put("NOMBRE", panes[i][0]);
            m.put("UNIDAD_MEDIDA", "unidad");
            m.put("PRECIO", panes[i][1]);
            m.put("STOCK_ACTUAL", 0.0);
            m.put("STOCK_MINIMO", ((Integer) panes[i][2]).doubleValue());
            m.put("ESTADO", "ACTIVO");
            m.put("RECETA", receta);
            lote.set(db.collection("productos").document(String.valueOf(i + 1)), m);
        }

        // ---- Sucursales ----
        String[] sucursales = {"CENTRAL", "CHIVA", "BARCO", "HEYDI", "TERMINAL"};
        for (int i = 0; i < sucursales.length; i++) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ID_SUCURSAL", i + 1);
            m.put("NOMBRE", sucursales[i]);
            m.put("DIRECCION", "Por definir");
            m.put("TELEFONO", "Por definir");
            m.put("ESTADO", "ACTIVO");
            lote.set(db.collection("sucursales").document(String.valueOf(i + 1)), m);
        }

        // ---- Usuarios (documento = nombre de usuario). Clave guardada como SHA-256, nunca en texto. ----
        Object[][] usuarios = {
            {"kgalvez", "Kevin Gálvez", "GERENTE", null},
            {"central", "Sucursal Central", "SUCURSAL", 1}, {"chiva", "Sucursal Chiva", "SUCURSAL", 2},
            {"barco", "Sucursal Barco", "SUCURSAL", 3}, {"heydi", "Sucursal Heydi", "SUCURSAL", 4},
            {"terminal", "Sucursal Terminal", "SUCURSAL", 5}
        };
        for (int i = 0; i < usuarios.length; i++) {
            String usuario = (String) usuarios[i][0];
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("ID_USUARIO", i + 1);
            m.put("USUARIO", usuario);
            m.put("CLAVE_HASH", hash(usuario, CLAVE_INICIAL));
            m.put("NOMBRE", usuarios[i][1]);
            m.put("ROL", usuarios[i][2]);
            m.put("ID_SUCURSAL", usuarios[i][3]);
            m.put("ESTADO", "ACTIVO");
            m.put("ULTIMO_ACCESO", null);
            lote.set(db.collection("usuarios").document(usuario), m);
        }

        // ---- Contadores (siguiente número de cada colección) ----
        lote.set(db.collection("contadores").document("insumos"), valor(insumos.length));
        lote.set(db.collection("contadores").document("productos"), valor(panes.length));
        lote.set(db.collection("contadores").document("sucursales"), valor(sucursales.length));
        lote.set(db.collection("contadores").document("usuarios"), valor(usuarios.length));

        Map<String, Object> m = new HashMap<String, Object>();
        m.put("cargado", FirestoreBase.ahora());
        lote.set(marca, m);

        lote.commit().get();
    }

    private static Map<String, Object> valor(long v) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("valor", v);
        return m;
    }

    /** SHA-256 de "usuario:clave" en hexadecimal (igual que STANDARD_HASH en la versión Oracle). */
    public static String hash(String usuario, String clave) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest((usuario.trim().toLowerCase() + ":" + clave).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
