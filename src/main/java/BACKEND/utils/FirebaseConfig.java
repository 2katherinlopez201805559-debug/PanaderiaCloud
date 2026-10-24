package BACKEND.utils;

import BACKEND.manager.DatosIniciales;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Conexión a Firebase Firestore.
 *  - En Render: toma la clave de la variable FIREBASE_CREDENCIALES_JSON (contenido del .json).
 *  - En su PC: lee el archivo clave-firebase.json de la carpeta del proyecto.
 * La primera vez, si la base está vacía, carga los datos iniciales.
 */
public final class FirebaseConfig {

    private static Firestore db;

    private FirebaseConfig() {
    }

    public static synchronized Firestore db() throws Exception {
        if (db == null) {
            if (FirebaseApp.getApps().isEmpty()) {
                try (InputStream clave = abrirClave()) {
                    FirebaseOptions opciones = new FirebaseOptions.Builder()
                            .setCredentials(GoogleCredentials.fromStream(clave))
                            .setProjectId(Ajustes.proyecto())
                            .build();
                    FirebaseApp.initializeApp(opciones);
                }
            }
            Firestore conexion = FirestoreClient.getFirestore();
            DatosIniciales.cargarSiHaceFalta(conexion);
            db = conexion;
        }
        return db;
    }

    private static InputStream abrirClave() throws Exception {
        String json = Ajustes.credencialesJson();
        if (json != null) {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        String ruta = Ajustes.rutaCredenciales();
        try {
            return new FileInputStream(ruta);
        } catch (java.io.FileNotFoundException e) {
            throw new IllegalStateException("No se encontró la clave de Firebase (" + ruta + "). "
                    + "En su PC copie clave-firebase.json a la carpeta del proyecto; "
                    + "en Render configure la variable FIREBASE_CREDENCIALES_JSON.");
        }
    }
}
