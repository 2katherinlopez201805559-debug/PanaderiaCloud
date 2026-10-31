package BACKEND.model;

/**
 * Sucursal nueva (la crea el gerente) junto con su usuario para entrar al sistema.
 * { "NOMBRE": "Mercado", "DIRECCION": "...", "TELEFONO": "...", "CLAVE": "Panaderia2026" }
 * Para ActualizarSucursal se envían DIRECCION, TELEFONO y ESTADO.
 */
public class NuevaSucursal {
    public String NOMBRE;
    public String DIRECCION;
    public String TELEFONO;
    public String CLAVE;
    public String ESTADO;
}
