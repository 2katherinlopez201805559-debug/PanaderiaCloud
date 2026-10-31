package BACKEND.manager;

import BACKEND.utils.Ajustes;
import BACKEND.utils.ReglaNegocio;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reportes (antes V_ALERTA_STOCK, SP_LISTAR_ALERTAS y SP_RESUMEN_PRODUCCION) y, además:
 *  - Estadisticas: panes más vendidos y sucursal que más vende.
 *  - CapacidadProduccion: cuántos panes alcanzan con los insumos y qué insumo lo limita.
 *  - PanesPorVencer: pan en existencia que se vence hoy o ya se venció (según sus lotes).
 */
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

    /* =====================================================================
       ESTADÍSTICAS DE VENTAS
       Venta = pedido DESPACHADO o ENTREGADO (el pan ya salió de la panadería),
       filtrado por FECHA_ENTREGA. Se descuentan las devoluciones APROBADAS.
       ===================================================================== */
    public Map<String, Object> Estadisticas(String desdeTxt, String hastaTxt) throws Exception {
        LocalDate desde = fecha(desdeTxt), hasta = fecha(hastaTxt);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaNegocio(20010, "La fecha inicial no puede ser mayor que la final.");
        }
        Map<Long, Map<String, Object>> panes = new LinkedHashMap<Long, Map<String, Object>>();
        Map<Long, Map<String, Object>> sucursales = new LinkedHashMap<Long, Map<String, Object>>();
        Map<Long, Map<Long, Double>> panPorSucursal = new HashMap<Long, Map<Long, Double>>();
        Map<Long, Long> sucursalDePedido = new HashMap<Long, Long>();
        Map<Long, Map<String, Object>> ventas = new LinkedHashMap<Long, Map<String, Object>>();   // registro de ventas
        int pedidos = 0;

        for (Map<String, Object> p : filas(col(PEDIDOS).get().get())) {
            String estado = String.valueOf(p.get("ESTADO"));
            if ((!"DESPACHADO".equals(estado) && !"ENTREGADO".equals(estado)) || !enRango(p.get("FECHA_ENTREGA"), desde, hasta)) {
                continue;
            }
            pedidos++;
            long idSuc = num(p.get("ID_SUCURSAL")).longValue();
            long idPedido = num(p.get("ID_PEDIDO")).longValue();
            sucursalDePedido.put(idPedido, idSuc);
            ventas.put(idPedido, fila("ID_PEDIDO", idPedido, "FECHA_ENTREGA", p.get("FECHA_ENTREGA"),
                    "SUCURSAL", p.get("SUCURSAL"), "ESTADO", estado, "TOTAL", d(p.get("TOTAL")), "DEVOLUCIONES", 0.0,
                    "NETO", d(p.get("TOTAL"))));
            Map<String, Object> s = acumulador(sucursales, idSuc, "ID_SUCURSAL", "SUCURSAL", p.get("SUCURSAL"));
            s.put("PEDIDOS", num(s.get("PEDIDOS")).intValue() + 1);
            Object det = p.get("DETALLE");
            if (!(det instanceof List)) {
                continue;
            }
            for (Object o : (List<?>) det) {
                Map<?, ?> l = (Map<?, ?>) o;
                long idPan = num(l.get("ID_PRODUCTO")).longValue();
                double cant = d(l.get("CANTIDAD")), monto = d(l.get("SUBTOTAL"));
                sumarVenta(acumulador(panes, idPan, "ID_PRODUCTO", "NOMBRE", l.get("PRODUCTO")), cant, monto);
                sumarVenta(s, cant, monto);
                Map<Long, Double> m = panPorSucursal.get(idSuc);
                if (m == null) {
                    m = new HashMap<Long, Double>();
                    panPorSucursal.put(idSuc, m);
                }
                m.put(idPan, valor(m, idPan) + cant);
            }
        }
        // Devoluciones aprobadas de esos pedidos: se restan de lo vendido
        for (Map<String, Object> dv : filas(col(DEVOLUCIONES).whereEqualTo("ESTADO", "APROBADA").get().get())) {
            long idPedidoDev = num(dv.get("ID_PEDIDO")).longValue();
            Long idSuc = sucursalDePedido.get(idPedidoDev);
            if (idSuc == null) {
                continue;
            }
            Map<String, Object> venta = ventas.get(idPedidoDev);
            venta.put("DEVOLUCIONES", redondear(d(venta.get("DEVOLUCIONES")) + d(dv.get("MONTO")), 2));
            venta.put("NETO", redondear(d(venta.get("TOTAL")) - d(venta.get("DEVOLUCIONES")), 2));
            long idPan = num(dv.get("ID_PRODUCTO")).longValue();
            double cant = d(dv.get("CANTIDAD")), monto = d(dv.get("MONTO"));
            sumarDevolucion(acumulador(panes, idPan, "ID_PRODUCTO", "NOMBRE", dv.get("PRODUCTO")), cant, monto);
            sumarDevolucion(sucursales.get(idSuc), cant, monto);
            Map<Long, Double> m = panPorSucursal.get(idSuc);
            if (m != null) {
                m.put(idPan, valor(m, idPan) - cant);
            }
        }

        List<Map<String, Object>> listaPanes = cerrar(panes.values());
        List<Map<String, Object>> listaSuc = cerrar(sucursales.values());
        for (Map<String, Object> s : listaSuc) {             // pan estrella de cada sucursal
            Map<Long, Double> m = panPorSucursal.get(num(s.get("ID_SUCURSAL")).longValue());
            long mejor = -1;
            double max = 0;
            if (m != null) {
                for (Map.Entry<Long, Double> e : m.entrySet()) {
                    if (e.getValue() > max) {
                        max = e.getValue();
                        mejor = e.getKey();
                    }
                }
            }
            s.put("PAN_TOP", mejor < 0 || !panes.containsKey(mejor) ? null : panes.get(mejor).get("NOMBRE"));
            s.put("PAN_TOP_CANTIDAD", redondear(max, 2));
        }
        double total = 0, unidades = 0;
        for (Map<String, Object> pn : listaPanes) {
            total += d(pn.get("NETO_MONTO"));
            unidades += d(pn.get("NETO_CANTIDAD"));
        }
        // Compras de insumos en el mismo período (para comparar gastos contra ventas)
        double compras = 0;
        int numCompras = 0;
        for (Map<String, Object> c : filas(col(COMPRAS).get().get())) {
            String f = String.valueOf(c.get("FECHA"));
            if (enRango(f.length() >= 10 ? f.substring(0, 10) : f, desde, hasta)) {
                compras += d(c.get("TOTAL"));
                numCompras++;
            }
        }
        List<Map<String, Object>> listaVentas = ordenar(new ArrayList<Map<String, Object>>(ventas.values()), "ID_PEDIDO", true);

        Map<String, Object> salida = new LinkedHashMap<String, Object>();
        salida.put("DESDE", desde == null ? null : desde.toString());
        salida.put("HASTA", hasta == null ? null : hasta.toString());
        salida.put("PEDIDOS", pedidos);
        salida.put("TOTAL_VENDIDO", redondear(total, 2));
        salida.put("PANES_VENDIDOS", redondear(unidades, 2));
        salida.put("PAN_TOP", listaPanes.isEmpty() ? null : listaPanes.get(0));
        salida.put("SUCURSAL_TOP", listaSuc.isEmpty() ? null : listaSuc.get(0));
        salida.put("PANES", listaPanes);
        salida.put("SUCURSALES", listaSuc);
        salida.put("VENTAS", listaVentas);
        salida.put("COMPRAS_TOTAL", redondear(compras, 2));
        salida.put("COMPRAS", numCompras);
        salida.put("DIFERENCIA", redondear(total - compras, 2));
        return salida;
    }

    private static boolean enRango(Object fechaEntrega, LocalDate desde, LocalDate hasta) {
        if (fechaEntrega == null) {
            return desde == null && hasta == null;
        }
        String f = fechaEntrega.toString();
        return (desde == null || f.compareTo(desde.toString()) >= 0) && (hasta == null || f.compareTo(hasta.toString()) <= 0);
    }

    private Map<String, Object> acumulador(Map<Long, Map<String, Object>> m, long id, String campoId, String campoNombre,
            Object nombre) {
        Map<String, Object> a = m.get(id);
        if (a == null) {
            a = new LinkedHashMap<String, Object>();
            a.put(campoId, id);
            a.put(campoNombre, nombre);
            a.put("PEDIDOS", 0);
            a.put("CANTIDAD", 0.0);
            a.put("MONTO", 0.0);
            a.put("DEVUELTO_CANTIDAD", 0.0);
            a.put("DEVUELTO_MONTO", 0.0);
            m.put(id, a);
        }
        return a;
    }

    private static void sumarVenta(Map<String, Object> a, double cant, double monto) {
        a.put("CANTIDAD", d(a.get("CANTIDAD")) + cant);
        a.put("MONTO", d(a.get("MONTO")) + monto);
    }

    private static void sumarDevolucion(Map<String, Object> a, double cant, double monto) {
        if (a != null) {
            a.put("DEVUELTO_CANTIDAD", d(a.get("DEVUELTO_CANTIDAD")) + cant);
            a.put("DEVUELTO_MONTO", d(a.get("DEVUELTO_MONTO")) + monto);
        }
    }

    /** Calcula netos, redondea y ordena de mayor a menor venta neta. */
    private List<Map<String, Object>> cerrar(java.util.Collection<Map<String, Object>> filas) {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> a : filas) {
            for (String c : new String[]{"CANTIDAD", "MONTO", "DEVUELTO_CANTIDAD", "DEVUELTO_MONTO"}) {
                a.put(c, redondear(d(a.get(c)), 2));
            }
            a.put("NETO_CANTIDAD", redondear(d(a.get("CANTIDAD")) - d(a.get("DEVUELTO_CANTIDAD")), 2));
            a.put("NETO_MONTO", redondear(d(a.get("MONTO")) - d(a.get("DEVUELTO_MONTO")), 2));
            salida.add(a);
        }
        Collections.sort(salida, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                int c = Double.compare(d(b.get("NETO_CANTIDAD")), d(a.get("NETO_CANTIDAD")));
                return c != 0 ? c : Double.compare(d(b.get("NETO_MONTO")), d(a.get("NETO_MONTO")));
            }
        });
        return salida;
    }

    private static double valor(Map<Long, Double> m, long k) {
        Double v = m.get(k);
        return v == null ? 0 : v;
    }

    /* =====================================================================
       CAPACIDAD DE PRODUCCIÓN: con los insumos de hoy, ¿cuántos panes alcanzan?
       ===================================================================== */
    public List<Map<String, Object>> CapacidadProduccion() throws Exception {
        Map<Long, Map<String, Object>> insumos = new HashMap<Long, Map<String, Object>>();
        for (Map<String, Object> i : todos(INSUMOS, "ID_INSUMO")) {
            insumos.put(num(i.get("ID_INSUMO")).longValue(), i);
        }
        Map<Long, Double> falta = new HashMap<Long, Double>();
        for (Map<String, Object> r : ObtenerResumenProduccion(null)) {
            falta.put(num(r.get("ID_PRODUCTO")).longValue(), d(r.get("FALTA_PRODUCIR")));
        }
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> pan : todos(PRODUCTOS, "ID_PRODUCTO")) {
            if (!"ACTIVO".equals(pan.get("ESTADO")) || !(pan.get("RECETA") instanceof List)) {
                continue;
            }
            double alcanza = Double.MAX_VALUE;
            String limitante = null;
            for (Object o : (List<?>) pan.get("RECETA")) {
                Map<?, ?> r = (Map<?, ?>) o;
                Map<String, Object> ins = insumos.get(num(r.get("ID_INSUMO")).longValue());
                double porPan = d(r.get("CANTIDAD"));
                if (porPan <= 0) {
                    continue;
                }
                double n = ins == null || !"ACTIVO".equals(ins.get("ESTADO")) ? 0
                        : Math.floor(d(ins.get("STOCK_ACTUAL")) / porPan + 1e-9);
                if (n < alcanza) {
                    alcanza = n;
                    limitante = ins == null ? "insumo " + r.get("ID_INSUMO") : String.valueOf(ins.get("NOMBRE"));
                }
            }
            if (alcanza == Double.MAX_VALUE) {
                continue;
            }
            long id = num(pan.get("ID_PRODUCTO")).longValue();
            double necesita = falta.containsKey(id) ? falta.get(id) : 0;
            double stock = d(pan.get("STOCK_ACTUAL")), minimo = d(pan.get("STOCK_MINIMO"));
            String estado = alcanza <= 0 ? "SIN_INSUMOS" : alcanza < necesita ? "NO_ALCANZA" : "OK";
            salida.add(fila("ID_PRODUCTO", id, "NOMBRE", pan.get("NOMBRE"), "STOCK_ACTUAL", stock, "STOCK_MINIMO", minimo,
                    "FALTA_PEDIDOS", necesita, "SE_PUEDEN_HACER", alcanza, "INSUMO_LIMITANTE", limitante,
                    "BAJO_MINIMO", stock <= minimo, "ESTADO_PRODUCCION", estado));
        }
        return salida;
    }

    /* =====================================================================
       PAN POR VENCER: según la fecha de cada lote y DIAS_VIDA_PAN
       VENCIDO     -> ya pasó su vida útil: retirarlo / no despacharlo
       VENCE_HOY   -> último día: venderlo o despacharlo hoy
       ===================================================================== */
    public List<Map<String, Object>> PanesPorVencer() throws Exception {
        int vida = Ajustes.diasVidaPan();
        LocalDate hoy = hoy();
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> pan : todos(PRODUCTOS, "ID_PRODUCTO")) {
            for (Map<String, Object> lote : Lotes.leer(pan)) {
                LocalDate f;
                try {
                    f = LocalDate.parse(String.valueOf(lote.get("FECHA")));
                } catch (Exception e) {
                    continue;
                }
                long edad = ChronoUnit.DAYS.between(f, hoy);
                String estado = edad >= vida ? "VENCIDO" : edad == vida - 1 ? "VENCE_HOY" : null;
                if (estado != null) {
                    salida.add(fila("ID_PRODUCTO", pan.get("ID_PRODUCTO"), "NOMBRE", pan.get("NOMBRE"),
                            "FECHA_PRODUCCION", f.toString(), "DIAS", edad, "CANTIDAD", Lotes.cantidad(lote),
                            "VENCE", f.plusDays(vida - 1).toString(), "ESTADO", estado));
                }
            }
        }
        return salida;
    }
}
