# 🥖 Panadería San Carlos — versión en la NUBE (PanaderiaCloud)

Proyecto final de **Desarrollo Web** — Universidad Mariano Gálvez de Guatemala,
Ingeniería en Sistemas de Información, Plan Sábado (2026). Katherin López · Carné 3675.

El mismo sistema (panes, recetas, pedidos, devoluciones, pagos, sucursales), con:

- **Backend Java con Jersey** (misma estructura `controller` / `manager` / `model` / `utils`).
- **Datos en Firebase Cloud Firestore.**
- **Servidor propio (Grizzly) dentro del programa:** no necesita Tomcat ni NetBeans encendido.
- **Publicado en Render:** funciona 24 horas con la computadora apagada, en el teléfono,
  con `https` y se puede **instalar como app**.

| | PanaderiaFirebase | **PanaderiaCloud (este)** |
|---|---|---|
| Servidor | Tomcat en su PC (NetBeans) | Grizzly incluido, corre en Render |
| PC encendida | Sí | **No** |
| Configuración | `config.xml` | Variables de entorno (`Ajustes.java`) |
| Datos | Firestore | Firestore (el mismo proyecto de Firebase) |

> Usa el mismo proyecto de Firebase (`panaderia-san-carlos-1d6f5`), así que ve los mismos
> datos que la versión PanaderiaFirebase.

## Funciones nuevas (octubre 2026)

- **Estadísticas** (Panel → 📊 Estadísticas): panes más vendidos, sucursal que más vende, pan que
  más pide cada sucursal, registro de ventas y ventas vs. compras. `GET /webapi/Reportes/Estadisticas`
- **Sucursales** (Panel → 🏪 Sucursales): botón para agregar sucursal; crea también su usuario
  (primera palabra del nombre). `POST /webapi/Sucursales/CrearSucursal`
- **Compras y proveedores** (`compras.html`): registro de compras con proveedor, precio y No. de
  factura; comparación de precios por insumo; alta de proveedores (los 3 primeros son de ejemplo).
- **Capacidad de producción y pan por vencer** (Panel → Alertas): cuántos panes alcanzan con los
  insumos y qué insumo los limita; cada producción se guarda como lote con fecha (sale primero el
  más viejo) y se avisa el pan que vence hoy o ya se venció (`DIAS_VIDA_PAN`, por defecto 3).
- **🔔 Campanita** (arriba, en todas las páginas del gerente): insumos en el mínimo, pan que no se
  puede producir, pan por producir y pan por vencer. Se revisa cada minuto.
- **✈️ Aviso por Telegram al celular del propietario** (gratis) cuando aparece un problema nuevo
  (y un recordatorio al día mientras siga). El correo (Resend) queda como opción.

- **🚚 Vendedores en ruta** (app `ruta.html` en el teléfono): las unidades de reparto (`unidad1`,
  `unidad2`, `unidad3`, rol VENDEDOR, contraseña `Panaderia2026`) ven los pedidos que se les asignaron,
  marcan **Entregado** o **reportan un problema** en ruta. El gerente recibe el aviso (campanita +
  Telegram) y en **Panel → 🚚 Ruta** reasigna el pedido a **otra unidad**. `GET/POST /webapi/Ruta/...`

### Activar Telegram (una sola vez)

1. En Telegram busque **@BotFather** → `/newbot` → póngale nombre → copie el **token**.
2. Render → su servicio → *Environment* → agregue `TELEGRAM_BOT_TOKEN` = el token → *Save*.
3. El propietario abre el bot y presiona **Iniciar** (`/start`).
4. Panel → Alertas → **Conectar Telegram** → **Enviar aviso ahora (prueba)**.

El correo es opcional: si algún día se quiere, se crea una cuenta en resend.com y se agrega
`RESEND_API_KEY` (el plan gratis de Render bloquea Gmail/SMTP).

## Estructura

```
PanaderiaCloud/
├── pom.xml            ← librerías (Jersey + Grizzly, Firebase Admin, JWT)
├── Dockerfile         ← receta para que Render lo compile y lo encienda
├── render.yaml        ← configuración de Render
└── src/main/
    ├── java/BACKEND/
    │   ├── Main.java      ← enciende el servidor (páginas en / y API en /webapi)
    │   ├── controller/    ← rutas REST (iguales a las de la plantilla)
    │   ├── manager/       ← lógica y transacciones de Firestore
    │   ├── model/         ← forma del JSON
    │   └── utils/         ← Ajustes, FirebaseConfig, seguridad JWT, respuestas
    └── resources/webapp/  ← páginas, CSS, JS, imágenes, app instalable
```

## Probarlo en su computadora (opcional)

1. Copie `clave-firebase.json` (el de la carpeta PanaderiaFirebase) a esta carpeta.
2. NetBeans → File → Open Project → **PanaderiaCloud** → Clean and Build → **Run**.
3. Abra `http://localhost:8080/` (sin `/PanaderiaFirebase`).

## Publicarlo en internet (Render)

1. Suba esta carpeta a un repositorio de **GitHub** (la clave NO se sube: está en `.gitignore`).
2. En **render.com** → *New* → *Web Service* → elija el repositorio → *Language: Docker* →
   *Instance type: Free*.
3. En *Environment Variables* agregue:
   - `FIREBASE_PROYECTO` = `panaderia-san-carlos-1d6f5`
   - `SECRETO` = una frase larga cualquiera
   - `FIREBASE_CREDENCIALES_JSON` = **todo el contenido** de `clave-firebase.json`
4. *Deploy*. En unos minutos queda en `https://<nombre>.onrender.com`.

Plan gratuito: si nadie entra en ~15 minutos el servidor se duerme y la primera visita tarda
cerca de 1 minuto. Antes de presentar, ábralo un minuto antes.

Usuarios: `kgalvez` (gerente), `central`, `chiva`, `barco`, `heydi`, `terminal` (sucursales),
`unidad1`, `unidad2`, `unidad3` (unidades de reparto).
Contraseña de todos: `Panaderia2026`.
