/* =====================================================================
   Panel de producción (Sr. Kevin Gálvez)
   ===================================================================== */
montarSistema(["GERENTE"]);

let productos = [];
let insumos = [];

/* ---------------- Pestañas ---------------- */
function abrirPestana(nombre) {
    document.querySelectorAll(".pestanas button").forEach(b => b.classList.toggle("activa", b.dataset.tab === nombre));
    document.querySelectorAll(".panel-pestana").forEach(p => p.classList.toggle("activo", p.id === "tab-" + nombre));
}
document.querySelectorAll(".pestanas button").forEach(b => b.addEventListener("click", () => { location.hash = b.dataset.tab; }));
function pestanaDesdeHash() {
    const nombre = location.hash.slice(1);
    abrirPestana(document.getElementById("tab-" + nombre) ? nombre : "pedidos");
}
window.addEventListener("hashchange", pestanaDesdeHash);
pestanaDesdeHash();

/* ---------------- Pedidos ---------------- */
const filtroSucursal = document.getElementById("filtroSucursal");
const filtroEstado = document.getElementById("filtroEstado");
filtroSucursal.addEventListener("change", cargarPedidos);
filtroEstado.addEventListener("change", cargarPedidos);

async function cargarKpis() {
    try {
        const todos = await Api.pedidos();
        const contar = e => todos.filter(p => p.ESTADO === e).length;
        document.getElementById("kpiPend").textContent = contar("PENDIENTE");
        document.getElementById("kpiDesp").textContent = contar("DESPACHADO");
        document.getElementById("kpiEntr").textContent = contar("ENTREGADO");
        const monto = todos.filter(p => p.ESTADO === "PENDIENTE").reduce((s, p) => s + Number(p.TOTAL), 0);
        document.getElementById("kpiMonto").textContent = moneda(monto);
    } catch (e) { /* el error ya se muestra en la tabla */ }
}

async function cargarPedidos() {
    const tbody = document.getElementById("tablaPedidos");
    tbody.innerHTML = `<tr><td colspan="7" class="vacio">Cargando…</td></tr>`;
    try {
        const pedidos = await Api.pedidos(filtroSucursal.value, filtroEstado.value);
        tbody.innerHTML = pedidos.length ? pedidos.map(p => `
            <tr>
                <td>${p.ID_PEDIDO}</td>
                <td><strong>${escaparHTML(p.SUCURSAL)}</strong></td>
                <td class="fecha">${escaparHTML(p.FECHA_PEDIDO)}</td>
                <td class="fecha">${escaparHTML(p.FECHA_ENTREGA)}</td>
                <td>${badgeEstado(p.ESTADO)}</td>
                <td class="num">${moneda(p.TOTAL)}</td>
                <td class="num" style="white-space:nowrap">
                    <button class="btn btn-claro btn-chico" type="button" onclick="verDetalle(${p.ID_PEDIDO})">Ver</button>
                    ${p.ESTADO === "PENDIENTE" ? `
                        <button class="btn btn-primario btn-chico" type="button" onclick="accionPedido('despachar', ${p.ID_PEDIDO}, this)">Despachar</button>
                        <button class="btn btn-claro btn-chico" type="button" onclick="accionPedido('cancelar', ${p.ID_PEDIDO}, this)">Cancelar</button>` : ""}
                    ${p.ESTADO === "DESPACHADO" ? `
                        <button class="btn btn-verde btn-chico" type="button" onclick="accionPedido('entregar', ${p.ID_PEDIDO}, this)">Entregado</button>` : ""}
                </td>
            </tr>`).join("")
            : `<tr><td colspan="7" class="vacio">No hay pedidos con este filtro.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="7" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

async function accionPedido(accion, id, boton) {
    const textos = { despachar: "despachar", entregar: "marcar como entregado", cancelar: "cancelar" };
    if (!confirm(`¿Desea ${textos[accion]} el pedido #${id}?`)) return;
    try {
        const r = await conBoton(boton, () => Api[accion](id));
        avisar(r.msj);
        refrescarTodo();
    } catch (e) {
        avisar(e.message, "error");
    }
}

async function verDetalle(id) {
    document.getElementById("modalTitulo").textContent = `Pedido #${id}`;
    const cuerpo = document.getElementById("modalCuerpo");
    cuerpo.innerHTML = `<p class="texto-suave">Cargando…</p>`;
    document.getElementById("modal").classList.add("abierto");
    try {
        const lineas = await Api.detallePedido(id);
        const total = lineas.reduce((s, l) => s + Number(l.SUBTOTAL), 0);
        cuerpo.innerHTML = `
            <table>
                <thead><tr><th>Pan</th><th class="num">Cant.</th><th class="num">En stock</th><th class="num">Subtotal</th></tr></thead>
                <tbody>${lineas.map(l => {
                    const prod = productos.find(p => p.ID_PRODUCTO === l.ID_PRODUCTO);
                    const stock = prod ? Number(prod.STOCK_ACTUAL) : null;
                    const falta = stock !== null && stock < Number(l.CANTIDAD);
                    return `<tr>
                        <td>${escaparHTML(l.PRODUCTO)}</td>
                        <td class="num">${numero(l.CANTIDAD, 0)}</td>
                        <td class="num">${stock === null ? "–" : numero(stock, 0)} ${falta ? '<span class="badge badge-alerta">insuficiente</span>' : ""}</td>
                        <td class="num">${moneda(l.SUBTOTAL)}</td></tr>`;
                }).join("")}</tbody>
                <tfoot><tr class="fila-total"><td colspan="3">Total</td><td class="num">${moneda(total)}</td></tr></tfoot>
            </table>`;
    } catch (e) {
        cuerpo.innerHTML = `<div class="aviso aviso-alerta">${escaparHTML(e.message)}</div>`;
    }
}

/* ---------------- Producción ---------------- */
const selPan = document.getElementById("selPan");
const cantProducir = document.getElementById("cantProducir");
const fechaResumen = document.getElementById("fechaResumen");
fechaResumen.value = fechaISO(1);
fechaResumen.addEventListener("change", cargarResumen);

async function cargarProductos() {
    productos = await Api.productos();
    const seleccionado = selPan.value;
    selPan.innerHTML = `<option value="">— Seleccione —</option>` +
        productos.filter(p => p.ESTADO === "ACTIVO").map(p => `<option value="${p.ID_PRODUCTO}">${escaparHTML(p.NOMBRE)}</option>`).join("");
    selPan.value = seleccionado;

    document.getElementById("tablaProductos").innerHTML = productos.map(p => {
        const bajo = Number(p.STOCK_ACTUAL) <= Number(p.STOCK_MINIMO);
        return `<tr>
            <td><strong>${escaparHTML(p.NOMBRE)}</strong></td>
            <td class="num">${moneda(p.PRECIO)}</td>
            <td class="num">${numero(p.STOCK_ACTUAL, 0)}</td>
            <td class="num">${numero(p.STOCK_MINIMO, 0)}</td>
            <td>${barraStock(p.STOCK_ACTUAL, p.STOCK_MINIMO)}</td>
            <td>${bajo ? '<span class="badge badge-alerta">Bajo mínimo</span>' : '<span class="badge badge-ok">OK</span>'}</td>
        </tr>`;
    }).join("");
}

async function cargarResumen() {
    const tbody = document.getElementById("tablaResumen");
    try {
        const filas = (await Api.resumenProduccion(fechaResumen.value)).filter(f => Number(f.TOTAL_PEDIDO) > 0);
        tbody.innerHTML = filas.length ? filas.map(f => `
            <tr>
                <td><strong>${escaparHTML(f.NOMBRE)}</strong></td>
                <td class="num">${numero(f.TOTAL_PEDIDO, 0)}</td>
                <td class="num">${numero(f.STOCK_ACTUAL, 0)}</td>
                <td class="num">${Number(f.FALTA_PRODUCIR) > 0
                    ? `<span class="badge badge-alerta">${numero(f.FALTA_PRODUCIR, 0)}</span>`
                    : '<span class="badge badge-ok">0</span>'}</td>
                <td class="num">${Number(f.FALTA_PRODUCIR) > 0
                    ? `<button class="btn btn-claro btn-chico" type="button" onclick="prepararProduccion(${f.ID_PRODUCTO}, ${Number(f.FALTA_PRODUCIR)})">Producir</button>` : ""}</td>
            </tr>`).join("")
            : `<tr><td colspan="5" class="vacio">No hay pedidos pendientes para esta fecha.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="5" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

function prepararProduccion(idProducto, cantidad) {
    selPan.value = idProducto;
    cantProducir.value = Math.ceil(cantidad);
    limpiarError(selPan);
    limpiarError(cantProducir);
    mostrarReceta();
    cantProducir.focus();
}

// Receta del pan seleccionado, con lo que se va a consumir
selPan.addEventListener("change", () => { limpiarError(selPan); mostrarReceta(); });
cantProducir.addEventListener("input", mostrarReceta);

let recetaCache = {};
async function mostrarReceta() {
    const caja = document.getElementById("receta");
    const id = selPan.value;
    if (!id) { caja.innerHTML = ""; return; }
    try {
        if (!recetaCache[id]) recetaCache[id] = await Api.receta(id);
        if (!insumos.length) insumos = await Api.insumos();
        const cant = Number(cantProducir.value) > 0 ? Number(cantProducir.value) : 0;
        caja.innerHTML = `
            <div class="tabla-contenedor">
            <table>
                <thead><tr><th>Insumo</th><th class="num">Necesita</th><th class="num">Hay</th></tr></thead>
                <tbody>${recetaCache[id].map(r => {
                    const ins = insumos.find(i => i.ID_INSUMO === r.ID_INSUMO);
                    const necesita = Number(r.CANTIDAD) * cant;
                    const hay = ins ? Number(ins.STOCK_ACTUAL) : 0;
                    const alcanza = hay >= necesita;
                    return `<tr>
                        <td>${escaparHTML(r.INSUMO)}</td>
                        <td class="num">${numero(necesita, 3)} ${escaparHTML(r.UNIDAD_MEDIDA)}</td>
                        <td class="num">${alcanza ? numero(hay, 3) : `<span class="badge badge-alerta">${numero(hay, 3)}</span>`}</td>
                    </tr>`;
                }).join("")}</tbody>
            </table></div>`;
    } catch (e) {
        caja.innerHTML = `<div class="aviso aviso-alerta">${escaparHTML(e.message)}</div>`;
    }
}

document.getElementById("formProducir").addEventListener("submit", async ev => {
    ev.preventDefault();
    let valido = validarRequerido(selPan, "Seleccione el pan.");
    if (!validarCantidad(cantProducir, { entero: true, maximo: 10000 })) valido = false;
    if (!valido) return;

    const nombre = selPan.options[selPan.selectedIndex].text;
    try {
        await conBoton(document.getElementById("btnProducir"),
            () => Api.producir(Number(selPan.value), Number(cantProducir.value)));
        avisar(`Se produjeron ${cantProducir.value} unidades de ${nombre}.`);
        cantProducir.value = "";
        insumos = [];
        refrescarTodo();
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* ---------------- Insumos ---------------- */
const selInsumo = document.getElementById("selInsumo");
const cantCompra = document.getElementById("cantCompra");

async function cargarInsumos() {
    insumos = await Api.insumos();
    const seleccionado = selInsumo.value;
    selInsumo.innerHTML = `<option value="">— Seleccione —</option>` +
        insumos.filter(i => i.ESTADO === "ACTIVO")
            .map(i => `<option value="${i.ID_INSUMO}" data-unidad="${escaparHTML(i.UNIDAD_MEDIDA)}">${escaparHTML(i.NOMBRE)}</option>`).join("");
    selInsumo.value = seleccionado;

    document.getElementById("tablaInsumos").innerHTML = insumos.map(i => {
        const bajo = Number(i.STOCK_ACTUAL) <= Number(i.STOCK_MINIMO);
        return `<tr>
            <td><strong>${escaparHTML(i.NOMBRE)}</strong></td>
            <td class="num">${numero(i.STOCK_ACTUAL, 3)} ${escaparHTML(i.UNIDAD_MEDIDA)}</td>
            <td class="num">${numero(i.STOCK_MINIMO, 3)}</td>
            <td>${barraStock(i.STOCK_ACTUAL, i.STOCK_MINIMO)}</td>
            <td>${bajo ? '<span class="badge badge-alerta">Comprar</span>' : '<span class="badge badge-ok">OK</span>'}</td>
        </tr>`;
    }).join("");
}

selInsumo.addEventListener("change", () => {
    limpiarError(selInsumo);
    const op = selInsumo.options[selInsumo.selectedIndex];
    document.getElementById("unidadCompra").textContent = op && op.dataset.unidad ? `(${op.dataset.unidad})` : "";
});

document.getElementById("formCompra").addEventListener("submit", async ev => {
    ev.preventDefault();
    let valido = validarRequerido(selInsumo, "Seleccione el insumo.");
    if (!validarCantidad(cantCompra, { maximo: 100000 })) valido = false;
    if (!valido) return;

    try {
        const r = await conBoton(document.getElementById("btnCompra"),
            () => Api.comprarInsumo(Number(selInsumo.value), Number(cantCompra.value)));
        avisar(r.msj);
        cantCompra.value = "";
        refrescarTodo();
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* ---------------- Alertas (notificación al Sr. Kevin) ---------------- */
let alertasPrevias = null;

async function cargarAlertas() {
    try {
        const alertas = await Api.alertas();
        document.getElementById("contadorAlertas").textContent = alertas.length;
        document.getElementById("tablaAlertas").innerHTML = alertas.length ? alertas.map(a => `
            <tr>
                <td><span class="badge ${a.TIPO === "INSUMO" ? "badge-DESPACHADO" : "badge-PENDIENTE"}">${escaparHTML(a.TIPO)}</span></td>
                <td><strong>${escaparHTML(a.NOMBRE)}</strong></td>
                <td class="num" style="color:var(--rojo);font-weight:700">${numero(a.STOCK_ACTUAL, 3)}</td>
                <td class="num">${numero(a.STOCK_MINIMO, 3)}</td>
                <td>${escaparHTML(a.UNIDAD_MEDIDA)}</td>
            </tr>`).join("")
            : `<tr><td colspan="5" class="vacio">Todo en orden: ningún producto ni insumo está en su mínimo.</td></tr>`;

        const banner = document.getElementById("bannerAlertas");
        const insumosBajos = alertas.filter(a => a.TIPO === "INSUMO");
        if (insumosBajos.length) {
            banner.innerHTML = `<strong>Atención:</strong> ${insumosBajos.length} insumo(s) en existencia mínima: ` +
                insumosBajos.map(a => escaparHTML(a.NOMBRE)).join(", ") + ". Registre una compra.";
            banner.classList.remove("oculto");
        } else {
            banner.classList.add("oculto");
        }

        // Aviso emergente cuando aparece una alerta nueva
        const claves = new Set(alertas.map(a => a.TIPO + a.ID));
        if (alertasPrevias) {
            alertas.filter(a => !alertasPrevias.has(a.TIPO + a.ID))
                .forEach(a => avisar(`Alerta: ${a.NOMBRE} llegó a su existencia mínima (${numero(a.STOCK_ACTUAL, 3)} ${a.UNIDAD_MEDIDA}).`, "error"));
        }
        alertasPrevias = claves;
    } catch (e) {
        document.getElementById("tablaAlertas").innerHTML = `<tr><td colspan="5" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

/* ---------------- Carga inicial ---------------- */
async function refrescarTodo() {
    try {
        await Promise.all([cargarProductos(), cargarInsumos()]);
    } catch (e) {
        avisar(e.message, "error");
    }
    recetaCache = {};
    mostrarReceta();
    cargarPedidos();
    cargarKpis();
    cargarResumen();
    cargarAlertas();
}

(async function iniciar() {
    try {
        const sucursales = await Api.sucursales();
        filtroSucursal.innerHTML += sucursales.map(s => `<option value="${s.ID_SUCURSAL}">${escaparHTML(s.NOMBRE)}</option>`).join("");
    } catch (e) {
        avisar(e.message, "error");
    }
    refrescarTodo();
    setInterval(cargarAlertas, 60000);
})();
