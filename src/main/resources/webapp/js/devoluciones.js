/* =====================================================================
   Devoluciones
   - Sucursal: registra devoluciones de sus pedidos despachados/entregados
   - Gerente : revisa y aprueba o rechaza
   ===================================================================== */
const sesion = montarSistema(["GERENTE", "SUCURSAL"]);
const esGerente = sesion.ROL === "GERENTE";

// El gerente no registra devoluciones: solo las revisa
if (esGerente) {
    const secNueva = document.getElementById("sec-nueva");
    secNueva.classList.add("oculto");
    secNueva.removeAttribute("data-seccion");
    secNueva.removeAttribute("id");
    document.getElementById("btnNueva").remove();
    document.getElementById("tituloHistorial").textContent = "Revisar devoluciones";
    document.getElementById("textoHistorial").textContent =
        "Al aprobar, el pan NO SOLICITADO o EXCEDENTE regresa al inventario; el DAÑADO se registra como merma.";
    document.getElementById("filtroEstado").value = "PENDIENTE";
}
seccionesPorHash(esGerente ? "historial" : "nueva");

const MOTIVOS = { NO_SOLICITADO: "No solicitado", EXCEDENTE: "Excedente", DANADO: "Dañado" };
let productos = [];
let lineasPedido = [];

/* ---------------- Formulario (sucursal) ---------------- */
const selPedido = document.getElementById("selPedido");
const selPan = document.getElementById("selPan");
const cantidad = document.getElementById("cantidad");
const observacion = document.getElementById("observacion");

function motivoElegido() {
    const r = document.querySelector('input[name="motivo"]:checked');
    return r ? r.value : "";
}

async function iniciarFormulario() {
    try {
        const [todos, panes] = await Promise.all([Api.pedidos(), Api.productos()]);
        productos = panes.filter(p => p.ESTADO === "ACTIVO");
        const recibidos = todos.filter(p => p.ESTADO === "DESPACHADO" || p.ESTADO === "ENTREGADO");
        selPedido.innerHTML = recibidos.length
            ? `<option value="">— Seleccione —</option>` + recibidos.map(p =>
                `<option value="${p.ID_PEDIDO}">#${p.ID_PEDIDO} · entrega ${escaparHTML(p.FECHA_ENTREGA)} · ${escaparHTML(p.ESTADO)}</option>`).join("")
            : `<option value="">No tiene pedidos despachados</option>`;

        const desdeUrl = new URLSearchParams(location.search).get("pedido");
        if (desdeUrl && selPedido.querySelector(`option[value="${desdeUrl}"]`)) {
            selPedido.value = desdeUrl;
            cargarDetallePedido();
        }
    } catch (e) {
        avisar(e.message, "error");
    }
}

selPedido.addEventListener("change", () => { limpiarError(selPedido); cargarDetallePedido(); });
document.querySelectorAll('input[name="motivo"]').forEach(r => r.addEventListener("change", () => {
    document.getElementById("errorMotivo").textContent = "";
    llenarPanes();
}));
observacion.addEventListener("input", () => {
    document.getElementById("contador").textContent = `${observacion.value.length}/250`;
});

async function cargarDetallePedido() {
    const caja = document.getElementById("detallePedido");
    lineasPedido = [];
    if (!selPedido.value) {
        caja.innerHTML = `<p class="texto-suave">Seleccione un pedido.</p>`;
        llenarPanes();
        return;
    }
    caja.innerHTML = `<p class="texto-suave">Cargando…</p>`;
    try {
        lineasPedido = await Api.detallePedido(selPedido.value);
        caja.innerHTML = `
            <table>
                <thead><tr><th>Pan</th><th class="num">Pedido</th></tr></thead>
                <tbody>${lineasPedido.map(l => `<tr><td>${escaparHTML(l.PRODUCTO)}</td><td class="num">${numero(l.CANTIDAD, 0)}</td></tr>`).join("")}</tbody>
            </table>`;
    } catch (e) {
        caja.innerHTML = `<div class="aviso aviso-alerta">${escaparHTML(e.message)}</div>`;
    }
    llenarPanes();
}

/**
 * NO_SOLICITADO -> solo panes que NO estaban en el pedido.
 * EXCEDENTE / DANADO -> solo panes que SÍ estaban en el pedido.
 */
function llenarPanes() {
    const motivo = motivoElegido();
    if (!motivo || !selPedido.value) {
        selPan.innerHTML = `<option value="">${!selPedido.value ? "Seleccione primero el pedido" : "Seleccione el motivo"}</option>`;
        return;
    }
    const enPedido = new Set(lineasPedido.map(l => l.ID_PRODUCTO));
    const lista = motivo === "NO_SOLICITADO"
        ? productos.filter(p => !enPedido.has(p.ID_PRODUCTO))
        : productos.filter(p => enPedido.has(p.ID_PRODUCTO));
    selPan.innerHTML = lista.length
        ? `<option value="">— Seleccione —</option>` + lista.map(p => {
            const linea = lineasPedido.find(l => l.ID_PRODUCTO === p.ID_PRODUCTO);
            const extra = linea ? ` (pidió ${numero(linea.CANTIDAD, 0)})` : "";
            return `<option value="${p.ID_PRODUCTO}">${escaparHTML(p.NOMBRE)}${extra}</option>`;
        }).join("")
        : `<option value="">No hay panes para este motivo</option>`;
}

document.getElementById("formDevolucion")?.addEventListener("submit", async ev => {
    ev.preventDefault();
    let valido = validarRequerido(selPedido, "Seleccione el pedido.");
    const motivo = motivoElegido();
    if (!motivo) { document.getElementById("errorMotivo").textContent = "Seleccione el motivo."; valido = false; }
    if (!validarRequerido(selPan, "Seleccione el pan.")) valido = false;

    let maximo = 5000;
    const linea = lineasPedido.find(l => String(l.ID_PRODUCTO) === selPan.value);
    if (motivo !== "NO_SOLICITADO" && linea) maximo = Number(linea.CANTIDAD);
    if (!validarCantidad(cantidad, { entero: true, maximo })) valido = false;

    limpiarError(observacion);
    if (motivo === "DANADO" && observacion.value.trim().length < 5) {
        valido = marcarError(observacion, "Describa el daño (mínimo 5 caracteres).");
    }
    if (!valido) { avisar("Revise los campos marcados en rojo.", "error"); return; }

    try {
        const r = await conBoton(document.getElementById("btnRegistrar"), () => Api.registrarDevolucion({
            ID_PEDIDO: Number(selPedido.value),
            ID_PRODUCTO: Number(selPan.value),
            CANTIDAD: Number(cantidad.value),
            MOTIVO: motivo,
            OBSERVACION: observacion.value.trim()
        }));
        avisar(r.msj);
        ev.target.reset();
        document.getElementById("contador").textContent = "0/250";
        llenarPanes();
        cargarDevoluciones();
        location.hash = "historial";
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* ---------------- Historial / revisión ---------------- */
document.getElementById("filtroEstado").addEventListener("change", cargarDevoluciones);

async function cargarDevoluciones() {
    const tbody = document.getElementById("tablaDevoluciones");
    tbody.innerHTML = `<tr><td colspan="9" class="vacio">Cargando…</td></tr>`;
    try {
        const lista = await Api.devoluciones(document.getElementById("filtroEstado").value);
        cacheDevoluciones = {};
        lista.forEach(d => { cacheDevoluciones[d.ID_DEVOLUCION] = d; });
        tbody.innerHTML = lista.length ? lista.map(d => `
            <tr>
                <td>${d.ID_DEVOLUCION}</td>
                <td>#${d.ID_PEDIDO}</td>
                <td>${escaparHTML(d.SUCURSAL)}</td>
                <td><strong>${escaparHTML(d.PRODUCTO)}</strong></td>
                <td class="num">${numero(d.CANTIDAD, 0)}</td>
                <td>${escaparHTML(MOTIVOS[d.MOTIVO] || d.MOTIVO)}</td>
                <td>${badgeEstado(d.ESTADO)}</td>
                <td class="num">${moneda(d.MONTO)}</td>
                <td class="num" style="white-space:nowrap">
                    <button class="btn btn-claro btn-chico" type="button" onclick="verDevolucion(${d.ID_DEVOLUCION})">Ver</button>
                    ${esGerente && d.ESTADO === "PENDIENTE" ? `
                        <button class="btn btn-verde btn-chico" type="button" onclick="resolver(${d.ID_DEVOLUCION}, true, this)">Aprobar</button>
                        <button class="btn btn-claro btn-chico" type="button" onclick="resolver(${d.ID_DEVOLUCION}, false, this)">Rechazar</button>` : ""}
                </td>
            </tr>`).join("")
            : `<tr><td colspan="9" class="vacio">No hay devoluciones con este filtro.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="9" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

let cacheDevoluciones = {};
function verDevolucion(id) {
    const d = cacheDevoluciones[id];
    if (!d) return;
    abrirModal(`Devolución #${d.ID_DEVOLUCION}`, `
        <table>
            <tbody>
                <tr><th>Pedido</th><td>#${d.ID_PEDIDO} · ${escaparHTML(d.SUCURSAL)}</td></tr>
                <tr><th>Pan</th><td>${escaparHTML(d.PRODUCTO)} — ${numero(d.CANTIDAD, 0)} u.</td></tr>
                <tr><th>Motivo</th><td>${escaparHTML(MOTIVOS[d.MOTIVO] || d.MOTIVO)}</td></tr>
                <tr><th>Observación</th><td>${escaparHTML(d.OBSERVACION || "—")}</td></tr>
                <tr><th>Monto</th><td>${moneda(d.MONTO)}</td></tr>
                <tr><th>Registrada</th><td>${escaparHTML(d.FECHA_REGISTRO)} por ${escaparHTML(d.REGISTRADO_POR)}</td></tr>
                <tr><th>Estado</th><td>${badgeEstado(d.ESTADO)} ${d.FECHA_RESOLUCION ? escaparHTML(d.FECHA_RESOLUCION) : ""}</td></tr>
            </tbody>
        </table>`);
}

async function resolver(id, aprobar, boton) {
    if (!confirm(`¿${aprobar ? "Aprobar" : "Rechazar"} la devolución #${id}?`)) return;
    try {
        const r = await conBoton(boton, () => aprobar ? Api.aprobarDevolucion(id) : Api.rechazarDevolucion(id));
        avisar(r.msj);
        cargarDevoluciones();
    } catch (e) {
        avisar(e.message, "error");
    }
}

if (!esGerente) iniciarFormulario();
cargarDevoluciones();
