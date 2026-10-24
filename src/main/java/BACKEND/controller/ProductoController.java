package BACKEND.controller;

import BACKEND.manager.CatalogoManager;
import BACKEND.manager.ProductoManager;
import BACKEND.model.LineaReceta;
import BACKEND.model.NuevoPan;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import BACKEND.model.Produccion;
import BACKEND.utils.Respuesta;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;
import BACKEND.utils.Acceso;

/**
 * GET  /webapi/Productos/ObtenerProductos
 * GET  /webapi/Productos/{id}/ObtenerReceta
 * (ObtenerProductos es público; receta y producción solo GERENTE)
 * POST /webapi/Productos/ProducirPan        { "ID_PRODUCTO": 3, "CANTIDAD": 50 }
 * POST /webapi/Productos/CrearPan           (solo gerente)
 *      { "NOMBRE": "Pan de manteca", "UNIDAD_MEDIDA": "unidad", "PRECIO": 1.50, "STOCK_MINIMO": 20,
 *        "RECETA": [ {"ID_INSUMO": 1, "CANTIDAD": 0.3}, {"ID_INSUMO": 8, "CANTIDAD": 0.04} ] }
 * POST /webapi/Productos/{id}/GuardarReceta (solo gerente)  { "RECETA": [ ... ] }
 * POST /webapi/Productos/{id}/ActualizarPan (solo gerente)  { "PRECIO": 1.75, "STOCK_MINIMO": 20, "ESTADO": "ACTIVO" }
 */
@Path("Productos")
@Produces(MediaType.APPLICATION_JSON)
public class ProductoController {

    private final ProductoManager manager = new ProductoManager();
    private final CatalogoManager catalogo = new CatalogoManager();

    @GET
    @Path("/ObtenerProductos")
    public Response ObtenerProductos() {
        try {
            return Respuesta.ok(manager.ObtenerProductos());
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @GET
    @Path("/{id}/ObtenerReceta")
    public Response ObtenerReceta(@Context SecurityContext sc, @PathParam("id") int id) {
        try {
            Acceso.requerirGerente(sc);
            return Respuesta.ok(manager.ObtenerReceta(id));
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/ProducirPan")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ProducirPan(@Context SecurityContext sc, Produccion item) {
        if (item == null || item.ID_PRODUCTO == null) {
            return Respuesta.validacion("Seleccione el pan a producir.");
        }
        if (item.CANTIDAD == null || item.CANTIDAD <= 0) {
            return Respuesta.validacion("La cantidad a producir debe ser mayor que 0.");
        }
        try {
            Acceso.requerirGerente(sc);
            manager.ProducirPan(item);
            return Respuesta.ok(item.ID_PRODUCTO, "Producción registrada correctamente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/CrearPan")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response CrearPan(@Context SecurityContext sc, NuevoPan p) {
        String error = validarPan(p);
        if (error == null) {
            error = validarReceta(p.RECETA);
        }
        if (error != null) {
            return Respuesta.validacion(error);
        }
        try {
            Acceso.requerirGerente(sc);
            int id = catalogo.CrearPan(p);
            return Respuesta.ok(id, "«" + p.NOMBRE.trim() + "» creado con su receta (#" + id + ").");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/GuardarReceta")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response GuardarReceta(@Context SecurityContext sc, @PathParam("id") int id, NuevoPan p) {
        String error = (p == null) ? "No se recibió la receta." : validarReceta(p.RECETA);
        if (error != null) {
            return Respuesta.validacion(error);
        }
        try {
            Acceso.requerirGerente(sc);
            catalogo.GuardarReceta(id, p.RECETA);
            return Respuesta.ok(id, "Receta guardada correctamente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    @POST
    @Path("/{id}/ActualizarPan")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ActualizarPan(@Context SecurityContext sc, @PathParam("id") int id, NuevoPan p) {
        if (p == null || p.PRECIO == null || p.PRECIO <= 0) {
            return Respuesta.validacion("El precio debe ser mayor que 0.");
        }
        if (p.STOCK_MINIMO == null || p.STOCK_MINIMO < 0) {
            return Respuesta.validacion("La existencia mínima no puede ser negativa.");
        }
        if (!"ACTIVO".equals(p.ESTADO) && !"INACTIVO".equals(p.ESTADO)) {
            return Respuesta.validacion("El estado debe ser ACTIVO o INACTIVO.");
        }
        try {
            Acceso.requerirGerente(sc);
            catalogo.ActualizarPan(id, p);
            return Respuesta.ok(id, "Pan actualizado correctamente.");
        } catch (Exception e) {
            return Respuesta.error(e);
        }
    }

    private String validarPan(NuevoPan p) {
        if (p == null) {
            return "No se recibieron los datos del pan.";
        }
        if (p.NOMBRE == null || p.NOMBRE.trim().length() < 3 || p.NOMBRE.trim().length() > 60) {
            return "El nombre del pan debe tener entre 3 y 60 letras.";
        }
        if (!p.NOMBRE.trim().matches("[\\p{L} .'-]+")) {
            return "El nombre del pan solo admite letras y espacios.";
        }
        if (p.PRECIO == null || p.PRECIO <= 0 || p.PRECIO > 1000) {
            return "El precio debe ser mayor que 0 (máximo Q 1,000).";
        }
        if (p.STOCK_MINIMO == null || p.STOCK_MINIMO < 0) {
            return "La existencia mínima no puede ser negativa.";
        }
        return null;
    }

    private String validarReceta(List<LineaReceta> receta) {
        if (receta == null || receta.isEmpty()) {
            return "Agregue al menos un insumo a la receta.";
        }
        Set<Integer> vistos = new HashSet<>();
        for (LineaReceta l : receta) {
            if (l == null || l.ID_INSUMO == null) {
                return "Seleccione el insumo en todas las líneas de la receta.";
            }
            if (l.CANTIDAD == null || l.CANTIDAD <= 0) {
                return "Todas las cantidades de la receta deben ser mayores que 0.";
            }
            if (!vistos.add(l.ID_INSUMO)) {
                return "Hay un insumo repetido en la receta.";
            }
        }
        return null;
    }
}
