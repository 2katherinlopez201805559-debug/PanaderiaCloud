package BACKEND.manager;

import BACKEND.utils.ReglaNegocio;
import BACKEND.utils.UsuarioSesion;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.HashMap;
import java.util.Map;

/** Inicio de sesión (antes PKG_SEGURIDAD.SP_LOGIN). */
public class AuthManager extends FirestoreBase {

    /** Valida usuario y contraseña: compara el SHA-256 de "usuario:clave" con CLAVE_HASH. */
    public UsuarioSesion Login(String usuario, String clave) throws Exception {
        String u = usuario == null ? "" : usuario.trim().toLowerCase();
        if (u.isEmpty() || clave == null) {
            throw new ReglaNegocio(20011, "Ingrese usuario y contraseña.");
        }
        DocumentReference ref = doc(USUARIOS, u);
        DocumentSnapshot snap = ref.get().get();
        // Mismo mensaje si falla el usuario o la clave (no revela cuál existe)
        if (!snap.exists()
                || !DatosIniciales.hash(u, clave).equals(snap.getString("CLAVE_HASH"))
                || !"ACTIVO".equals(snap.getString("ESTADO"))) {
            throw new ReglaNegocio(20011, "Usuario o contraseña incorrectos.");
        }
        Map<String, Object> acceso = new HashMap<String, Object>();
        acceso.put("ULTIMO_ACCESO", ahora());
        ref.update(acceso).get();

        Object suc = snap.get("ID_SUCURSAL");
        return new UsuarioSesion(
                num(snap.get("ID_USUARIO")).intValue(),
                u,
                snap.getString("NOMBRE"),
                snap.getString("ROL"),
                suc == null ? null : num(suc).intValue());
    }

    /** Nombre de la sucursal del usuario (para mostrarlo en pantalla). */
    public String NombreSucursal(Integer idSucursal) throws Exception {
        if (idSucursal == null) {
            return null;
        }
        DocumentSnapshot s = doc(SUCURSALES, idSucursal).get().get();
        return s.exists() ? s.getString("NOMBRE") : null;
    }
}
