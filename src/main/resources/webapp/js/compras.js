/* =====================================================================
   Compras de insumos (facturas), comparación de precios y proveedores.
   Solo gerente.
   ===================================================================== */
montarSistema(["GERENTE"]);
seccionesPorHash("compras");

let insumosC = [];
let proveedoresC = [];
let comparacion = [];

const cInsumo = document.getElementById("cInsumo");
const cProveedor = document.getElementById("cProveedor");
const cCantidad = document.getElementById("cCantidad");
const cPrecio = document.getElementById("cPrecio");
const filtroProveedor = document.getElementById("filtroProveedor");
const desdeCompras = document.getElementById("desdeCompras");
const hastaCompras = document.getElementById("hastaCompras");

/* ---------------- Datos base ---------------- */
async function cargarBase() {
    [insumosC, proveedoresC] = await Promise.all([Api.insumos(), Api.proveedores()]);
    const selIns = cInsumo.value, selProv = cProveedor.value, selFiltro = filtroProveedor.value;
    cInsumo.innerHTML = `<option value="">— Seleccione —</option>` + insumosC.filter(i => i.ESTADO === "ACTIVO")
        .map(i => `<option value="${i.ID_INSUMO}">${escaparHTML(i.NOMBRE)} (${escaparHTML(i.UNIDAD_MEDIDA)})</option>`).join("");
    const activos = proveedoresC.filter(p => p.ESTADO === "ACTIVO");
    cProveedor.innerHTML = `<option value="">— Sin proveedor —</option>` +
        activos.map(p => `<option value="${p.ID_PROVEEDOR}">${escaparHTML(p.NOMBRE)}</option>`).join("");
    filtroProveedor.innerHTML = `<option value="">Todos los proveedores</option>` +
        proveedoresC.map(p => `<option value="${p.ID_PROVEEDOR}">${escaparHTML(p.NOMBRE)}</option>`).join("");
    cInsumo.value = selIns; cProveedor.value = selProv; filtroProveedor.value = selFiltro;
}

function precioDe(prov, idInsumo) {
    const pr = (prov?.PRECIOS || []).find(x => x.ID_INSUMO === idInsumo);
    return pr ? Number(pr.PRECIO) : null;
}

/* ---------------- Formulario de compra ---------------- */
function actualizarFormulario() {
    const ins = insumosC.find(i => i.ID_INSUMO === Number(cInsumo.value));
    document.getElementById("cUnidad").textContent = ins ? `(${ins.UNIDAD_MEDIDA})` : "";
    const sug = document.getElementById("cSugerencia");
    sug.textContent = "";
    if (ins) {
        // El más barato para este insumo
        const ofertas = proveedoresC.filter(p => p.ESTADO === "ACTIVO")
            .map(p => ({ p, precio: precioDe(p, ins.ID_INSUMO) })).filter(o => o.precio !== null)
            .sort((a, b) => a.precio - b.precio);
        if (ofertas.length) sug.textContent = `💡 Más barato: ${ofertas[0].p.NOMBRE} a ${moneda(ofertas[0].precio)} por ${ins.UNIDAD_MEDIDA}`;
        const prov = proveedoresC.find(p => p.ID_PROVEEDOR === Number(cProveedor.value));
        const precio = precioDe(prov, ins.ID_INSUMO);
        if (precio !== null && !cPrecio.dataset.tocado) cPrecio.value = precio.toFixed(2);
    }
    const total = Number(cCantidad.value) * Number(cPrecio.value);
    document.getElementById("cTotal").innerHTML = total > 0
        ? `<div class="total"><span>Total de la factura</span><strong>${moneda(total)}</strong></div>` : "";
}
cInsumo.addEventListener("change", () => { delete cPrecio.dataset.tocado; actualizarFormulario(); });
cProveedor.addEventListener("change", () => { delete cPrecio.dataset.tocado; actualizarFormulario(); });
cCantidad.addEventListener("input", actualizarFormulario);
cPrecio.addEventListener("input", () => { cPrecio.dataset.tocado = "1"; actualizarFormulario(); });

document.getElementById("formCompraFactura").addEventListener("submit", async ev => {
    ev.preventDefault();
    let ok = validarRequerido(cInsumo, "Seleccione el insumo.");
    ok = validarCantidad(cCantidad, { maximo: 100000 }) && ok;
    ok = validarCantidad(cPrecio, { maximo: 100000, permitirVacio: true }) && ok;
    if (!ok) return;
    const compra = {
        ID_INSUMO: Number(cInsumo.value),
        CANTIDAD: Number(cCantidad.value),
        ID_PROVEEDOR: cProveedor.value ? Number(cProveedor.value) : null,
        PRECIO_UNITARIO: cPrecio.value ? Number(cPrecio.value) : null,
        NO_FACTURA: document.getElementById("cFactura").value.trim() || null,
        OBSERVACION: document.getElementById("cObs").value.trim() || null
    };
    try {
        const r = await conBoton(document.getElementById("btnCompraFactura"), () => Api.registrarCompra(compra));
        avisar(r.msj);
        ev.target.reset();
        delete cPrecio.dataset.tocado;
        actualizarFormulario();
        await cargarBase();
        cargarCompras();
        cargarComparacion();
        cargarProveedores();
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* ---------------- Registro de compras ---------------- */
async function cargarCompras() {
    const tbody = document.getElementById("tablaCompras");
    try {
        const lista = await Api.compras(desdeCompras.value, hastaCompras.value, filtroProveedor.value);
        const total = lista.reduce((s, c) => s + Number(c.TOTAL || 0), 0);
        const porProv = {};
        lista.forEach(c => { if (c.PROVEEDOR) porProv[c.PROVEEDOR] = (porProv[c.PROVEEDOR] || 0) + Number(c.TOTAL || 0); });
        const top = Object.entries(porProv).sort((a, b) => b[1] - a[1])[0];
        document.getElementById("kpiCompras").textContent = lista.length;
        document.getElementById("kpiGastado").textContent = moneda(total);
        document.getElementById("kpiTopProv").textContent = top ? top[0].replace(" (ejemplo)", "") : "—";
        tbody.innerHTML = lista.length ? lista.map(c => `
            <tr>
                <td>${c.ID_COMPRA}</td>
                <td class="nowrap">${escaparHTML(c.FECHA)}</td>
                <td>${c.NO_FACTURA ? `<strong>${escaparHTML(c.NO_FACTURA)}</strong>` : '<span class="texto-suave">—</span>'}</td>
                <td>${escaparHTML(c.PROVEEDOR || "—")}</td>
                <td>${escaparHTML(c.INSUMO)}</td>
                <td class="num">${numero(c.CANTIDAD, 3)} ${escaparHTML(c.UNIDAD_MEDIDA || "")}</td>
                <td class="num">${c.PRECIO_UNITARIO ? moneda(c.PRECIO_UNITARIO) : "—"}</td>
                <td class="num"><strong>${Number(c.TOTAL) ? moneda(c.TOTAL) : "—"}</strong></td>
            </tr>`).join("")
            : `<tr><td colspan="8" class="vacio">No hay compras registradas en este período.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="8" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}
[filtroProveedor, desdeCompras, hastaCompras].forEach(el => el.addEventListener("change", cargarCompras));

/* ---------------- Comparar precios ---------------- */
async function cargarComparacion() {
    const tbody = document.getElementById("tablaPrecios");
    try {
        comparacion = await Api.compararPrecios();
        const provs = proveedoresC.filter(p => p.ESTADO === "ACTIVO");
        document.getElementById("cabeceraPrecios").innerHTML = `<tr><th>Insumo</th>${provs.map(p =>
            `<th class="num">${escaparHTML(p.NOMBRE.replace(" (ejemplo)", ""))}</th>`).join("")}<th class="num">Diferencia</th><th>Última compra</th></tr>`;
        tbody.innerHTML = comparacion.map(c => {
            const mejor = c.MEJOR ? Number(c.MEJOR.PRECIO) : null;
            const celdas = provs.map(p => {
                const o = c.OFERTAS.find(x => x.ID_PROVEEDOR === p.ID_PROVEEDOR);
                if (!o) return `<td class="num texto-suave">—</td>`;
                const esMejor = Number(o.PRECIO) === mejor && c.OFERTAS.length > 1;
                return `<td class="num ${esMejor ? "precio-mejor" : ""}">${moneda(o.PRECIO)}${esMejor ? " ✓" : ""}</td>`;
            }).join("");
            const u = c.ULTIMA_COMPRA;
            const ahorro = Number(c.AHORRO_POR_UNIDAD || 0);
            return `<tr>
                <td><strong>${escaparHTML(c.INSUMO)}</strong> <span class="texto-suave">/ ${escaparHTML(c.UNIDAD_MEDIDA)}</span></td>
                ${celdas}
                <td class="num">${c.DIFERENCIA ? moneda(c.DIFERENCIA) : "—"}</td>
                <td>${u ? `${moneda(u.PRECIO)} <span class="texto-suave">· ${escaparHTML(u.PROVEEDOR || "sin proveedor")}</span>
                    ${ahorro > 0 ? `<br><small style="color:var(--ok)">Podía ahorrar ${moneda(ahorro)} por ${escaparHTML(c.UNIDAD_MEDIDA)}</small>` : ""}` : '<span class="texto-suave">—</span>'}</td>
            </tr>`;
        }).join("");
    } catch (e) {
        tbody.innerHTML = `<tr><td class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

/* ---------------- Proveedores ---------------- */
async function cargarProveedores() {
    const tbody = document.getElementById("tablaProveedores");
    tbody.innerHTML = proveedoresC.length ? proveedoresC.map(p => `
        <tr>
            <td>${p.ID_PROVEEDOR}</td>
            <td><strong>${escaparHTML(p.NOMBRE)}</strong>${p.CORREO ? `<br><small class="texto-suave">${escaparHTML(p.CORREO)}</small>` : ""}</td>
            <td>${escaparHTML(p.TELEFONO || "—")}</td>
            <td>${escaparHTML(p.NIT || "—")}</td>
            <td class="num">${(p.PRECIOS || []).length}</td>
            <td>${p.ESTADO === "ACTIVO" ? '<span class="badge badge-ok">Activo</span>' : '<span class="badge badge-CANCELADO">Inactivo</span>'}</td>
            <td style="white-space:nowrap">
                <button class="btn btn-claro btn-chico" type="button" onclick="editarProveedor(${p.ID_PROVEEDOR})">✏️</button>
                <button class="btn btn-claro btn-chico" type="button" onclick="editarPrecios(${p.ID_PROVEEDOR})">💲 Precios</button>
            </td>
        </tr>`).join("") : `<tr><td colspan="7" class="vacio">No hay proveedores.</td></tr>`;
}

const formProv = document.getElementById("formProveedor");
const pNombre = document.getElementById("pNombre");
function limpiarProveedor() {
    formProv.reset();
    document.getElementById("pId").value = "";
    document.getElementById("tituloProveedor").textContent = "➕ Agregar proveedor";
    document.getElementById("campoEstadoProv").classList.add("oculto");
    document.getElementById("btnCancelarProv").classList.add("oculto");
    limpiarError(pNombre);
}
document.getElementById("btnCancelarProv").addEventListener("click", limpiarProveedor);

function editarProveedor(id) {
    const p = proveedoresC.find(x => x.ID_PROVEEDOR === id);
    if (!p) return;
    document.getElementById("pId").value = id;
    pNombre.value = p.NOMBRE || "";
    document.getElementById("pTelefono").value = p.TELEFONO || "";
    document.getElementById("pNit").value = p.NIT || "";
    document.getElementById("pCorreo").value = p.CORREO || "";
    document.getElementById("pDireccion").value = p.DIRECCION || "";
    document.getElementById("pEstado").value = p.ESTADO;
    document.getElementById("tituloProveedor").textContent = `✏️ Editar: ${p.NOMBRE}`;
    document.getElementById("campoEstadoProv").classList.remove("oculto");
    document.getElementById("btnCancelarProv").classList.remove("oculto");
    pNombre.focus();
}

formProv.addEventListener("submit", async ev => {
    ev.preventDefault();
    if (!validarRequerido(pNombre, "Escriba el nombre del proveedor.")) return;
    const id = document.getElementById("pId").value;
    const datos = {
        NOMBRE: pNombre.value.trim(),
        TELEFONO: document.getElementById("pTelefono").value.trim(),
        NIT: document.getElementById("pNit").value.trim(),
        CORREO: document.getElementById("pCorreo").value.trim(),
        DIRECCION: document.getElementById("pDireccion").value.trim(),
        ESTADO: id ? document.getElementById("pEstado").value : "ACTIVO"
    };
    try {
        const r = await conBoton(document.getElementById("btnProveedor"),
            () => id ? Api.actualizarProveedor(Number(id), datos) : Api.crearProveedor(datos));
        avisar(r.msj);
        limpiarProveedor();
        await cargarBase();
        cargarProveedores();
        cargarComparacion();
    } catch (e) {
        avisar(e.message, "error");
    }
});

function editarPrecios(id) {
    const p = proveedoresC.find(x => x.ID_PROVEEDOR === id);
    if (!p) return;
    abrirModal(`Precios de ${p.NOMBRE}`, `
        <p class="texto-suave" style="font-size:.88rem">Precio por unidad en quetzales. Deje vacío si este proveedor no lo vende.</p>
        <div class="tabla-contenedor"><table>
            <thead><tr><th>Insumo</th><th>Precio (Q)</th></tr></thead>
            <tbody>${insumosC.map(i => {
                const pr = precioDe(p, i.ID_INSUMO);
                return `<tr><td>${escaparHTML(i.NOMBRE)} <span class="texto-suave">/ ${escaparHTML(i.UNIDAD_MEDIDA)}</span></td>
                    <td><input type="number" min="0.01" step="0.01" style="max-width:120px" data-insumo="${i.ID_INSUMO}"
                        data-original="${pr ?? ""}" value="${pr ?? ""}"></td></tr>`;
            }).join("")}</tbody>
        </table></div>
        <button class="btn btn-primario mt" type="button" id="btnGuardarPrecios">Guardar precios</button>`);
    document.getElementById("btnGuardarPrecios").addEventListener("click", async e => {
        const cambios = [...document.querySelectorAll("#modalCuerpo input[data-insumo]")]
            .filter(inp => inp.value !== inp.dataset.original);
        if (cambios.some(inp => inp.value && !(Number(inp.value) > 0))) {
            avisar("Los precios deben ser mayores que 0.", "error");
            return;
        }
        if (!cambios.length) { cerrarModal(); return; }
        try {
            await conBoton(e.currentTarget, async () => {
                for (const inp of cambios) {
                    await Api.guardarPrecio(id, Number(inp.dataset.insumo), inp.value ? Number(inp.value) : null);
                }
            });
            avisar(`${cambios.length} precio(s) guardado(s).`);
            cerrarModal();
            await cargarBase();
            cargarProveedores();
            cargarComparacion();
        } catch (err) {
            avisar(err.message, "error");
        }
    });
}

/* ---------------- Inicio ---------------- */
(async function iniciar() {
    desdeCompras.value = fechaISO(-29);
    hastaCompras.value = fechaISO(0);
    try {
        await cargarBase();
    } catch (e) {
        avisar(e.message, "error");
    }
    actualizarFormulario();
    cargarCompras();
    cargarComparacion();
    cargarProveedores();
})();
