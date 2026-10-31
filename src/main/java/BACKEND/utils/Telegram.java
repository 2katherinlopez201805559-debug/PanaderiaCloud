package BACKEND.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 * Mensajes al celular del propietario por Telegram (gratis).
 *
 *  1. En Telegram se habla con @BotFather -> /newbot -> da un TOKEN.
 *  2. Ese token va en la variable TELEGRAM_BOT_TOKEN de Render.
 *  3. El propietario abre su bot y le escribe /start.
 *  4. En el panel (Alertas) se presiona "Conectar Telegram": el sistema busca ese
 *     mensaje, guarda el número del chat en Firestore y desde ahí ya le puede escribir.
 *
 * Usa HTTPS (puerto 443), que sí está permitido en el plan gratuito de Render.
 */
public final class Telegram {

    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Jsonb JSON = JsonbBuilder.create();

    private Telegram() {
    }

    public static boolean configurado() {
        return Ajustes.telegramToken() != null;
    }

    /** Envía un mensaje (admite <b>, <i>, <a href>). */
    public static void enviar(String chatId, String texto) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("chat_id", chatId);
        cuerpo.put("text", texto.length() > 4000 ? texto.substring(0, 3990) + "…" : texto);
        cuerpo.put("parse_mode", "HTML");
        cuerpo.put("disable_web_page_preview", true);
        llamar("sendMessage", JSON.toJson(cuerpo));
    }

    /**
     * Busca el último chat que le escribió al bot (después de /start).
     * Devuelve {CHAT_ID, NOMBRE} o null si nadie le ha escrito.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buscarChat() throws Exception {
        String r = llamar("getUpdates", "{}");
        Map<String, Object> datos = JSON.fromJson(r, LinkedHashMap.class);
        Object res = datos.get("result");
        if (!(res instanceof List) || ((List<?>) res).isEmpty()) {
            return null;
        }
        List<?> lista = (List<?>) res;
        for (int i = lista.size() - 1; i >= 0; i--) {          // del más nuevo al más viejo
            Map<String, Object> u = (Map<String, Object>) lista.get(i);
            Object m = u.get("message") != null ? u.get("message") : u.get("my_chat_member");
            if (!(m instanceof Map)) {
                continue;
            }
            Object chat = ((Map<String, Object>) m).get("chat");
            if (chat instanceof Map) {
                Map<String, Object> c = (Map<String, Object>) chat;
                Map<String, Object> salida = new LinkedHashMap<String, Object>();
                salida.put("CHAT_ID", new java.math.BigDecimal(String.valueOf(c.get("id"))).toBigInteger().toString());
                Object nombre = c.get("first_name") != null ? c.get("first_name") : c.get("title");
                salida.put("NOMBRE", nombre == null ? "" : String.valueOf(nombre));
                return salida;
            }
        }
        return null;
    }

    private static String llamar(String metodo, String json) throws Exception {
        String token = Ajustes.telegramToken();
        if (token == null) {
            throw new IllegalStateException("Telegram no configurado: falta la variable TELEGRAM_BOT_TOKEN en Render.");
        }
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + metodo))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            String motivo = r.statusCode() == 401 ? "el TOKEN del bot es incorrecto"
                    : r.statusCode() == 400 || r.statusCode() == 403 ? "el propietario debe abrir el bot y escribirle /start" : r.body();
            throw new IllegalStateException("Telegram respondió " + r.statusCode() + ": " + motivo + ".");
        }
        return r.body();
    }
}
