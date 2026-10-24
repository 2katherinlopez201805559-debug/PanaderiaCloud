package BACKEND.manager;

import BACKEND.utils.FirebaseConfig;
import BACKEND.utils.ReglaNegocio;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase base de todos los managers (reemplaza a PanaderiaBaseManager de Oracle).
 *
 * En Firestore no hay tablas sino COLECCIONES de documentos:
 *   insumos, productos (con su RECETA adentro), sucursales, usuarios,
 *   pedidos (con su DETALLE adentro), devoluciones, pagos y contadores.
 * Cada documento usa como ID el número de su registro ("1", "2", ...)
 * y guarda los campos con los mismos nombres que tenían las columnas
 * en Oracle (ID_PRODUCTO, NOMBRE, PRECIO...), así el frontend no cambia.
 *
 * Las reglas que antes estaban en los paquetes PL/SQL ahora están en los
 * managers, y lo que antes era COMMIT/ROLLBACK ahora es una TRANSACCIÓN
 * de Firestore (db.runTransaction): o se guarda todo, o no se guarda nada.
 */
public abstract class FirestoreBase {

    // Nombres de las colecciones
    protected static final String INSUMOS = "insumos";
    protected static final String PRODUCTOS = "productos";
    protected static final String SUCURSALES = "sucursales";
    protected static final String USUARIOS = "usuarios";
    protected static final String PEDIDOS = "pedidos";
    protected static final String DEVOLUCIONES = "devoluciones";
    protected static final String PAGOS = "pagos";
    protected static final String CONTADORES = "contadores";

    protected static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    protected Firestore db() throws Exception {
        return FirebaseConfig.db();
    }

    protected CollectionReference col(String coleccion) throws Exception {
        return db().collection(coleccion);
    }

    protected DocumentReference doc(String coleccion, Object id) throws Exception {
        return db().collection(coleccion).document(String.valueOf(id));
    }

    /** Ejecuta una transacción y espera el resultado (como un bloque PL/SQL con COMMIT/ROLLBACK). */
    protected <T> T transaccion(Transaction.Function<T> trabajo) throws Exception {
        return db().runTransaction(trabajo).get();
    }

    /** Siguiente número para una colección (lo que hacía GENERATED AS IDENTITY). Solo dentro de una transacción. */
    protected long siguienteId(Transaction tx, String coleccion) throws Exception {
        DocumentReference ref = doc(CONTADORES, coleccion);
        DocumentSnapshot snap = tx.get(ref).get();
        long valor = snap.exists() && snap.get("valor") != null ? num(snap.get("valor")).longValue() : 0L;
        return valor + 1;
    }

    /** Guarda el nuevo valor del contador (se llama en la parte de escrituras de la transacción). */
    protected void guardarContador(Transaction tx, String coleccion, long valor) throws Exception {
        Map<String, Object> datos = new HashMap<String, Object>();
        datos.put("valor", valor);
        tx.set(doc(CONTADORES, coleccion), datos);
    }

    /** Todos los documentos de una colección como lista de filas, ordenados por un campo numérico. */
    protected List<Map<String, Object>> todos(String coleccion, String campoOrden) throws Exception {
        return ordenar(filas(col(coleccion).get().get()), campoOrden, false);
    }

    protected List<Map<String, Object>> filas(QuerySnapshot snap) {
        List<Map<String, Object>> salida = new ArrayList<Map<String, Object>>();
        for (QueryDocumentSnapshot d : snap.getDocuments()) {
            salida.add(new LinkedHashMap<String, Object>(d.getData()));
        }
        return salida;
    }

    /** Ordena por un campo numérico (desc = de mayor a menor). */
    protected List<Map<String, Object>> ordenar(List<Map<String, Object>> filas, final String campo, boolean desc) {
        Comparator<Map<String, Object>> c = new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return Double.compare(num(a.get(campo)).doubleValue(), num(b.get(campo)).doubleValue());
            }
        };
        Collections.sort(filas, desc ? Collections.reverseOrder(c) : c);
        return filas;
    }

    /** Copia de la fila sin los campos indicados (ej. sin RECETA o DETALLE). */
    protected Map<String, Object> sin(Map<String, Object> fila, String... campos) {
        Map<String, Object> copia = new LinkedHashMap<String, Object>(fila);
        for (String c : campos) {
            copia.remove(c);
        }
        return copia;
    }

    /* ---------------- Números ---------------- */

    /** Firestore devuelve Long o Double: aquí se convierte a número sin importar cuál llegó. */
    protected static BigDecimal num(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal) {
            return (BigDecimal) valor;
        }
        return new BigDecimal(valor.toString());
    }

    protected static double d(Object valor) {
        return num(valor).doubleValue();
    }

    /** Redondea (3 decimales para insumos, 2 para dinero) para no arrastrar errores de punto flotante. */
    protected static double redondear(double valor, int decimales) {
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_UP).doubleValue();
    }

    /** 20.0 -> "20" ; 0.35 -> "0.35" (para los mensajes de error). */
    protected static String txt(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
    }

    protected static String dinero(double valor) {
        return "Q " + BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /* ---------------- Fechas (hora de Guatemala) ---------------- */

    protected static String ahora() {
        return LocalDateTime.now(GUATEMALA).format(FECHA_HORA);
    }

    protected static LocalDate hoy() {
        return LocalDate.now(GUATEMALA);
    }

    /** "2026-10-02" -> LocalDate ; vacío -> null */
    protected static LocalDate fecha(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocio(20010, "La fecha debe tener formato AAAA-MM-DD.");
        }
    }

    /** Nombre del usuario por su ID (para guardar quién registró). Solo lectura, dentro de una transacción. */
    protected String nombreUsuario(Transaction tx, int idUsuario) throws Exception {
        for (QueryDocumentSnapshot u : tx.get(col(USUARIOS).whereEqualTo("ID_USUARIO", (long) idUsuario)).get().getDocuments()) {
            return u.getString("NOMBRE");
        }
        return "Usuario " + idUsuario;
    }

    protected static Map<String, Object> fila(Object... claveValor) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < claveValor.length; i += 2) {
            m.put((String) claveValor[i], claveValor[i + 1]);
        }
        return m;
    }
}
