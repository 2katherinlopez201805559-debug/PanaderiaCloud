package BACKEND.model;

/**
 * Problema reportado en ruta por una unidad de reparto.
 * { "TIPO": "VEHICULO", "DESCRIPCION": "Se pinchó una llanta en la 4a. calle" }
 * TIPO: VEHICULO | ACCIDENTE | PRODUCTO | SUCURSAL_CERRADA | OTRO
 */
public class ReporteRuta {
    public String TIPO;
    public String DESCRIPCION;
}
