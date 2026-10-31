/* =====================================================================
   Service worker: permite INSTALAR la página como app en el teléfono.
   - Páginas, CSS, JS e imágenes: primero intenta la red (siempre lo más
     nuevo) y si no hay internet usa la copia guardada.
   - Las llamadas a webapi/ (datos de Firebase) NUNCA se guardan.
   Si cambia algo y el teléfono muestra lo viejo, suba la VERSION.
   ===================================================================== */
const VERSION = "panaderia-v2";

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", e => {
    e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== VERSION).map(k => caches.delete(k))))
        .then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
    const req = e.request;
    if (req.method !== "GET" || req.url.includes("/webapi/")) return;   // datos: siempre en vivo
    e.respondWith(
        fetch(req).then(resp => {
            if (resp.ok) {
                const copia = resp.clone();
                caches.open(VERSION).then(c => c.put(req, copia));
            }
            return resp;
        }).catch(() => caches.match(req))
    );
});
