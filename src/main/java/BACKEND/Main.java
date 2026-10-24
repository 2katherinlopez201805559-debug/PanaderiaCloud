package BACKEND;

import BACKEND.utils.Ajustes;
import java.net.URI;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Servidor de Panadería San Carlos (versión en la nube).
 *
 * Es la misma plantilla Jersey (controller / manager / model / utils), pero en lugar de
 * necesitar Tomcat y NetBeans, trae su propio servidor web (Grizzly) adentro.
 * Así puede correr en un servidor de internet (Render) con su computadora apagada.
 *
 *   Páginas:  http://servidor/            (carpeta src/main/resources/webapp)
 *   API REST: http://servidor/webapi/...  (los mismos controladores Jersey)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int puerto = Ajustes.puerto();

        // Jersey: registra todos los @Path y @Provider del paquete BACKEND (igual que web.xml)
        ResourceConfig jersey = new ResourceConfig().packages("BACKEND");

        URI base = URI.create("http://0.0.0.0:" + puerto + "/webapi/");
        HttpServer servidor = GrizzlyHttpServerFactory.createHttpServer(base, jersey, false);

        // Páginas web (HTML, CSS, JS, imágenes) en la raíz
        CLStaticHttpHandler paginas = new CLStaticHttpHandler(Main.class.getClassLoader(), "/webapp/");
        paginas.setFileCacheEnabled(false);
        servidor.getServerConfiguration().addHttpHandler(paginas, "/");

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::shutdownNow));
        servidor.start();
        System.out.println("==============================================");
        System.out.println(" Panadería San Carlos funcionando");
        System.out.println(" Abra: http://localhost:" + puerto + "/");
        System.out.println("==============================================");
        Thread.currentThread().join();
    }
}
