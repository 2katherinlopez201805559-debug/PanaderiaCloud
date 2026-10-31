package BACKEND.manager;

import com.google.cloud.firestore.DocumentSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lotes de pan: cuándo se produjo cada parte del pan que hay en existencia.
 * Cada pan guarda adentro LOTES: [{FECHA: "2026-10-30", CANTIDAD: 50}, ...].
 *
 *  - Al PRODUCIR se agrega un lote con la fecha de hoy.
 *  - Al DESPACHAR se saca primero el pan más viejo (PEPS / FIFO).
 *  - Al aprobar una DEVOLUCIÓN que regresa al inventario se agrega como lote de hoy.
 *
 * El pan que ya existía antes de esta función no tiene fecha: se toma como el más
 * viejo y es lo primero que sale al despachar.
 */
final class Lotes {

    private Lotes() {
    }

    /** Lista de lotes del pan, ordenada del más viejo al más nuevo. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> leer(DocumentSnapshot pan) {
        Object v = pan.get("LOTES");
        return v instanceof List ? copiar((List<Map<String, Object>>) v) : new ArrayList<Map<String, Object>>();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> leer(Map<String, Object> pan) {
        Object v = pan.get("LOTES");
        return v instanceof List ? copiar((List<Map<String, Object>>) v) : new ArrayList<Map<String, Object>>();
    }

    /** Agrega pan con la fecha indicada (si ya hay un lote de esa fecha, lo suma). */
    static List<Map<String, Object>> agregar(List<Map<String, Object>> lotes, String fecha, double cantidad) {
        for (Map<String, Object> l : lotes) {
            if (fecha.equals(l.get("FECHA"))) {
                l.put("CANTIDAD", redondear(cantidad(l) + cantidad));
                return lotes;
            }
        }
        Map<String, Object> nuevo = new LinkedHashMap<String, Object>();
        nuevo.put("FECHA", fecha);
        nuevo.put("CANTIDAD", redondear(cantidad));
        lotes.add(nuevo);
        ordenar(lotes);
        return lotes;
    }

    /**
     * Saca pan empezando por el más viejo. stockAntes = existencia antes de sacar;
     * lo que no tiene lote (pan antiguo sin fecha) sale primero.
     */
    static List<Map<String, Object>> sacar(List<Map<String, Object>> lotes, double stockAntes, double cantidad) {
        double sinFecha = Math.max(stockAntes - total(lotes), 0);
        double falta = Math.max(cantidad - sinFecha, 0);
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> l : lotes) {
            double c = cantidad(l);
            if (falta >= c) {
                falta = redondear(falta - c);        // el lote se va completo
            } else {
                l.put("CANTIDAD", redondear(c - falta));
                falta = 0;
                salida.add(l);
            }
        }
        return salida;
    }

    static double total(List<Map<String, Object>> lotes) {
        double t = 0;
        for (Map<String, Object> l : lotes) {
            t += cantidad(l);
        }
        return redondear(t);
    }

    static double cantidad(Map<String, Object> lote) {
        Object c = lote.get("CANTIDAD");
        return c == null ? 0 : new BigDecimal(c.toString()).doubleValue();
    }

    private static List<Map<String, Object>> copiar(List<Map<String, Object>> origen) {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> l : origen) {
            if (l != null && l.get("FECHA") != null && cantidad(l) > 0) {
                salida.add(new LinkedHashMap<String, Object>(l));
            }
        }
        ordenar(salida);
        return salida;
    }

    private static void ordenar(List<Map<String, Object>> lotes) {
        Collections.sort(lotes, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return String.valueOf(a.get("FECHA")).compareTo(String.valueOf(b.get("FECHA")));
            }
        });
    }

    private static double redondear(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
