package BACKEND.model;

import java.util.List;

/**
 * Pan nuevo con su receta (lo crea el gerente).
 * {
 *   "NOMBRE": "Pan de manteca", "UNIDAD_MEDIDA": "unidad",
 *   "PRECIO": 1.50, "STOCK_MINIMO": 20,
 *   "RECETA": [ { "ID_INSUMO": 1, "CANTIDAD": 0.300 },
 *               { "ID_INSUMO": 8, "CANTIDAD": 0.040 } ]
 * }
 * Para GuardarReceta solo se envía "RECETA".
 * Para ActualizarPan se envían "PRECIO", "STOCK_MINIMO" y "ESTADO".
 */
public class NuevoPan {
    public String NOMBRE;
    public String UNIDAD_MEDIDA;
    public Double PRECIO;
    public Double STOCK_MINIMO;
    public String ESTADO;
    public List<LineaReceta> RECETA;
}
