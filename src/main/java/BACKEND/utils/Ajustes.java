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
}
