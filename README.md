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

Usuarios: `kgalvez` (gerente), `central`, `chiva`, `barco`, `heydi`, `terminal`.
Contraseña de todos: `Panaderia2026`.
