/* =====================================================================
   Pedidos: la sucursal pide pan (su sucursal queda fija);
   el gerente puede registrar un pedido para cualquier sucursal.
   ===================================================================== */
const sesion = montarSistema(["GERENTE", "SUCURSAL"]);
const esGerente = sesion.ROL === "GERENTE";
seccionesPorHash("nuevo");

// El gerente revisa los pedidos en su panel
if (esGerente && location.hash === "#historial") location.replace("panel.html#pedidos");

const selSucursal = document.getElementById("selSucursal");
const fechaEntrega = document.getElementById("fechaEntrega");
let productos = [];

fechaEntrega.min = fechaISO(0);
fechaEntrega.value = fechaISO(1);

async function iniciar() {
    try {
        const [sucursales, panes] = await Promise.all([Api.sucursales(), Api.productos()]);
        if (esGerente) {
            selSucursal.innerHTML = `<option value="">— Seleccione —</option>` +
                sucursales.filter(s => s.ESTADO === "ACTIVO")
                    .map(s => `<option value="${s.ID_SUCURSAL}">${escaparHTML(s.NOMBRE)}</option>`).join("");
        } else {
            // Sucursal fija: no puede pedir a nombre de otra
            selSucursal.innerHTML = `<option value="${sesion.ID_SUCURSAL}">${escaparHTML(sesion.SUCURSAL)}</option>`;
            selSucursal.disabled = true;
        }
        productos = panes.filter(p => p.ESTADO === "ACTIVO");
        pintarPanes();
    } catch (e) {
        avisar(e.message, "error");
        document.getElementById("tablaPanes").innerHTML = `<tr><td colspan="4" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
    if (!esGerente) cargarPedidos();
}

function pintarPanes() {
    document.getElementById("tablaPanes").innerHTML = productos.map(p => `
        <tr>
            <td><strong>${escaparHTML(p.NOMBRE)}</strong></td>
            <td class="num">${moneda(p.PRECIO)}</td>
            <td class="num">
                <div class="campo" style="margin:0;align-items:flex-end">
                    <input class="cantidad" type="number" min="0" step="1" inputmode="numeric"
                           data-id="${p.ID_PRODUCTO}" data-precio="${p.PRECIO}" placeholder="0"
                           aria-label="Cantidad de ${escaparHTML(p.NOMBRE)}">
                    <span class="msg-error"></span>
                </div>
            </td>
            <td class="num" data-subtotal="${p.ID_PRODUCTO}">Q 0.00</td>
        </tr>`).join("");
    document.querySelectorAll("input.cantidad").forEach(i => i.addEventListener("input", recalcular));
}

function recalcular() {
    let total = 0, unidades = 0;
    document.querySelectorAll("input.cantidad").forEach(i => {
        const cant = Number(i.value) > 0 ? Number(i.value) : 0;
        const sub = cant * Number(i.dataset.precio);
        total += sub;
        unidades += cant;
        document.querySelector(`[data-subtotal="${i.dataset.id}"]`).textContent = moneda(sub);
    });
    document.getElementById("totalPedido").textContent = moneda(total);
    document.getElementById("totalUnidades").textContent = numero(unidades, 0) + " u.";
}

document.getElementById("formPedido").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const errorDetalle = document.getElementById("errorDetalle");
    errorDetalle.textContent = "";

    let valido = validarRequerido(selSucursal, "Seleccione la sucursal.");
    if (!validarRequerido(fechaEntrega, "Seleccione la fecha de entrega.")) valido = false;
    else if (fechaEntrega.value < fechaISO(0)) valido = marcarError(fechaEntrega, "La fecha no puede ser anterior a hoy.");

    const detalle = [];
    document.querySelectorAll("input.cantidad").forEach(i => {
        if (!validarCantidad(i, { entero: true, maximo: 5000, permitirVacio: true })) { valido = false; return; }
        if (String(i.value).trim() && Number(i.value) > 0) {
            detalle.push({ ID_PRODUCTO: Number(i.dataset.id), CANTIDAD: Number(i.value) });
        }
    });
    if (detalle.length === 0) {
        errorDetalle.textContent = "Ingrese la cantidad de al menos un pan.";
        valido = false;
    }
    if (!valido) { avisar("Revise los campos marcados en rojo.", "error"); return; }

    try {
        const r = await conBoton(document.getElementById("btnEnviar"), () => Api.registrarPedido({
            ID_SUCURSAL: Number(selSucursal.value),
            FECHA_ENTREGA: fechaEntrega.value,
            DETALLE: detalle
        }));
        avisar(r.msj);
        limpiarCantidades();
        if (!esGerente) {
            cargarPedidos();
            location.hash = "historial";
        }
    } catch (e) {
        avisar(e.message, "error");
    }
});

function limpiarCantidades() {
    document.querySelectorAll("input.cantidad").forEach(i => { i.value = ""; limpiarError(i); });
    document.getElementById("errorDetalle").textContent = "";
    recalcular();
}
document.getElementById("btnLimpiar").addEventListener("click", limpiarCantidades);

/* ---------- Historial de la sucursal ---------- */
document.getElementById("filtroEstado").addEventListener("change", cargarPedidos);

async function cargarPedidos() {
    const tbody = document.getElementById("tablaPedidos");
    tbody.innerHTML = `<tr><td colspan="6" class="vacio">Cargando…</td></tr>`;
    try {
        const pedidos = await Api.pedidos(null, document.getElementById("filtroEstado").value);
        tbody.innerHTML = pedidos.length ? pedidos.map(p => `
            <tr>
                <td>${p.ID_PEDIDO}</td>
                <td class="fecha">${escaparHTML(p.FECHA_PEDIDO)}</td>
                <td class="fecha">${escaparHTML(p.FECHA_ENTREGA)}</td>
                <td>${badgeEstado(p.ESTADO)}</td>
                <td class="num">${moneda(p.TOTAL)}</td>
                <td class="num" style="white-space:nowrap">
                    <button class="btn btn-claro btn-chico" type="button" onclick="verDetalle(${p.ID_PEDIDO})">Ver</button>
                    ${p.ESTADO === "PENDIENTE" ? `<button class="btn btn-claro btn-chico" type="button" onclick="accion('cancelar', ${p.ID_PEDIDO}, this)">Cancelar</button>` : ""}
                    ${p.ESTADO === "DESPACHADO" ? `<button class="btn btn-verde btn-chico" type="button" onclick="accion('entregar', ${p.ID_PEDIDO}, this)">Recibido</button>` : ""}
                    ${["DESPACHADO", "ENTREGADO"].includes(p.ESTADO) ? `<a class="btn btn-claro btn-chico" href="devoluciones.html?pedido=${p.ID_PEDIDO}#nueva">Devolver</a>` : ""}
                    ${p.ESTADO !== "CANCELADO" ? `<a class="btn btn-rojo btn-chico" href="pagos.html?pedido=${p.ID_PEDIDO}#pagar">💳 Pagar</a>` : ""}
                </td>
            </tr>`).join("")
            : `<tr><td colspan="6" class="vacio">No hay pedidos con este filtro.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

async function accion(tipo, id, boton) {
    const texto = { cancelar: `¿Cancelar el pedido #${id}?`, entregar: `¿Confirma que recibió el pedido #${id}?` }[tipo];
    if (!confirm(texto)) return;
    try {
        const r = await conBoton(boton, () => Api[tipo](id));
        avisar(r.msj);
        cargarPedidos();
    } catch (e) { avisar(e.message, "error"); }
}

async function verDetalle(id) {
    abrirModal(`Pedido #${id}`, `<p class="texto-suave">Cargando…</p>`);
    try {
        const lineas = await Api.detallePedido(id);
        const total = lineas.reduce((s, l) => s + Number(l.SUBTOTAL), 0);
        document.getElementById("modalCuerpo").innerHTML = `
            <table>
                <thead><tr><th>Pan</th><th class="num">Cant.</th><th class="num">Precio</th><th class="num">Subtotal</th></tr></thead>
                <tbody>${lineas.map(l => `
                    <tr><td>${escaparHTML(l.PRODUCTO)}</td><td class="num">${numero(l.CANTIDAD, 0)}</td>
                        <td class="num">${moneda(l.PRECIO_UNITARIO)}</td><td class="num">${moneda(l.SUBTOTAL)}</td></tr>`).join("")}
                </tbody>
                <tfoot><tr class="fila-total"><td colspan="3">Total</td><td class="num">${moneda(total)}</td></tr></tfoot>
            </table>`;
    } catch (e) {
        document.getElementById("modalCuerpo").innerHTML = `<div class="aviso aviso-alerta">${escaparHTML(e.message)}</div>`;
    }
}

iniciar();
