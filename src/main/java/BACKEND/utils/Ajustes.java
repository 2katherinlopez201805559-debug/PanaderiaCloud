package BACKEND.utils;

/**
 * Ajustes del sistema (reemplaza a config.xml).
 * En internet (Render) se leen de VARIABLES DE ENTORNO, que se escriben en el panel de Render
 * y nunca quedan en GitHub. En su computadora se usan los valores por defecto de abajo.
 *
 *   FIREBASE_CREDENCIALES_JSON  contenido completo del archivo clave-firebase.json (en Render)
 *   FIREBASE_CREDENCIALES       ruta al archivo .json  (por defecto: clave-firebase.json en la carpeta del proyecto)
 *   FIREBASE_PROYECTO           ID del proyecto de Firebase
 *   SECRETO                     frase larga para firmar los tokens de inicio de sesión
 *   PORT                        puerto del servidor (Render lo pone solo; en su PC: 8080)
 *
 *   Avisos por correo al propietario (servicio Resend, https://resend.com):
 *   RESEND_API_KEY              llave de Resend (empieza con re_). Sin ella no se envían correos.
 *   CORREO_PROPIETARIO          a quién le llegan los avisos
 *   CORREO_REMITENTE            quién aparece como remitente
 *   DIAS_VIDA_PAN               días que dura el pan antes de vencerse (por defecto 3)
 *
 *   Avisos por Telegram (gratis):
 *   TELEGRAM_BOT_TOKEN          token que da @BotFather al crear el bot
 *   TELEGRAM_CHAT_ID            (opcional) chat del propietario; si no está, se usa el que se
 *                               guardó con el botón "Conectar Telegram" del panel
 */
public final class Ajustes {

    private Ajustes() {
    }

    public static String valor(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return (v == null || v.trim().isEmpty()) ? porDefecto : v.trim();
    }

    public static String credencialesJson() {
        return valor("FIREBASE_CREDENCIALES_JSON", null);
    }

    public static String rutaCredenciales() {
        return valor("FIREBASE_CREDENCIALES", "clave-firebase.json");
    }

    public static String proyecto() {
        return valor("FIREBASE_PROYECTO", "panaderia-san-carlos-1d6f5");
    }

    public static String secreto() {
        return valor("SECRETO", "UMG-Panaderia-San-Carlos-2026-solo-para-pruebas-locales");
    }

    public static int puerto() {
        try {
            return Integer.parseInt(valor("PORT", "8080"));
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    public static String resendApiKey() {
        return valor("RESEND_API_KEY", null);
    }

    public static String correoPropietario() {
        return valor("CORREO_PROPIETARIO", "2katherin.lopez.201805559@gmail.com");
    }

    public static String correoRemitente() {
        return valor("CORREO_REMITENTE", "Panadería San Carlos <onboarding@resend.dev>");
    }

    public static int diasVidaPan() {
        try {
            return Math.max(1, Integer.parseInt(valor("DIAS_VIDA_PAN", "3")));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    public static String telegramToken() {
        return valor("TELEGRAM_BOT_TOKEN", null);
    }

    public static String telegramChatId() {
        return valor("TELEGRAM_CHAT_ID", null);
    }
}
