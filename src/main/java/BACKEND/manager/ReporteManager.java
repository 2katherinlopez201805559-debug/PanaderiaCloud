package BACKEND.manager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reportes (antes V_ALERTA_STOCK, SP_LISTAR_ALERTAS y SP_RESUMEN_PRODUCCION). */
public class ReporteManager extends FirestoreBase {

    /** Panes e insumos ACTIVOS en o bajo su existencia mínima. */
    public List<Map<String, Object>> ObtenerAlertas() throws Exception {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> i : todos(INSUMOS, "ID_INSUMO")) {
            agregarSiBajo(salida, "INSUMO", i.get("ID_INSUMO"), i);
        }
        for (Map<String, Object> p : todos(PRODUCTOS, "ID_PRODUCTO")) {
            agregarSiBajo(salida, "PRODUCTO", p.get("ID_PRODUCTO"), p);
        }
        Collections.sort(salida, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                int t = String.valueOf(a.get("TIPO")).compareTo(String.valueOf(b.get("TIPO")));
                return t != 0 ? t : String.valueOf(a.get("NOMBRE")).compareTo(String.valueOf(b.get("NOMBRE")));
            }
        });
        return salida;
    }

    private void agregarSiBajo(List<Map<String, Object>> salida, String tipo, Object id, Map<String, Object> x) {
        if ("ACTIVO".equals(x.get("ESTADO")) && d(x.get("STOCK_ACTUAL")) <= d(x.get("STOCK_MINIMO"))) {
            salida.add(fila("TIPO", tipo, "ID", id, "NOMBRE", x.get("NOMBRE"), "UNIDAD_MEDIDA", x.get("UNIDAD_MEDIDA"),
                    "STOCK_ACTUAL", x.get("STOCK_ACTUAL"), "STOCK_MINIMO", x.get("STOCK_MINIMO")));
        }
    }

    /** Total pedido por pan (pedidos PENDIENTES de esa fecha) vs. stock: cuánto falta producir. */
    public List<Map<String, Object>> ObtenerResumenProduccion(String fechaEntrega) throws Exception {
        LocalDate f = fecha(fechaEntrega);
        Map<Long, Double> pedido = new HashMap<Long, Double>();
        for (Map<String, Object> p : filas(col(PEDIDOS).whereEqualTo("ESTADO", "PENDIENTE").get().get())) {
            if (f != null && !f.toString().equals(p.get("FECHA_ENTREGA"))) {
                continue;
            }
            Object det = p.get("DETALLE");
            if (det instanceof List) {
                for (Object o : (List<?>) det) {
                    Map<?, ?> linea = (Map<?, ?>) o;
                    long idPan = num(linea.get("ID_PRODUCTO")).longValue();
                    Double previo = pedido.get(idPan);
                    pedido.put(idPan, (previo == null ? 0 : previo) + d(linea.get("CANTIDAD")));
                }
            }
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> pan : todos(PRODUCTOS, "ID_PRODUCTO")) {
            if (!"ACTIVO".equals(pan.get("ESTADO"))) {
                continue;
            }
            long id = num(pan.get("ID_PRODUCTO")).longValue();
            double total = pedido.containsKey(id) ? pedido.get(id) : 0;
            double stock = d(pan.get("STOCK_ACTUAL"));
            salida.add(fila("ID_PRODUCTO", id, "NOMBRE", pan.get("NOMBRE"), "TOTAL_PEDIDO", total,
                    "STOCK_ACTUAL", stock, "FALTA_PRODUCIR", Math.max(total - stock, 0)));
        }
        return salida;
    }
}
