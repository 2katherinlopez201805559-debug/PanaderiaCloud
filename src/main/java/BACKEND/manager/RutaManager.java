package BACKEND.manager;

import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vendedores en ruta (unidades de reparto).
 *
 *  1. El gerente DESPACHA el pedido y lo ASIGNA a una unidad (usuario con rol VENDEDOR).
 *  2. La unidad ve sus pedidos en el teléfono (ruta.html) y:
 *       - marca ENTREGADO, o
 *       - REPORTA UN PROBLEMA en ruta (vehículo, accidente, producto, sucursal cerrada, otro).
 *  3. El problema queda ABIERTO, aparece en la campanita y llega por Telegram al gerente.
 *  4. El gerente lo RESUELVE: reasigna el pedido a OTRA unidad (que ya lo ve en su app)
 *     o lo deja con la misma unidad con una nota.
 *
 * Campos que se agregan al pedido: ID_UNIDAD, UNIDAD, ID_INCIDENTE (problema abierto o null).
 */
public class RutaManager extends FirestoreBase {

    public static final List<String> TIPOS = Arrays.asList("VEHICULO", "ACCIDENTE", "PRODUCTO", "SUCURSAL_CERRADA", "OTRO");

    /* =====================================================================
       UNIDADES
       ===================================================================== */
    public List<Map<String, Object>> ObtenerUnidades() throws Exception {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> enRuta = PedidosEnRuta();
        for (Map<String, Object> u : filas(col(USUARIOS).whereEqualTo("ROL", "VENDEDOR").get().get())) {
            long id = num(u.get("ID_USUARIO")).longValue();
            int pedidos = 0, problemas = 0;
            for (Map<String, Object> p : enRuta) {
                if (p.get("ID_UNIDAD") != null && num(p.get("ID_UNIDAD")).longValue() == id) {
                    pedidos++;
                    if (p.get("ID_INCIDENTE") != null) {
                        problemas++;
                    }
                }
            }
            salida.add(fila("ID_UNIDAD", id, "USUARIO", u.get("USUARIO"), "NOMBRE", u.get("NOMBRE"),
                    "ESTADO", u.get("ESTADO"), "PEDIDOS_EN_RUTA", pedidos, "PROBLEMAS", problemas));
        }
        return ordenar(salida, "ID_UNIDAD", false);
    }

    /** Pedidos DESPACHADOS (en camino), con su unidad y si tienen un problema abierto. */
    public List<Map<String, Object>> PedidosEnRuta() throws Exception {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : filas(col(PEDIDOS).whereEqualTo("ESTADO", "DESPACHADO").get().get())) {
            salida.add(sin(p, "DETALLE"));
        }
        return ordenar(salida, "ID_PEDIDO", true);
    }

    /* =====================================================================
       GERENTE: asignar unidad y resolver problemas
       ===================================================================== */
    public void AsignarUnidad(final int idPedido, final int idUnidad) throws Exception {
        final DocumentReference ref = doc(PEDIDOS, idPedido);
        transaccion(tx -> {
            DocumentSnapshot p = tx.get(ref).get();
            if (!p.exists()) {
                throw new ReglaNegocio(20004, "El pedido " + idPedido + " no existe.");
            }
            if (!"DESPACHADO".equals(p.getString("ESTADO"))) {
                throw new ReglaNegocio(20026, "Solo se asigna una unidad a pedidos DESPACHADOS (este está "
                        + p.getString("ESTADO") + ").");
            }
            Map<String, Object> unidad = unidad(tx, idUnidad);
            tx.update(ref, fila("ID_UNIDAD", (long) idUnidad, "UNIDAD", unidad.get("NOMBRE"), "FECHA_ASIGNACION", ahora()));
            return null;
        });
    }

    /**
     * Resuelve un problema. idUnidadNueva != null → el pedido pasa a esa unidad (otra unidad lo despacha).
     * idUnidadNueva == null → sigue con la misma unidad.
     */
    public String ResolverIncidente(final int idIncidente, final Integer idUnidadNueva, final String nota, final int idGerente)
            throws Exception {
        final DocumentReference refInc = doc(INCIDENTES, idIncidente);
        return transaccion(tx -> {
            DocumentSnapshot inc = tx.get(refInc).get();
            if (!inc.exists()) {
                throw new ReglaNegocio(20027, "El reporte " + idIncidente + " no existe.");
            }
            if (!"ABIERTO".equals(inc.getString("ESTADO"))) {
                throw new ReglaNegocio(20027, "El reporte " + idIncidente + " ya fue resuelto.");
            }
            long idPedido = num(inc.get("ID_PEDIDO")).longValue();
            DocumentReference refPed = doc(PEDIDOS, idPedido);
            DocumentSnapshot p = tx.get(refPed).get();
            Map<String, Object> nueva = null;
            if (idUnidadNueva != null) {
                if (inc.get("ID_UNIDAD") != null && num(inc.get("ID_UNIDAD")).intValue() == idUnidadNueva) {
                    throw new ReglaNegocio(20026, "Elija una unidad distinta a la que reportó el problema.");
                }
                nueva = unidad(tx, idUnidadNueva);
            }
            String resuelve = nombreUsuario(tx, idGerente);
            String texto = nota == null || nota.trim().isEmpty() ? null : nota.trim().substring(0, Math.min(250, nota.trim().length()));

            // ESCRITURAS
            Map<String, Object> cambios = fila("ESTADO", "RESUELTO", "FECHA_RESOLUCION", ahora(), "RESUELTO_POR", resuelve,
                    "NOTA", texto, "ID_UNIDAD_NUEVA", nueva == null ? null : (Object) (long) idUnidadNueva,
                    "UNIDAD_NUEVA", nueva == null ? null : nueva.get("NOMBRE"));
            tx.update(refInc, cambios);
            if (p.exists()) {
                Map<String, Object> ped = fila("ID_INCIDENTE", null);
                if (nueva != null && "DESPACHADO".equals(p.getString("ESTADO"))) {
                    ped.put("ID_UNIDAD", (long) idUnidadNueva);
                    ped.put("UNIDAD", nueva.get("NOMBRE"));
                    ped.put("FECHA_ASIGNACION", ahora());
                }
                tx.update(refPed, ped);
            }
            return nueva == null ? "Reporte resuelto; el pedido sigue con la misma unidad."
                    : "Pedido #" + idPedido + " reasignado a " + nueva.get("NOMBRE") + ".";
        });
    }

    public List<Map<String, Object>> ObtenerIncidentes(String estado, Integer idUnidad) throws Exception {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> i : filas(col(INCIDENTES).get().get())) {
            if (estado != null && !estado.equals(i.get("ESTADO"))) {
                continue;
            }
            if (idUnidad != null && (i.get("ID_UNIDAD") == null || num(i.get("ID_UNIDAD")).intValue() != idUnidad)) {
                continue;
            }
            salida.add(i);
        }
        return ordenar(salida, "ID_INCIDENTE", true);
    }

    /* =====================================================================
       UNIDAD DE REPARTO (app del vendedor)
       ===================================================================== */
    /** Pedidos asignados a la unidad que siguen en ruta, con sus panes y los datos de la sucursal. */
    public List<Map<String, Object>> MisPedidos(int idUnidad) throws Exception {
        Map<Long, Map<String, Object>> sucursales = new LinkedHashMap<Long, Map<String, Object>>();
        for (Map<String, Object> s : todos(SUCURSALES, "ID_SUCURSAL")) {
            sucursales.put(num(s.get("ID_SUCURSAL")).longValue(), s);
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> p : filas(col(PEDIDOS).whereEqualTo("ID_UNIDAD", (long) idUnidad).get().get())) {
            if (!"DESPACHADO".equals(p.get("ESTADO"))) {
                continue;
            }
            Map<String, Object> s = sucursales.get(num(p.get("ID_SUCURSAL")).longValue());
            p.put("DIRECCION", s == null ? null : s.get("DIRECCION"));
            p.put("TELEFONO", s == null ? null : s.get("TELEFONO"));
            salida.add(p);
        }
        return ordenar(salida, "ID_PEDIDO", false);
    }

    /** La unidad entrega el pedido. Si tenía un problema abierto, queda resuelto. */
    public void Entregar(final int idPedido, final int idUnidad) throws Exception {
        final DocumentReference ref = doc(PEDIDOS, idPedido);
        transaccion(tx -> {
            DocumentSnapshot p = pedidoDeLaUnidad(tx, ref, idPedido, idUnidad);
            DocumentReference refInc = p.get("ID_INCIDENTE") == null ? null
                    : doc(INCIDENTES, num(p.get("ID_INCIDENTE")).longValue());
            DocumentSnapshot inc = refInc == null ? null : tx.get(refInc).get();
            // ESCRITURAS
            tx.update(ref, fila("ESTADO", "ENTREGADO", "FECHA_ENTREGADO", ahora(), "ID_INCIDENTE", null));
            if (inc != null && inc.exists() && "ABIERTO".equals(inc.getString("ESTADO"))) {
                tx.update(refInc, fila("ESTADO", "RESUELTO", "FECHA_RESOLUCION", ahora(),
                        "NOTA", "Entregado por la misma unidad."));
            }
            return null;
        });
    }

    /** La unidad reporta un problema en ruta. Devuelve el ID del reporte. */
    public int ReportarProblema(final int idPedido, final int idUnidad, final String tipo, final String descripcion)
            throws Exception {
        final String t = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!TIPOS.contains(t)) {
            throw new ReglaNegocio(20028, "Elija el tipo de problema.");
        }
        final String d = descripcion == null ? "" : descripcion.trim();
        if (d.length() < 5) {
            throw new ReglaNegocio(20028, "Describa el problema (mínimo 5 letras).");
        }
        final DocumentReference ref = doc(PEDIDOS, idPedido);
        long id = transaccion(tx -> {
            DocumentSnapshot p = pedidoDeLaUnidad(tx, ref, idPedido, idUnidad);
            if (p.get("ID_INCIDENTE") != null) {
                throw new ReglaNegocio(20028, "Este pedido ya tiene un problema reportado; gerencia lo está revisando.");
            }
            String nombre = nombreUsuario(tx, idUnidad);
            long nuevoId = siguienteId(tx, INCIDENTES);
            // ESCRITURAS
            Map<String, Object> inc = new LinkedHashMap<String, Object>();
            inc.put("ID_INCIDENTE", nuevoId);
            inc.put("ID_PEDIDO", (long) idPedido);
            inc.put("ID_SUCURSAL", p.get("ID_SUCURSAL"));
            inc.put("SUCURSAL", p.getString("SUCURSAL"));
            inc.put("ID_UNIDAD", (long) idUnidad);
            inc.put("UNIDAD", nombre);
            inc.put("TIPO", t);
            inc.put("DESCRIPCION", d.substring(0, Math.min(300, d.length())));
            inc.put("ESTADO", "ABIERTO");
            inc.put("FECHA", ahora());
            tx.set(doc(INCIDENTES, nuevoId), inc);
            guardarContador(tx, INCIDENTES, nuevoId);
            tx.update(ref, fila("ID_INCIDENTE", nuevoId));
            return nuevoId;
        });
        Notificador.revisarEnSegundoPlano();   // campanita + Telegram al gerente
        return (int) id;
    }

    /* ---------------- Ayudas ---------------- */

    private DocumentSnapshot pedidoDeLaUnidad(com.google.cloud.firestore.Transaction tx, DocumentReference ref,
            int idPedido, int idUnidad) throws Exception {
        DocumentSnapshot p = tx.get(ref).get();
        if (!p.exists()) {
            throw new ReglaNegocio(20004, "El pedido " + idPedido + " no existe.");
        }
        if (p.get("ID_UNIDAD") == null || num(p.get("ID_UNIDAD")).intValue() != idUnidad) {
            throw new ReglaNegocio(20026, "El pedido " + idPedido + " no está asignado a su unidad.");
        }
        if (!"DESPACHADO".equals(p.getString("ESTADO"))) {
            throw new ReglaNegocio(20026, "El pedido " + idPedido + " ya no está en ruta (está " + p.getString("ESTADO") + ").");
        }
        return p;
    }

    private Map<String, Object> unidad(com.google.cloud.firestore.Transaction tx, int idUnidad) throws Exception {
        for (QueryDocumentSnapshot u : tx.get(col(USUARIOS).whereEqualTo("ID_USUARIO", (long) idUnidad)).get().getDocuments()) {
            if (!"VENDEDOR".equals(u.getString("ROL"))) {
                break;
            }
            if (!"ACTIVO".equals(u.getString("ESTADO"))) {
                throw new ReglaNegocio(20026, "La unidad " + u.getString("NOMBRE") + " está inactiva.");
            }
            return new LinkedHashMap<String, Object>(u.getData());
        }
        throw new ReglaNegocio(20026, "La unidad " + idUnidad + " no existe.");
    }
}
