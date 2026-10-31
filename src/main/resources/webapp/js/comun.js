/* =====================================================================
   Panadería San Carlos — Código compartido por todas las páginas
   - Sesión (token JWT guardado en sessionStorage)
   - Menú lateral desplegable según el rol (GERENTE / SUCURSAL)
   - Cliente REST (fetch) hacia /BACKEND/webapi
   - Avisos, formato de números y validaciones
   ===================================================================== */

// ---- Datos del curso: edita aquí tu nombre y carné ----
const CURSO = {
    universidad: "Universidad Mariano Gálvez de Guatemala",
    carrera: "Ingeniería en Sistemas de Información",
    curso: "Desarrollo Web",
    plan: "Plan Sábado",
    estudiante: "Katherin López",
    carne: "3675",
    ciclo_Escolar: 2026
};

// Ruta relativa: funciona igual en localhost y cuando se publique en internet
const API_BASE = "webapi";

/* ---------------------------------------------------------------------
   Sesión
   --------------------------------------------------------------------- */
const Sesion = {
    clave: "panaderia.sesion",
    obtener() {
        try { return JSON.parse(sessionStorage.getItem(this.clave)); } catch (e) { return null; }
    },
    guardar(datos) {
        try { sessionStorage.setItem(this.clave, JSON.stringify(datos)); } catch (e) { /* sin almacenamiento */ }
    },
    cerrar() {
        try { sessionStorage.removeItem(this.clave); } catch (e) { /* nada */ }
        location.href = "login.html";
    },
    esGerente() {
        const s = this.obtener();
        return !!s && s.ROL === "GERENTE";
    },
    /** Página de inicio según el rol. */
    inicio() {
        const s = this.obtener();
        if (s && s.ROL === "VENDEDOR") return "ruta.html";
        return this.esGerente() ? "panel.html#pedidos" : "pedidos.html#nuevo";
    }
};

/* ---------------------------------------------------------------------
   Menú lateral (lista desplegable). "roles" indica quién lo ve.
   --------------------------------------------------------------------- */
const MENU = [
    {
        grupo: "Pedidos", icono: "🧾", roles: ["GERENTE", "SUCURSAL"], items: [
            { texto: "Nuevo pedido", href: "pedidos.html#nuevo", roles: ["GERENTE", "SUCURSAL"] },
            { texto: "Mis pedidos", href: "pedidos.html#historial", roles: ["SUCURSAL"] },
            { texto: "Pedidos de sucursales", href: "panel.html#pedidos", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Ruta", icono: "🚚", roles: ["GERENTE", "VENDEDOR"], items: [
            { texto: "Mis entregas", href: "ruta.html#entregas", roles: ["VENDEDOR"] },
            { texto: "Mis reportes", href: "ruta.html#reportes", roles: ["VENDEDOR"] },
            { texto: "Unidades en ruta", href: "panel.html#ruta", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Devoluciones", icono: "↩️", roles: ["GERENTE", "SUCURSAL"], items: [
            { texto: "Registrar devolución", href: "devoluciones.html#nueva", roles: ["SUCURSAL"] },
            { texto: "Mis devoluciones", href: "devoluciones.html#historial", roles: ["SUCURSAL"] },
            { texto: "Revisar devoluciones", href: "devoluciones.html#historial", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Producción", icono: "🍞", roles: ["GERENTE"], items: [
            { texto: "Registrar producción", href: "panel.html#produccion", roles: ["GERENTE"] },
            { texto: "Panes y recetas", href: "panel.html#panes", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Reportes", icono: "📊", roles: ["GERENTE"], items: [
            { texto: "Estadísticas de ventas", href: "panel.html#estadisticas", roles: ["GERENTE"] },
            { texto: "Sucursales", href: "panel.html#sucursales", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Pagos", icono: "💳", roles: ["GERENTE", "SUCURSAL"], items: [
            { texto: "Pagar pedido", href: "pagos.html#pagar", roles: ["SUCURSAL"] },
            { texto: "Mis pagos", href: "pagos.html#historial", roles: ["SUCURSAL"] },
            { texto: "Cuentas por cobrar", href: "pagos.html#cuentas", roles: ["GERENTE"] },
            { texto: "Revisar pagos", href: "pagos.html#historial", roles: ["GERENTE"] },
            { texto: "Registrar cobro", href: "pagos.html#pagar", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Inventario", icono: "📦", roles: ["GERENTE"], items: [
            { texto: "Insumos y existencias", href: "panel.html#insumos", roles: ["GERENTE"] },
            { texto: "Alertas y avisos", href: "panel.html#alertas", roles: ["GERENTE"] }
        ]
    },
    {
        grupo: "Compras", icono: "🧾", roles: ["GERENTE"], items: [
            { texto: "Registrar compra / factura", href: "compras.html#compras", roles: ["GERENTE"] },
            { texto: "Comparar precios", href: "compras.html#precios", roles: ["GERENTE"] },
            { texto: "Proveedores", href: "compras.html#proveedores", roles: ["GERENTE"] }
        ]
    }
];

/**
 * Arma la pantalla del sistema: menú lateral + barra superior + pie.
 * Si no hay sesión envía al login; si el rol no tiene permiso, a su inicio.
 */
function montarSistema(rolesPermitidos) {
    const s = Sesion.obtener();
    if (!s || !s.TOKEN) {
        location.replace("login.html?volver=" + encodeURIComponent(location.pathname.split("/").pop() + location.hash));
        throw new Error("Sin sesión");
    }
    if (rolesPermitidos && !rolesPermitidos.includes(s.ROL)) {
        location.replace(Sesion.inicio());
        throw new Error("Sin permiso");
    }

    const main = document.querySelector("main");
    const app = document.createElement("div");
    app.className = "app";

    const pagina = location.pathname.split("/").pop() || "index.html";
    const grupos = MENU.filter(g => g.roles.includes(s.ROL)).map(g => {
        const items = g.items.filter(i => i.roles.includes(s.ROL));
        const abierto = items.some(i => i.href.split("#")[0] === pagina);
        return `
            <details class="menu-grupo" ${abierto ? "open" : ""}>
                <summary><span class="ico">${g.icono}</span>${g.grupo}</summary>
                ${items.map(i => `<a href="${i.href}" data-href="${i.href}">${i.texto}</a>`).join("")}
            </details>`;
    }).join("");

    const quien = s.ROL === "GERENTE" ? "Gerente" : s.ROL === "VENDEDOR" ? "Unidad de reparto" : "Sucursal " + (s.SUCURSAL || "");
    app.innerHTML = `
        <aside class="lateral" id="lateral">
            <a class="lateral-marca" href="index.html" title="Ir al sitio público">
                <img src="img/umg.png" alt="Escudo UMG">
                <span><strong>San Carlos</strong><small>Sistema de producción</small></span>
            </a>
            <nav class="lateral-menu" aria-label="Menú del sistema">${grupos}</nav>
            <div class="lateral-pie">
                <a href="index.html">🌐 Ver sitio público</a>
                <button type="button" id="btnSalir">⎋ Cerrar sesión</button>
            </div>
        </aside>
        <div class="app-cuerpo">
            <header class="barra-superior">
                <button type="button" class="btn-menu" id="btnMenu" aria-label="Abrir menú">☰</button>
                <span class="barra-umg">UMG · ${CURSO.carrera} · ${CURSO.plan}</span>
                ${s.ROL === "GERENTE" ? `
                <div class="campanita" id="campanita">
                    <button type="button" class="btn-campana" id="btnCampana" aria-label="Avisos" aria-expanded="false">🔔<span class="campana-num oculto" id="campanaNum">0</span></button>
                    <div class="campana-lista oculto" id="campanaLista" role="dialog" aria-label="Avisos"></div>
                </div>` : ""}
                <div class="usuario-chip">
                    <span class="avatar">${escaparHTML((s.NOMBRE || "?").charAt(0))}</span>
                    <span><strong>${escaparHTML(s.NOMBRE)}</strong><small>${escaparHTML(quien)}</small></span>
                </div>
            </header>
            <div class="app-contenido"></div>
            ${htmlPie()}
        </div>
        <div class="velo" id="velo"></div>`;

    main.replaceWith(app);
    app.querySelector(".app-contenido").append(main);

    marcarMenuActivo();
    window.addEventListener("hashchange", marcarMenuActivo);

    if (s.ROL === "GERENTE") iniciarCampanita();
    document.getElementById("btnSalir").addEventListener("click", () => Sesion.cerrar());
    const alternar = (abrir) => document.body.classList.toggle("menu-abierto", abrir);
    document.getElementById("btnMenu").addEventListener("click", () => alternar(!document.body.classList.contains("menu-abierto")));
    document.getElementById("velo").addEventListener("click", () => alternar(false));
    app.querySelectorAll(".lateral-menu a").forEach(a => a.addEventListener("click", () => alternar(false)));

    agregarToasts();
    return s;
}

/* ---------------------------------------------------------------------
   🔔 Campanita del gerente: insumos bajos, pan que no se puede producir,
   pan por producir y pan por vencer. Se revisa cada minuto.
   --------------------------------------------------------------------- */
let avisosVistos = null;
function iniciarCampanita() {
    const btn = document.getElementById("btnCampana");
    const lista = document.getElementById("campanaLista");
    const abrir = (si) => { lista.classList.toggle("oculto", !si); btn.setAttribute("aria-expanded", si); };
    btn.addEventListener("click", e => { e.stopPropagation(); abrir(lista.classList.contains("oculto")); });
    document.addEventListener("click", e => { if (!e.target.closest("#campanita")) abrir(false); });
    document.addEventListener("keydown", e => { if (e.key === "Escape") abrir(false); });
    revisarCampanita();
    setInterval(revisarCampanita, 60000);
}

async function revisarCampanita() {
    const num = document.getElementById("campanaNum");
    const lista = document.getElementById("campanaLista");
    if (!num) return;
    let avisos;
    try { avisos = await Api.avisos(); } catch (e) { return; }
    const altos = avisos.filter(a => a.NIVEL === "ALTO").length;
    num.textContent = avisos.length;
    num.classList.toggle("oculto", !avisos.length);
    num.classList.toggle("medio", !altos);
    lista.innerHTML = `<div class="campana-titulo"><strong>Avisos</strong><a href="panel.html#alertas">Ver todo</a></div>` +
        (avisos.length ? avisos.map(a => `
            <a class="campana-item ${a.NIVEL === "ALTO" ? "alto" : ""}" href="${escaparHTML(a.ENLACE)}">
                <span class="ico">${a.ICONO}</span>
                <span><strong>${escaparHTML(a.TITULO)}</strong><small>${escaparHTML(a.DETALLE)}</small></span>
            </a>`).join("") : `<p class="campana-vacia">✅ Todo en orden.</p>`);
    // Aviso emergente solo si aparece algo nuevo
    const claves = new Set(avisos.map(a => a.CLAVE));
    if (avisosVistos) {
        const nuevos = avisos.filter(a => !avisosVistos.has(a.CLAVE));
        if (nuevos.length) avisar(`🔔 ${nuevos[0].TITULO}${nuevos.length > 1 ? ` y ${nuevos.length - 1} aviso(s) más` : ""}`, "error");
    }
    avisosVistos = claves;
}

function marcarMenuActivo() {
    const actual = (location.pathname.split("/").pop() || "index.html") + location.hash;
    document.querySelectorAll(".lateral-menu a").forEach(a => {
        a.classList.toggle("activo", a.dataset.href === actual);
    });
}

/** Encabezado y pie de las páginas públicas (portada y login). */
function montarPublico() {
    const s = Sesion.obtener();
    const header = document.createElement("header");
    header.className = "encabezado";
    header.innerHTML = `
        <div class="franja"></div>
        <div class="contenedor">
            <a class="marca" href="index.html">
                <img src="img/umg.png" alt="Escudo de la Universidad Mariano Gálvez">
                <span>
                    <span class="titulo">Panadería San Carlos</span>
                    <span class="sub">Pan fresco cada mañana · Proyecto UMG</span>
                </span>
            </a>
            <nav class="menu" aria-label="Menú principal">
                <a href="index.html#productos">Productos</a>
                <a href="index.html#sucursales">Sucursales</a>
                <a href="index.html#umg">Proyecto UMG</a>
                ${s && s.TOKEN
                    ? `<a class="activo" href="${Sesion.inicio()}">Ir al sistema</a>`
                    : `<a class="activo" href="login.html">Ingresar</a>`}
            </nav>
        </div>`;
    document.body.prepend(header);
    document.body.insertAdjacentHTML("beforeend", htmlPie());
    agregarToasts();
}

function htmlPie() {
    return `
        <footer class="pie">
            <div class="contenedor">
                <div>
                    <strong>${CURSO.universidad}</strong><br>
                    ${CURSO.carrera} · ${CURSO.curso} · ${CURSO.plan} · ${CURSO.anio ?? CURSO.ciclo_Escolar}
                </div>
                <div>
                    Desarrollado por <strong>${CURSO.estudiante}</strong> · Carné ${CURSO.carne}<br>
                    <code>Firebase · Java Jersey (REST) · HTML/CSS/JS</code>
                </div>
            </div>
        </footer>`;
}

function agregarToasts() {
    if (document.getElementById("toasts")) return;
    const toasts = document.createElement("div");
    toasts.id = "toasts";
    toasts.setAttribute("aria-live", "polite");
    document.body.append(toasts);
}

/* ---------------------------------------------------------------------
   Cliente REST (envía el token en cada petición)
   --------------------------------------------------------------------- */
async function api(ruta, opciones = {}) {
    const config = { method: opciones.method || "GET", headers: { "Accept": "application/json" } };
    const s = Sesion.obtener();
    if (s && s.TOKEN) config.headers["Authorization"] = "Bearer " + s.TOKEN;
    if (opciones.body !== undefined) {
        config.headers["Content-Type"] = "application/json";
        config.body = JSON.stringify(opciones.body);
    }

    let respuesta;
    try {
        respuesta = await fetch(`${API_BASE}/${ruta}`, config);
    } catch (e) {
        throw new Error("No se pudo conectar con el servidor. Revise su internet e intente de nuevo.");
    }

    let datos = null;
    const texto = await respuesta.text();
    if (texto) {
        try { datos = JSON.parse(texto); } catch (e) { datos = texto; }
    }

    // Sesión vencida: de regreso al login (excepto en el propio login)
    if (respuesta.status === 401 && !ruta.startsWith("Auth/Login")) {
        try { sessionStorage.removeItem(Sesion.clave); } catch (e) { /* nada */ }
        location.href = "login.html?expirada=1";
        throw new Error("Sesión expirada.");
    }
    if (!respuesta.ok) {
        const mensaje = (datos && datos.msj) ? datos.msj : `Error ${respuesta.status} en el servidor.`;
        throw new Error(mensaje);
    }
    return datos;
}

const Api = {
    login: (usuario, clave) => api("Auth/Login", { method: "POST", body: { USUARIO: usuario, CLAVE: clave } }),
    sucursales: () => api("Sucursales/ObtenerSucursales"),
    productos: () => api("Productos/ObtenerProductos"),
    receta: (id) => api(`Productos/${id}/ObtenerReceta`),
    producir: (idProducto, cantidad) => api("Productos/ProducirPan", { method: "POST", body: { ID_PRODUCTO: idProducto, CANTIDAD: cantidad } }),
    insumos: () => api("Insumos/ObtenerInsumos"),
    comprarInsumo: (idInsumo, cantidad) => api("Insumos/RegistrarCompra", { method: "POST", body: { ID_INSUMO: idInsumo, CANTIDAD: cantidad } }),
    pedidos: (idSucursal, estado) => {
        const q = new URLSearchParams();
        if (idSucursal) q.set("idSucursal", idSucursal);
        if (estado) q.set("estado", estado);
        const qs = q.toString();
        return api("Pedidos/ObtenerPedidos" + (qs ? "?" + qs : ""));
    },
    detallePedido: (id) => api(`Pedidos/${id}/ObtenerDetalle`),
    registrarPedido: (pedido) => api("Pedidos/RegistrarPedido", { method: "POST", body: pedido }),
    despachar: (id) => api(`Pedidos/${id}/Despachar`, { method: "POST" }),
    entregar: (id) => api(`Pedidos/${id}/Entregar`, { method: "POST" }),
    cancelar: (id) => api(`Pedidos/${id}/Cancelar`, { method: "POST" }),
    devoluciones: (estado) => api("Devoluciones/ObtenerDevoluciones" + (estado ? "?estado=" + encodeURIComponent(estado) : "")),
    registrarDevolucion: (d) => api("Devoluciones/RegistrarDevolucion", { method: "POST", body: d }),
    aprobarDevolucion: (id) => api(`Devoluciones/${id}/Aprobar`, { method: "POST" }),
    rechazarDevolucion: (id) => api(`Devoluciones/${id}/Rechazar`, { method: "POST" }),
    crearPan: (pan) => api("Productos/CrearPan", { method: "POST", body: pan }),
    guardarReceta: (id, receta) => api(`Productos/${id}/GuardarReceta`, { method: "POST", body: { RECETA: receta } }),
    actualizarPan: (id, datos) => api(`Productos/${id}/ActualizarPan`, { method: "POST", body: datos }),
    pagos: (estado) => api("Pagos/ObtenerPagos" + (estado ? "?estado=" + encodeURIComponent(estado) : "")),
    estadoCuenta: (idSucursal) => api("Pagos/EstadoCuenta" + (idSucursal ? "?idSucursal=" + encodeURIComponent(idSucursal) : "")),
    registrarPago: (pago) => api("Pagos/RegistrarPago", { method: "POST", body: pago }),
    confirmarPago: (id) => api(`Pagos/${id}/Confirmar`, { method: "POST" }),
    rechazarPago: (id) => api(`Pagos/${id}/Rechazar`, { method: "POST" }),
    alertas: () => api("Reportes/ObtenerAlertas"),
    resumenProduccion: (fecha) => api("Reportes/ResumenProduccion" + (fecha ? "?fecha=" + encodeURIComponent(fecha) : "")),
    // Estadísticas, capacidad de producción, pan por vencer y avisos por correo
    estadisticas: (desde, hasta) => api("Reportes/Estadisticas?" + new URLSearchParams({ desde: desde || "", hasta: hasta || "" })),
    capacidad: () => api("Reportes/CapacidadProduccion"),
    porVencer: () => api("Reportes/PanesPorVencer"),
    estadoAvisos: () => api("Reportes/EstadoAvisos"),
    avisos: () => api("Reportes/Avisos"),
    conectarTelegram: () => api("Reportes/ConectarTelegram", { method: "POST" }),
    // Vendedores en ruta (unidades de reparto)
    misPedidosRuta: () => api("Ruta/MisPedidos"),
    misReportesRuta: () => api("Ruta/MisReportes"),
    entregarRuta: (id) => api(`Ruta/Entrega/${id}`, { method: "POST" }),
    reportarProblema: (id, tipo, descripcion) => api(`Ruta/Reporte/${id}`, { method: "POST", body: { TIPO: tipo, DESCRIPCION: descripcion } }),
    unidades: () => api("Ruta/Unidades"),
    pedidosEnRuta: () => api("Ruta/PedidosEnRuta"),
    incidentes: (estado) => api("Ruta/Incidentes" + (estado ? "?estado=" + encodeURIComponent(estado) : "")),
    asignarUnidad: (idPedido, idUnidad) => api(`Ruta/Asignar/${idPedido}`, { method: "POST", body: { ID_UNIDAD: idUnidad } }),
    resolverIncidente: (id, idUnidad, nota) => api(`Ruta/Incidentes/${id}/Resolver`, { method: "POST", body: { ID_UNIDAD: idUnidad, NOTA: nota } }),
    enviarAviso: () => api("Reportes/EnviarAviso", { method: "POST" }),
    // Sucursales nuevas
    crearSucursal: (s) => api("Sucursales/CrearSucursal", { method: "POST", body: s }),
    actualizarSucursal: (id, s) => api(`Sucursales/${id}/ActualizarSucursal`, { method: "POST", body: s }),
    // Proveedores, precios y compras (facturas)
    proveedores: () => api("Proveedores/ObtenerProveedores"),
    crearProveedor: (p) => api("Proveedores/CrearProveedor", { method: "POST", body: p }),
    actualizarProveedor: (id, p) => api(`Proveedores/${id}/ActualizarProveedor`, { method: "POST", body: p }),
    guardarPrecio: (id, idInsumo, precio) => api(`Proveedores/${id}/GuardarPrecio`, { method: "POST", body: { ID_INSUMO: idInsumo, PRECIO: precio } }),
    compararPrecios: () => api("Proveedores/CompararPrecios"),
    registrarCompra: (c) => api("Insumos/RegistrarCompra", { method: "POST", body: c }),
    compras: (desde, hasta, idProveedor) => api("Insumos/ObtenerCompras?" + new URLSearchParams({ desde: desde || "", hasta: hasta || "", idProveedor: idProveedor || "" }))
};

/* ---------------------------------------------------------------------
   Utilidades de interfaz
   --------------------------------------------------------------------- */
function avisar(mensaje, tipo = "ok") {
    const caja = document.getElementById("toasts");
    if (!caja) { alert(mensaje); return; }
    const t = document.createElement("div");
    t.className = "toast" + (tipo === "error" ? " error" : "");
    t.textContent = mensaje;
    caja.append(t);
    setTimeout(() => t.remove(), tipo === "error" ? 6000 : 3500);
}

function moneda(n) {
    return "Q " + Number(n || 0).toLocaleString("es-GT", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function numero(n, decimales = 2) {
    return Number(n || 0).toLocaleString("es-GT", { maximumFractionDigits: decimales });
}

function escaparHTML(texto) {
    return String(texto ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function badgeEstado(estado) {
    return `<span class="badge badge-${escaparHTML(estado)}">${escaparHTML(estado)}</span>`;
}

function barraStock(actual, minimo) {
    const bajo = Number(actual) <= Number(minimo);
    const tope = Math.max(Number(minimo) * 2, Number(actual), 1);
    const pct = Math.min(100, Math.round(Number(actual) / tope * 100));
    return `<div class="stock ${bajo ? "bajo" : ""}" title="Mínimo: ${numero(minimo)}">
                <div class="barra"><span style="width:${pct}%"></span></div>
            </div>`;
}

function fechaISO(diasDesdeHoy = 0) {
    const d = new Date();
    d.setDate(d.getDate() + diasDesdeHoy);
    const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
}

/** Desactiva el botón mientras corre la acción (evita dobles envíos). */
async function conBoton(boton, accion) {
    const original = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = "Procesando…";
    try {
        return await accion();
    } finally {
        boton.disabled = false;
        boton.innerHTML = original;
    }
}

/** Muestra una sección según el #hash (#nuevo, #historial…). */
function seccionesPorHash(porDefecto) {
    const mostrar = () => {
        const id = location.hash.slice(1) || porDefecto;
        const objetivo = document.getElementById("sec-" + id) ? id : porDefecto;
        document.querySelectorAll("[data-seccion]").forEach(s => s.classList.toggle("oculto", s.dataset.seccion !== objetivo));
        window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", mostrar);
    mostrar();
}

/* ---------------------------------------------------------------------
   Validaciones de formulario
   --------------------------------------------------------------------- */
function marcarError(input, mensaje) {
    input.classList.add("invalido");
    const msg = input.closest(".campo")?.querySelector(".msg-error");
    if (msg) msg.textContent = mensaje;
    return false;
}

function limpiarError(input) {
    input.classList.remove("invalido");
    const msg = input.closest(".campo")?.querySelector(".msg-error");
    if (msg) msg.textContent = "";
}

function validarRequerido(input, mensaje) {
    limpiarError(input);
    if (!String(input.value).trim()) return marcarError(input, mensaje);
    return true;
}

/** Valida un número > 0. Si entero=true no acepta decimales (panes). */
function validarCantidad(input, { entero = false, maximo = 100000, permitirVacio = false } = {}) {
    limpiarError(input);
    const texto = String(input.value).trim();
    if (!texto) return permitirVacio ? true : marcarError(input, "Ingrese una cantidad.");
    const valor = Number(texto);
    if (!Number.isFinite(valor)) return marcarError(input, "Debe ser un número.");
    if (valor <= 0) return marcarError(input, "Debe ser mayor que 0.");
    if (entero && !Number.isInteger(valor)) return marcarError(input, "Solo unidades completas.");
    if (valor > maximo) return marcarError(input, `Máximo ${numero(maximo)}.`);
    return true;
}

/** Modal genérico de detalle (las páginas con <div id="modal">). */
function abrirModal(titulo, html) {
    document.getElementById("modalTitulo").textContent = titulo;
    document.getElementById("modalCuerpo").innerHTML = html;
    document.getElementById("modal").classList.add("abierto");
}
function cerrarModal() {
    const m = document.getElementById("modal");
    if (m) m.classList.remove("abierto");
}
document.addEventListener("keydown", e => { if (e.key === "Escape") cerrarModal(); });
document.addEventListener("click", e => { if (e.target && e.target.id === "modal") cerrarModal(); });

/* ---------------------------------------------------------------------
   App para teléfono: registra el service worker y muestra el botón
   "Instalar app" cuando el navegador lo permite (Android / Chrome).
   En iPhone: Safari → Compartir → "Agregar a pantalla de inicio".
   --------------------------------------------------------------------- */
if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => { /* sin https: sigue como página */ }));
}
let avisoInstalar = null;
window.addEventListener("beforeinstallprompt", e => {
    e.preventDefault();
    avisoInstalar = e;
    if (document.getElementById("btnInstalar")) return;
    const b = document.createElement("button");
    b.id = "btnInstalar";
    b.type = "button";
    b.className = "btn-instalar";
    b.textContent = "📲 Instalar app";
    b.addEventListener("click", async () => {
        b.remove();
        avisoInstalar.prompt();
        await avisoInstalar.userChoice;
        avisoInstalar = null;
    });
    document.body.append(b);
});
window.addEventListener("appinstalled", () => document.getElementById("btnInstalar")?.remove());
