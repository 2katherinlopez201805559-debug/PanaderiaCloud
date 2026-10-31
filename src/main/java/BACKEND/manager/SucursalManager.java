package BACKEND.manager;

import BACKEND.model.NuevaSucursal;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sucursales (antes PKG_PANADERIA_WEB.SP_LISTAR_SUCURSALES) y alta de sucursales nuevas. */
public class SucursalManager extends FirestoreBase {

    private static final String CLAVE_INICIAL = "Panaderia2026";

    public List<Map<String, Object>> ObtenerSucursales() throws Exception {
        return todos(SUCURSALES, "ID_SUCURSAL");
    }

    /**
     * Crea la sucursal y su usuario en UNA transacción.
     * Usuario = primera palabra del nombre, en minúsculas y sin tildes ("Mercado Sur" -> "mercado").
     * Devuelve { ID_SUCURSAL, USUARIO }.
     */
    public Map<String, Object> CrearSucursal(final NuevaSucursal s) throws Exception {
        final String nombre = s.NOMBRE == null ? "" : s.NOMBRE.trim().replaceAll("\\s+", " ").toUpperCase();
        if (nombre.length() < 3 || nombre.length() > 60) {
            throw new ReglaNegocio(20007, "El nombre de la sucursal debe tener entre 3 y 60 letras.");
        }
        final String usuario = usuarioDe(nombre);
        if (!usuario.matches("[a-z0-9]{3,30}")) {
            throw new ReglaNegocio(20007, "El nombre debe empezar con una palabra de 3 letras o más (sin símbolos).");
        }
        final String clave = s.CLAVE == null || s.CLAVE.isEmpty() ? CLAVE_INICIAL : s.CLAVE;
        if (clave.length() < 6) {
            throw new ReglaNegocio(20011, "La contraseña debe tener al menos 6 caracteres.");
        }
        final String hash = DatosIniciales.hash(usuario, clave);
        return transaccion(tx -> {
            // LECTURAS
            for (DocumentSnapshot o : tx.get(col(SUCURSALES)).get().getDocuments()) {
                if (nombre.equalsIgnoreCase(o.getString("NOMBRE"))) {
                    throw new ReglaNegocio(20007, "Ya existe la sucursal " + nombre + ".");
                }
            }
            DocumentReference refUsuario = doc(USUARIOS, usuario);
            if (tx.get(refUsuario).get().exists()) {
                throw new ReglaNegocio(20007, "Ya existe el usuario \"" + usuario
                        + "\". Use otro nombre para la sucursal (la primera palabra debe ser distinta).");
            }
            long idSuc = siguienteId(tx, SUCURSALES);
            long idUsr = siguienteId(tx, USUARIOS);

            // ESCRITURAS
            tx.set(doc(SUCURSALES, idSuc), fila("ID_SUCURSAL", idSuc, "NOMBRE", nombre,
                    "DIRECCION", texto(s.DIRECCION, "Por definir"), "TELEFONO", texto(s.TELEFONO, "Por definir"),
                    "ESTADO", "ACTIVO", "FECHA_CREACION", ahora()));
            guardarContador(tx, SUCURSALES, idSuc);
            Map<String, Object> u = new LinkedHashMap<String, Object>();
            u.put("ID_USUARIO", idUsr);
            u.put("USUARIO", usuario);
            u.put("CLAVE_HASH", hash);
            u.put("NOMBRE", "Sucursal " + nombre.charAt(0) + nombre.substring(1).toLowerCase());
            u.put("ROL", "SUCURSAL");
            u.put("ID_SUCURSAL", idSuc);
            u.put("ESTADO", "ACTIVO");
            u.put("ULTIMO_ACCESO", null);
            tx.set(refUsuario, u);
            guardarContador(tx, USUARIOS, idUsr);
            return fila("ID_SUCURSAL", idSuc, "USUARIO", usuario);
        });
    }

    /** Cambia dirección, teléfono y estado. Si se desactiva, su usuario tampoco puede entrar. */
    public void ActualizarSucursal(final int idSucursal, final NuevaSucursal s) throws Exception {
        final String estado = s.ESTADO == null || s.ESTADO.isEmpty() ? "ACTIVO" : s.ESTADO.trim().toUpperCase();
        if (!estado.equals("ACTIVO") && !estado.equals("INACTIVO")) {
            throw new ReglaNegocio(20007, "El estado debe ser ACTIVO o INACTIVO.");
        }
        final DocumentReference ref = doc(SUCURSALES, idSucursal);
        transaccion(tx -> {
            DocumentSnapshot suc = tx.get(ref).get();
            if (!suc.exists()) {
                throw new ReglaNegocio(20007, "La sucursal " + idSucursal + " no existe.");
            }
            List<com.google.cloud.firestore.QueryDocumentSnapshot> usuarios =
                    tx.get(col(USUARIOS).whereEqualTo("ID_SUCURSAL", (long) idSucursal)).get().getDocuments();
            tx.update(ref, fila("DIRECCION", texto(s.DIRECCION, "Por definir"), "TELEFONO", texto(s.TELEFONO, "Por definir"),
                    "ESTADO", estado));
            for (DocumentSnapshot u : usuarios) {
                tx.update(u.getReference(), fila("ESTADO", estado));
            }
            return null;
        });
    }

    /** "Mercado Sur" -> "mercado" (igual que en sucursal.html). */
    static String usuarioDe(String nombre) {
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().split("\\s+")[0].toLowerCase();
    }

    private static String texto(String v, String porDefecto) {
        return v == null || v.trim().isEmpty() ? porDefecto : v.trim().substring(0, Math.min(200, v.trim().length()));
    }
}
