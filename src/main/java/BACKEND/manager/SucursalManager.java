package BACKEND.manager;

import java.util.List;
import java.util.Map;

/** Sucursales (antes PKG_PANADERIA_WEB.SP_LISTAR_SUCURSALES). */
public class SucursalManager extends FirestoreBase {

    public List<Map<String, Object>> ObtenerSucursales() throws Exception {
        return todos(SUCURSALES, "ID_SUCURSAL");
    }
}
