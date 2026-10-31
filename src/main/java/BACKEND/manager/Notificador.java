package BACKEND.manager;

import BACKEND.utils.Ajustes;
import BACKEND.utils.Correo;
import BACKEND.utils.Telegram;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisos al propietario.
 *
 * Revisa: insumos en su mínimo, pan que ya no se puede producir (o no alcanza para los
 * pedidos), pan bajo su mínimo y pan por vencer. Cada problema es un "aviso".
 *
 *  - 🔔 Campanita: el panel pide la lista de avisos (Avisos()) y la muestra arriba.
 *  - ✈️ Telegram (si TELEGRAM_BOT_TOKEN está configurado) y 📧 correo (si RESEND_API_KEY
 *    está configurado): se manda UN mensaje solo si hay un aviso NUEVO, o una vez al
 *    día como recordatorio mientras el problema siga. Lo enviado se guarda en
 *    Firestore: sistema/notificaciones.
 *
 * La revisión se hace en segundo plano después de producir, despachar o registrar un
 * pedido, y cada hora mientras el servidor está encendido.
 */
public final class Notificador extends FirestoreBase {

    private static final ExecutorService HILO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notificador");
        t.setDaemon(true);
        return t;
    });

    private Notificador() {
    }

    /* =====================================================================
       LISTA DE AVISOS (la usa la campanita y el mensaje)
       Cada aviso: {CLAVE, NIVEL (ALTO|MEDIO), ICONO, TITULO, DETALLE, ENLACE}
       ===================================================================== */
    public static List<Map<String, Object>> Avisos() throws Exception {
        ReporteManager r = new ReporteManager();
        List<Map<String, Object>> avisos = new ArrayList<Map<String, Object>>();

        for (Map<String, Object> a : r.ObtenerAlertas()) {
            if ("INSUMO".equals(a.get("TIPO"))) {
                avisos.add(aviso("I" + a.get("ID"), "ALTO", "🧂", "Comprar " + a.get("NOMBRE"),
                        "Hay " + txt(d(a.get("STOCK_ACTUAL"))) + " " + a.get("UNIDAD_MEDIDA") + " (mínimo "
                        + txt(d(a.get("STOCK_MINIMO"))) + ").", "panel.html#insumos"));
            }
        }
        for (Map<String, Object> c : r.CapacidadProduccion()) {
            String estado = String.valueOf(c.get("ESTADO_PRODUCCION"));
            if ("SIN_INSUMOS".equals(estado)) {
                avisos.add(aviso("C" + c.get("ID_PRODUCTO") + estado, "ALTO", "⛔", "No se puede producir " + c.get("NOMBRE"),
                        "Falta " + c.get("INSUMO_LIMITANTE") + ".", "panel.html#alertas"));
            } else if ("NO_ALCANZA".equals(estado)) {
                avisos.add(aviso("C" + c.get("ID_PRODUCTO") + estado, "ALTO", "⛔", "No alcanza para los pedidos de " + c.get("NOMBRE"),
                        "Piden " + txt(d(c.get("FALTA_PEDIDOS"))) + " y alcanza para " + txt(d(c.get("SE_PUEDEN_HACER")))
                        + " (limita: " + c.get("INSUMO_LIMITANTE") + ").", "panel.html#alertas"));
            }
            if (Boolean.TRUE.equals(c.get("BAJO_MINIMO")) || d(c.get("FALTA_PEDIDOS")) > 0) {
                String detalle = "Hay " + txt(d(c.get("STOCK_ACTUAL"))) + " (mínimo " + txt(d(c.get("STOCK_MINIMO"))) + ")"
                        + (d(c.get("FALTA_PEDIDOS")) > 0 ? ", faltan " + txt(d(c.get("FALTA_PEDIDOS"))) + " para pedidos" : "")
                        + ". Alcanzan los insumos para " + txt(d(c.get("SE_PUEDEN_HACER"))) + ".";
                avisos.add(aviso("P" + c.get("ID_PRODUCTO"), "MEDIO", "🍞", "Mandar a producir " + c.get("NOMBRE"), detalle,
                        "panel.html#produccion"));
            }
        }
        for (Map<String, Object> v : r.PanesPorVencer()) {
            boolean vencido = "VENCIDO".equals(v.get("ESTADO"));
            avisos.add(aviso("V" + v.get("ID_PRODUCTO") + v.get("FECHA_PRODUCCION") + v.get("ESTADO"), vencido ? "ALTO" : "MEDIO", "⏰",
                    (vencido ? "Pan vencido: " : "Vence hoy: ") + v.get("NOMBRE"),
                    txt(d(v.get("CANTIDAD"))) + " unidades producidas el " + v.get("FECHA_PRODUCCION")
                    + (vencido ? ". Retirarlo." : ". Despacharlo primero."), "panel.html#alertas"));
        }
        return avisos;
    }

    private static Map<String, Object> aviso(String clave, String nivel, String icono, String titulo, String detalle, String enlace) {
        Map<String, Object> a = new LinkedHashMap<String, Object>();
        a.put("CLAVE", clave);
        a.put("NIVEL", nivel);
        a.put("ICONO", icono);
        a.put("TITULO", titulo);
        a.put("DETALLE", detalle);
        a.put("ENLACE", enlace);
        return a;
    }

    /* =====================================================================
       ENVÍO (Telegram y/o correo)
       ===================================================================== */
    public static boolean hayCanal() {
        return Telegram.configurado() || Correo.configurado();
    }

    /** Programa una revisión sin hacer esperar a quien llamó. */
    public static void revisarEnSegundoPlano() {
        if (!hayCanal()) {
            return;
        }
        HILO.submit(() -> {
            try {
                revisarAhora(false);
            } catch (Exception e) {
                System.err.println("[Notificador] " + e.getMessage());
            }
        });
    }

    /** Revisa y envía si hace falta. forzar = enviar aunque ya se haya avisado hoy. Devuelve un mensaje. */
    public static String revisarAhora(boolean forzar) throws Exception {
        synchronized (Notificador.class) {        // una revisión a la vez (evita mensajes repetidos)
            return new Notificador().revisar(forzar);
        }
    }

    private String revisar(boolean forzar) throws Exception {
        if (!hayCanal()) {
            throw new IllegalStateException("No hay a dónde enviar: configure Telegram (TELEGRAM_BOT_TOKEN) en Render.");
        }
        List<Map<String, Object>> avisos = Avisos();
        TreeSet<String> firma = new TreeSet<String>();
        for (Map<String, Object> a : avisos) {
            firma.add(String.valueOf(a.get("CLAVE")));
        }
        if (firma.isEmpty() && !forzar) {
            return "Todo en orden: no hay avisos.";
        }

        DocumentReference ref = db().collection("sistema").document("notificaciones");
        DocumentSnapshot ultimo = ref.get().get();
        String hoy = hoy().toString();
        boolean yaHoy = ultimo.exists() && hoy.equals(ultimo.getString("FECHA"));
        List<?> anterior = ultimo.exists() && ultimo.get("FIRMA") instanceof List ? (List<?>) ultimo.get("FIRMA")
                : Collections.emptyList();
        if (!forzar && yaHoy && anterior.containsAll(firma)) {
            return "Ya se avisó hoy y no hay avisos nuevos.";
        }

        List<String> enviados = new ArrayList<String>(), errores = new ArrayList<String>();
        if (Telegram.configurado()) {
            try {
                String chat = chatTelegram();
                if (chat == null) {
                    throw new IllegalStateException("falta conectar el chat: el propietario escribe /start al bot y luego presione \"Conectar Telegram\"");
                }
                Telegram.enviar(chat, textoTelegram(avisos));
                enviados.add("Telegram");
            } catch (Exception e) {
                errores.add("Telegram: " + e.getMessage());
            }
        }
        if (Correo.configurado()) {
            try {
                Correo.enviar("Panadería San Carlos: " + avisos.size() + " aviso(s)", htmlCorreo(avisos));
                enviados.add("correo a " + Ajustes.correoPropietario());
            } catch (Exception e) {
                errores.add("Correo: " + e.getMessage());
            }
        }
        if (enviados.isEmpty()) {
            throw new IllegalStateException("No se pudo enviar. " + String.join(" | ", errores));
        }
        ref.set(fila("FECHA", hoy, "ENVIADO", ahora(), "FIRMA", new ArrayList<String>(firma), "CANALES", enviados));
        return "Aviso enviado por " + String.join(" y ", enviados) + (errores.isEmpty() ? "." : ". (" + String.join(" | ", errores) + ")");
    }

    /* =====================================================================
       Conectar el chat de Telegram del propietario
       ===================================================================== */
    public static String chatTelegram() throws Exception {
        if (Ajustes.telegramChatId() != null) {
            return Ajustes.telegramChatId();
        }
        DocumentSnapshot s = FirebaseConfigAcceso.db().collection("sistema").document("telegram").get().get();
        return s.exists() ? s.getString("CHAT_ID") : null;
    }

    public static Map<String, Object> EstadoTelegram() throws Exception {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("CONFIGURADO", Telegram.configurado());
        String chat = Telegram.configurado() ? chatTelegram() : null;
        r.put("CONECTADO", chat != null);
        if (chat != null && Ajustes.telegramChatId() == null) {
            DocumentSnapshot s = FirebaseConfigAcceso.db().collection("sistema").document("telegram").get().get();
            r.put("NOMBRE", s.getString("NOMBRE"));
        }
        return r;
    }

    /** Busca quién le escribió /start al bot, lo guarda y le manda un saludo. */
    public static String ConectarTelegram() throws Exception {
        Map<String, Object> chat = Telegram.buscarChat();
        if (chat == null) {
            throw new IllegalStateException("Todavía nadie le ha escrito al bot. Abra el bot en Telegram, presione "
                    + "Iniciar (o escriba /start) y vuelva a presionar \"Conectar Telegram\".");
        }
        String id = String.valueOf(chat.get("CHAT_ID"));
        FirebaseConfigAcceso.db().collection("sistema").document("telegram")
                .set(fila("CHAT_ID", id, "NOMBRE", chat.get("NOMBRE"), "CONECTADO", ahora())).get();
        Telegram.enviar(id, "✅ <b>Panadería San Carlos</b>\nHola " + esc(chat.get("NOMBRE"))
                + ", desde ahora recibirá aquí los avisos de insumos, producción y pan por vencer.");
        return "Telegram conectado con " + chat.get("NOMBRE") + ". Le llegó un mensaje de bienvenida.";
    }

    /* =====================================================================
       Textos de los mensajes
       ===================================================================== */
    private static String textoTelegram(List<Map<String, Object>> avisos) {
        StringBuilder t = new StringBuilder("🥖 <b>Panadería San Carlos</b>\n<i>" + ahora() + "</i>\n");
        if (avisos.isEmpty()) {
            return t.append("\n✅ Todo en orden: no hay avisos.").toString();
        }
        for (String nivel : new String[]{"ALTO", "MEDIO"}) {
            for (Map<String, Object> a : avisos) {
                if (nivel.equals(a.get("NIVEL"))) {
                    t.append("\n").append(a.get("ICONO")).append(" <b>").append(esc(a.get("TITULO"))).append("</b>\n")
                            .append(esc(a.get("DETALLE"))).append("\n");
                }
            }
        }
        return t.append("\nVer en el panel: https://panaderiacloud.onrender.com/panel.html#alertas").toString();
    }

    private static String htmlCorreo(List<Map<String, Object>> avisos) {
        StringBuilder h = new StringBuilder("<div style=\"font-family:Arial,sans-serif;max-width:640px;color:#1f2933\">"
                + "<h2 style=\"color:#7e22ce;margin-bottom:4px\">Panadería San Carlos</h2>"
                + "<p style=\"margin-top:0;color:#52606d\">Aviso automático · " + ahora() + "</p>");
        for (Map<String, Object> a : avisos) {
            String color = "ALTO".equals(a.get("NIVEL")) ? "#c8323a" : "#b7791f";
            h.append("<div style=\"border-left:4px solid ").append(color).append(";padding:8px 12px;margin:10px 0;background:#faf5ff\">")
                    .append("<b>").append(a.get("ICONO")).append(" ").append(esc(a.get("TITULO"))).append("</b><br>")
                    .append(esc(a.get("DETALLE"))).append("</div>");
        }
        return h.append("<p><a href=\"https://panaderiacloud.onrender.com/panel.html#alertas\">Abrir el panel</a></p></div>").toString();
    }

    private static String esc(Object o) {
        return o == null ? "" : o.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Acceso a Firestore desde métodos estáticos. */
    private static final class FirebaseConfigAcceso {
        static com.google.cloud.firestore.Firestore db() throws Exception {
            return BACKEND.utils.FirebaseConfig.db();
        }
    }
}
