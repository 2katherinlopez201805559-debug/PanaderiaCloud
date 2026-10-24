/* =====================================================================
   Pagos de pedidos
   - Sucursal: paga sus pedidos (queda EN REVISIÓN) y ve su historial.
   - Gerente : registra cobros (quedan CONFIRMADOS), revisa los pagos de
               las sucursales y ve las cuentas por cobrar.
   Formas de pago: EFECTIVO, TRANSFERENCIA, DEPOSITO, TARJETA.
   El sistema nunca pide números de tarjeta ni claves del banco.
   ===================================================================== */
const sesion = montarSistema(["GERENTE", "SUCURSAL"]);
const esGerente = sesion.ROL === "GERENTE";

const METODOS = {
    EFECTIVO:      { icono: "💵", texto: "Efectivo" },
    TRANSFERENCIA: { icono: "📱", texto: "Transferencia" },
    DEPOSITO:      { icono: "🏦", texto: "Depósito" },
    TARJETA:       { icono: "💳", texto: "Tarjeta" }
};
const ESTADO_PAGO = { PENDIENTE: "En revisión", CONFIRMADO: "Confirmado", RECHAZADO: "Rechazado" };
const ESTADO_CUENTA = { SIN_PAGO: "Sin pago", PARCIAL: "Parcial", EN_REVISION: "En revisión", PAGADO: "Pagado" };

/* Datos de cobro de la panadería (DE EJEMPLO para el proyecto: cámbielos por los reales) */
const CUENTA_PANADERIA = {
    nombre: "Panadería San Carlos, S.A.",
    cuentas: [
        { banco: "Banrural", tipo: "Monetaria", numero: "0000-000000-0 (ejemplo)" },
        { banco: "Banco Industrial", tipo: "Monetaria", numero: "000-000000-0 (ejemplo)" }
    ]
};

if (esGerente) {
    document.getElementById("tituloPagar").textContent = "Registrar cobro";
    document.getElementById("textoPagar").textContent =
        "Registre un pago que ya recibió (por ejemplo, efectivo del repartidor). Queda confirmado de inmediato.";
    document.getElementById("btnPagar").textContent = "Registrar cobro";
    document.getElementById("tituloSaldos").textContent = "Saldos de las sucursales";
    document.getElementById("colSucursalSaldo").classList.remove("oculto");
    document.getElementById("tituloHistorial").textContent = "Revisar pagos";
    document.getElementById("textoHistorial").textContent =
        "Confirme el pago cuando el dinero ya esté en caja o en la cuenta del banco; si no aparece, rechácelo.";
    document.getElementById("linkNuevoPago").textContent = "+ Registrar cobro";
    document.getElementById("filtroEstado").value = "PENDIENTE";
}
seccionesPorHash(esGerente ? "cuentas" : "pagar");

/* =====================================================================
   1. PAGAR
   ===================================================================== */
const selPedido = document.getElementById("selPedido");
const monto = document.getElementById("monto");
const banco = document.getElementById("banco");
const referencia = document.getElementById("referencia");
const observacion = document.getElementById("observacion");
let cuentas = [];

function metodoElegido() {
    const r = document.querySelector('input[name="metodo"]:checked');
    return r ? r.value : "";
}

function pedidoElegido() {
    return cuentas.find(c => String(c.ID_PEDIDO) === selPedido.value);
}

async function cargarCuentas() {
    try {
        cuentas = await Api.estadoCuenta();
        const conSaldo = cuentas.filter(c => Number(c.POR_PAGAR) > 0);
        const previo = selPedido.value || new URLSearchParams(location.search).get("pedido") || "";
        selPedido.innerHTML = conSaldo.length
            ? `<option value="">— Seleccione —</option>` + conSaldo.map(c => `
                <option value="${c.ID_PEDIDO}">#${c.ID_PEDIDO}${esGerente ? " · " + escaparHTML(c.SUCURSAL) : ""} · entrega ${escaparHTML(c.FECHA_ENTREGA || "—")} · saldo ${moneda(c.POR_PAGAR)}</option>`).join("")
            : `<option value="">No hay pedidos con saldo pendiente 🎉</option>`;
        if (previo && selPedido.querySelector(`option[value="${previo}"]`)) selPedido.value = previo;
        mostrarResumen();

        const tbody = document.getElementById("tablaSaldos");
        const columnas = esGerente ? 5 : 4;
        const visibles = cuentas.filter(c => c.ESTADO_PAGO !== "PAGADO").slice(0, 12);
        tbody.innerHTML = visibles.length ? visibles.map(c => `
            <tr>
                <td>#${c.ID_PEDIDO}</td>
                ${esGerente ? `<td>${escaparHTML(c.SUCURSAL)}</td>` : ""}
                <td class="num">${moneda(c.TOTAL)}</td>
                <td class="num"><strong>${moneda(c.SALDO)}</strong></td>
                <td>${badgeCuenta(c.ESTADO_PAGO)}</td>
            </tr>`).join("")
            : `<tr><td colspan="${columnas}" class="vacio">Todo está pagado. ¡Gracias!</td></tr>`;
    } catch (e) {
        selPedido.innerHTML = `<option value="">${escaparHTML(e.message)}</option>`;
        avisar(e.message, "error");
    }
}

function badgeCuenta(estado) {
    return `<span class="badge badge-${escaparHTML(estado)}">${escaparHTML(ESTADO_CUENTA[estado] || estado)}</span>`;
}

function mostrarResumen() {
    const caja = document.getElementById("resumenSaldo");
    const c = pedidoElegido();
    if (!c) { caja.classList.add("oculto"); return; }
    caja.classList.remove("oculto");
    caja.innerHTML = `
        <div><span>Total del pedido</span><strong>${moneda(c.TOTAL)}</strong></div>
        ${Number(c.CREDITO) > 0 ? `<div><span>− Devoluciones aprobadas</span><strong>${moneda(c.CREDITO)}</strong></div>` : ""}
        ${Number(c.PAGADO) > 0 ? `<div><span>− Pagos confirmados</span><strong>${moneda(c.PAGADO)}</strong></div>` : ""}
        ${Number(c.EN_REVISION) > 0 ? `<div><span>− Pagos en revisión</span><strong>${moneda(c.EN_REVISION)}</strong></div>` : ""}
        <div class="total"><span>Por pagar</span><strong>${moneda(c.POR_PAGAR)}</strong></div>`;
    monto.max = Number(c.POR_PAGAR);
    if (!monto.value) monto.value = Number(c.POR_PAGAR).toFixed(2);
}

selPedido.addEventListener("change", () => {
    limpiarError(selPedido);
    monto.value = "";
    limpiarError(monto);
    mostrarResumen();
});
document.getElementById("btnTodo").addEventListener("click", () => {
    const c = pedidoElegido();
    if (!c) { marcarError(selPedido, "Seleccione primero el pedido."); return; }
    monto.value = Number(c.POR_PAGAR).toFixed(2);
    limpiarError(monto);
});

/* Muestra u oculta campos e instrucciones según la forma de pago */
function prepararMetodo() {
    const m = metodoElegido();
    document.getElementById("errorMetodo").textContent = "";
    const mostrar = (id, si) => document.getElementById(id).classList.toggle("oculto", !si);
    mostrar("campoBanco", m === "TRANSFERENCIA" || m === "DEPOSITO");
    mostrar("campoTipoTarjeta", m === "TARJETA");
    mostrar("campoReferencia", m !== "" && m !== "EFECTIVO");

    const label = document.getElementById("labelReferencia");
    const ayuda = document.getElementById("ayudaReferencia");
    const caja = document.getElementById("instrucciones");
    const listaCuentas = CUENTA_PANADERIA.cuentas.map(c =>
        `<li><strong>${escaparHTML(c.banco)}</strong> · ${escaparHTML(c.tipo)} · ${escaparHTML(c.numero)}</li>`).join("");

    const textos = {
        EFECTIVO: `<strong>💵 Pago en efectivo</strong><br>Entregue el dinero al repartidor cuando llegue el pan y pida su recibo.
                   ${esGerente ? "Como gerente, el cobro queda confirmado al registrarlo." : "Gerencia lo confirmará al recibir el efectivo."}`,
        TRANSFERENCIA: `<strong>📱 Transferencia (banca en línea o app)</strong><br>Transfiera a nombre de <strong>${escaparHTML(CUENTA_PANADERIA.nombre)}</strong>:
                   <ul>${listaCuentas}</ul>Luego escriba el <strong>número de transferencia</strong> que le da el banco.`,
        DEPOSITO: `<strong>🏦 Depósito bancario</strong><br>Deposite en agencia o agente bancario a nombre de <strong>${escaparHTML(CUENTA_PANADERIA.nombre)}</strong>:
                   <ul>${listaCuentas}</ul>Escriba el <strong>número de la boleta</strong> de depósito.`,
        TARJETA: `<strong>💳 Tarjeta de débito o crédito</strong><br>Pague con el <strong>POS</strong> del repartidor o con el <strong>link de pago</strong> que le envía gerencia.
                   Escriba solo el <strong>número de autorización</strong> que aparece en el voucher o en el correo de confirmación.
                   <div class="aviso-seguro">🔒 Nunca escriba el número de la tarjeta, la fecha de vencimiento ni el código de seguridad.</div>`
    };
    caja.classList.toggle("oculto", !m);
    caja.innerHTML = textos[m] || "";

    const etiquetas = {
        TRANSFERENCIA: ["No. de transferencia *", "Aparece en el comprobante de la banca en línea."],
        DEPOSITO: ["No. de boleta *", "Número impreso en la boleta de depósito."],
        TARJETA: ["No. de autorización *", "Código del voucher o del correo de confirmación (no el de la tarjeta)."]
    };
    if (etiquetas[m]) {
        label.textContent = etiquetas[m][0];
        ayuda.textContent = etiquetas[m][1];
    }
}
document.querySelectorAll('input[name="metodo"]').forEach(r => r.addEventListener("change", prepararMetodo));
[monto, banco, referencia].forEach(i => i.addEventListener("input", () => limpiarError(i)));
banco.addEventListener("change", () => limpiarError(banco));

document.getElementById("formPago").addEventListener("submit", async ev => {
    ev.preventDefault();
    const c = pedidoElegido();
    let valido = validarRequerido(selPedido, "Seleccione el pedido.");
    const m = metodoElegido();
    if (!m) { document.getElementById("errorMetodo").textContent = "Seleccione la forma de pago."; valido = false; }

    const maximo = c ? Number(c.POR_PAGAR) : 100000;
    if (!validarCantidad(monto, { maximo })) valido = false;
    else if (!/^\d+(\.\d{1,2})?$/.test(monto.value.trim())) valido = marcarError(monto, "Máximo 2 decimales.");

    if ((m === "TRANSFERENCIA" || m === "DEPOSITO") && !validarRequerido(banco, "Seleccione el banco.")) valido = false;
    if (m && m !== "EFECTIVO") {
        limpiarError(referencia);
        const ref = referencia.value.trim();
        if (!/^[A-Za-z0-9-]{4,40}$/.test(ref)) {
            valido = marcarError(referencia, "Solo letras, números y guion (4 a 40).");
        } else if (m === "TARJETA" && /^\d{13,19}$/.test(ref)) {
            // Parece un número de tarjeta: no se acepta
            valido = marcarError(referencia, "Eso parece un número de tarjeta. Escriba solo el No. de autorización.");
        }
    }
    if (!valido) { avisar("Revise los campos marcados en rojo.", "error"); return; }

    let obs = observacion.value.trim();
    if (m === "TARJETA") obs = `[Tarjeta de ${document.getElementById("tipoTarjeta").value}] ${obs}`.trim();

    const resumen = `${METODOS[m].icono} ${METODOS[m].texto} por ${moneda(monto.value)} al pedido #${selPedido.value}`;
    if (!confirm(`¿Registrar ${resumen}?`)) return;

    try {
        const r = await conBoton(document.getElementById("btnPagar"), () => Api.registrarPago({
            ID_PEDIDO: Number(selPedido.value),
            METODO: m,
            MONTO: Number(monto.value),
            BANCO: (m === "TRANSFERENCIA" || m === "DEPOSITO") ? banco.value : null,
            REFERENCIA: m === "EFECTIVO" ? null : referencia.value.trim().toUpperCase(),
            OBSERVACION: obs
        }));
        avisar(r.msj);
        ev.target.reset();
        prepararMetodo();
        selPedido.value = "";
        await cargarCuentas();
        cargarPagos();
        if (esGerente) cargarCuentasCobrar();
        location.hash = "historial";
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* =====================================================================
   2. HISTORIAL / REVISIÓN
   ===================================================================== */
document.getElementById("filtroEstado").addEventListener("change", cargarPagos);
let cachePagos = {};

async function cargarPagos() {
    const tbody = document.getElementById("tablaPagos");
    tbody.innerHTML = `<tr><td colspan="9" class="vacio">Cargando…</td></tr>`;
    try {
        const lista = await Api.pagos(document.getElementById("filtroEstado").value);
        cachePagos = {};
        lista.forEach(p => { cachePagos[p.ID_PAGO] = p; });
        tbody.innerHTML = lista.length ? lista.map(p => `
            <tr>
                <td>${p.ID_PAGO}</td>
                <td>#${p.ID_PEDIDO}</td>
                <td>${escaparHTML(p.SUCURSAL)}</td>
                <td>${METODOS[p.METODO]?.icono || ""} ${escaparHTML(METODOS[p.METODO]?.texto || p.METODO)}</td>
                <td>${p.BANCO ? escaparHTML(p.BANCO) + "<br>" : ""}<span class="texto-suave">${escaparHTML(p.REFERENCIA || "—")}</span></td>
                <td class="num"><strong>${moneda(p.MONTO)}</strong></td>
                <td><span class="badge badge-${escaparHTML(p.ESTADO)}">${escaparHTML(ESTADO_PAGO[p.ESTADO] || p.ESTADO)}</span></td>
                <td class="fecha">${escaparHTML(p.FECHA_REGISTRO)}</td>
                <td class="num" style="white-space:nowrap">
                    <button class="btn btn-claro btn-chico" type="button" onclick="verPago(${p.ID_PAGO})">Ver</button>
                    ${esGerente && p.ESTADO === "PENDIENTE" ? `
                        <button class="btn btn-verde btn-chico" type="button" onclick="resolverPago(${p.ID_PAGO}, true, this)">Confirmar</button>
                        <button class="btn btn-claro btn-chico" type="button" onclick="resolverPago(${p.ID_PAGO}, false, this)">Rechazar</button>` : ""}
                </td>
            </tr>`).join("")
            : `<tr><td colspan="9" class="vacio">No hay pagos con este filtro.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="9" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

function verPago(id) {
    const p = cachePagos[id];
    if (!p) return;
    abrirModal(`Pago #${p.ID_PAGO}`, `
        <table><tbody>
            <tr><th>Pedido</th><td>#${p.ID_PEDIDO} · ${escaparHTML(p.SUCURSAL)}</td></tr>
            <tr><th>Forma de pago</th><td>${METODOS[p.METODO]?.icono || ""} ${escaparHTML(METODOS[p.METODO]?.texto || p.METODO)}</td></tr>
            <tr><th>Monto</th><td><strong>${moneda(p.MONTO)}</strong></td></tr>
            <tr><th>Banco</th><td>${escaparHTML(p.BANCO || "—")}</td></tr>
            <tr><th>Referencia</th><td>${escaparHTML(p.REFERENCIA || "—")}</td></tr>
            <tr><th>Observación</th><td>${escaparHTML(p.OBSERVACION || "—")}</td></tr>
            <tr><th>Registrado</th><td>${escaparHTML(p.FECHA_REGISTRO)} por ${escaparHTML(p.REGISTRADO_POR)}</td></tr>
            <tr><th>Estado</th><td><span class="badge badge-${escaparHTML(p.ESTADO)}">${escaparHTML(ESTADO_PAGO[p.ESTADO] || p.ESTADO)}</span>
                ${p.FECHA_RESOLUCION ? escaparHTML(p.FECHA_RESOLUCION) : ""}</td></tr>
        </tbody></table>`);
}

async function resolverPago(id, confirmar, boton) {
    const p = cachePagos[id];
    const texto = confirmar
        ? `¿Confirmar que recibió ${moneda(p.MONTO)} (${METODOS[p.METODO]?.texto}) de ${p.SUCURSAL}?`
        : `¿Rechazar el pago #${id}? El saldo del pedido vuelve a quedar pendiente.`;
    if (!confirm(texto)) return;
    try {
        const r = await conBoton(boton, () => confirmar ? Api.confirmarPago(id) : Api.rechazarPago(id));
        avisar(r.msj);
        cargarPagos();
        cargarCuentas();
        cargarCuentasCobrar();
    } catch (e) {
        avisar(e.message, "error");
    }
}

/* =====================================================================
   3. CUENTAS POR COBRAR (gerente)
   ===================================================================== */
const filtroSucursal = document.getElementById("filtroSucursal");
filtroSucursal.addEventListener("change", cargarCuentasCobrar);

async function cargarCuentasCobrar() {
    if (!esGerente) return;
    const tbody = document.getElementById("tablaCuentas");
    tbody.innerHTML = `<tr><td colspan="10" class="vacio">Cargando…</td></tr>`;
    try {
        const lista = await Api.estadoCuenta(filtroSucursal.value);
        const suma = campo => lista.reduce((s, c) => s + Math.max(0, Number(c[campo])), 0);
        document.getElementById("kpiPorCobrar").textContent = moneda(suma("POR_PAGAR"));
        document.getElementById("kpiRevision").textContent = moneda(suma("EN_REVISION"));
        document.getElementById("kpiCobrado").textContent = moneda(suma("PAGADO"));
        document.getElementById("kpiCredito").textContent = moneda(suma("CREDITO"));
        tbody.innerHTML = lista.length ? lista.map(c => `
            <tr>
                <td>#${c.ID_PEDIDO}</td>
                <td><strong>${escaparHTML(c.SUCURSAL)}</strong></td>
                <td class="fecha">${escaparHTML(c.FECHA_ENTREGA || "—")}</td>
                <td>${badgeEstado(c.ESTADO)}</td>
                <td class="num">${moneda(c.TOTAL)}</td>
                <td class="num">${Number(c.CREDITO) ? moneda(c.CREDITO) : "—"}</td>
                <td class="num">${Number(c.PAGADO) ? moneda(c.PAGADO) : "—"}</td>
                <td class="num">${Number(c.EN_REVISION) ? moneda(c.EN_REVISION) : "—"}</td>
                <td class="num"><strong>${moneda(c.SALDO)}</strong></td>
                <td>${badgeCuenta(c.ESTADO_PAGO)}</td>
            </tr>`).join("")
            : `<tr><td colspan="10" class="vacio">No hay pedidos.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="10" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

/* ---------------- Carga inicial ---------------- */
(async function iniciar() {
    if (esGerente) {
        try {
            const sucursales = await Api.sucursales();
            filtroSucursal.innerHTML += sucursales.map(s => `<option value="${s.ID_SUCURSAL}">${escaparHTML(s.NOMBRE)}</option>`).join("");
        } catch (e) { /* el filtro queda con "Todas" */ }
        cargarCuentasCobrar();
    }
    cargarCuentas();
    cargarPagos();
})();
