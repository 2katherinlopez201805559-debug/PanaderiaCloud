package BACKEND.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 * Envío de correos con la API de Resend (https://resend.com).
 *
 * ¿Por qué no Gmail/SMTP? El plan gratuito de Render bloquea los puertos de correo
 * (25, 465 y 587). Resend se usa por HTTPS (puerto 443), que sí está permitido.
 *
 * Plan gratis de Resend sin dominio propio: el remitente es onboarding@resend.dev y
 * solo se puede enviar al correo con el que se creó la cuenta de Resend.
 */
public final class Correo {

    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Jsonb JSON = JsonbBuilder.create();

    private Correo() {
    }

    public static boolean configurado() {
        return Ajustes.resendApiKey() != null;
    }

    /** Envía un correo HTML. Lanza excepción con el motivo si Resend lo rechaza. */
    public static void enviar(String asunto, String html) throws Exception {
        String llave = Ajustes.resendApiKey();
        if (llave == null) {
            throw new IllegalStateException("Correo no configurado: falta la variable RESEND_API_KEY en Render.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("from", Ajustes.correoRemitente());
        cuerpo.put("to", Collections.singletonList(Ajustes.correoPropietario()));
        cuerpo.put("subject", asunto);
        cuerpo.put("html", html);

        HttpRequest peticion = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + llave)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(cuerpo), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("Resend respondió " + r.statusCode() + ": " + r.body());
        }
    }
}
