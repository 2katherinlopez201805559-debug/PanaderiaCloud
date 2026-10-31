/* =====================================================================
   Panel del gerente — pestañas nuevas:
   - Alertas: capacidad de producción, pan por vencer y aviso por correo
   - Estadísticas: panes más vendidos, sucursal que más vende, registro de ventas
   - Sucursales: lista y botón para agregar una sucursal nueva
   (Se carga después de panel.js)
   ===================================================================== */

/* ---------------- Alertas: capacidad, pan por vencer, correo ---------------- */
const TEXTO_PRODUCCION = {
    OK: '<span class="badge badge-ok">Alcanza</span>',
    NO_ALCANZA: '<span class="badge badge-PENDIENTE">No alcanza para los pedidos</span>',
    SIN_INSUMOS: '<span class="badge badge-alerta">Sin insumos</span>'
};

async function cargarCapacidad() {
    const tbody = document.getElementById("tablaCapacidad");
    try {
        const filas = await Api.capacidad();
        tbody.innerHTML = filas.length ? filas.map(c => `
            <tr>
                <td><strong>${escaparHTML(c.NOMBRE)}</strong></td>
                <td class="num" style="font-weight:700;${c.ESTADO_PRODUCCION === "OK" ? "" : "color:var(--rojo)"}">${numero(c.SE_PUEDEN_HACER, 0)}</td>
                <td>${escaparHTML(c.INSUMO_LIMITANTE || "—")}</td>
                <td class="num">${Number(c.FALTA_PEDIDOS) > 0 ? numero(c.FALTA_PEDIDOS, 0) : "—"}</td>
                <td class="num">${numero(c.STOCK_ACTUAL, 0)} / ${numero(c.STOCK_MINIMO, 0)}${c.BAJO_MINIMO ? ' <span class="badge badge-alerta">bajo</span>' : ""}</td>
                <td>${TEXTO_PRODUCCION[c.ESTADO_PRODUCCION] || ""}</td>
            </tr>`).join("")
            : `<tr><td colspan="6" class="vacio">No hay panes activos con receta.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

async function cargarPorVencer() {
    const tbody = document.getElementById("tablaVencer");
    try {
        const filas = await Api.porVencer();
        tbody.innerHTML = filas.length ? filas.map(v => `
            <tr>
                <td><strong>${escaparHTML(v.NOMBRE)}</strong></td>
                <td class="num">${numero(v.CANTIDAD, 0)}</td>
                <td>${escaparHTML(v.FECHA_PRODUCCION)}</td>
                <td class="num">${numero(v.DIAS, 0)}</td>
                <td>${v.ESTADO === "VENCIDO"
                    ? '<span class="badge badge-alerta">Vencido: retirarlo</span>'
                    : '<span class="badge badge-PENDIENTE">Vence hoy: despacharlo primero</span>'}</td>
            </tr>`).join("")
            : `<tr><td colspan="5" class="vacio">Ningún pan está por vencer.</td></tr>`;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="5" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

async function cargarEstadoCorreo() {
    const caja = document.getElementById("estadoTelegram");
    try {
        const r = await Api.estadoAvisos();
        document.getElementById("vidaPan").textContent = `Vida útil del pan: ${r.DIAS_VIDA_PAN} día(s)`;
        const tg = r.TELEGRAM || {};
        const conectar = document.getElementById("btnConectarTelegram");
        document.getElementById("pasosTelegram").classList.toggle("oculto", !!tg.CONECTADO);
        conectar.classList.toggle("oculto", !tg.CONFIGURADO);
        if (tg.CONECTADO) {
            caja.className = "estado-telegram ok";
            caja.innerHTML = `✅ Telegram conectado${tg.NOMBRE ? ` con <strong>${escaparHTML(tg.NOMBRE)}</strong>` : ""}.`;
            conectar.textContent = "Volver a conectar";
        } else if (tg.CONFIGURADO) {
            caja.className = "estado-telegram";
            caja.innerHTML = "🤖 El bot ya está configurado. Falta el paso 3 y 4: que el propietario le escriba <code>/start</code> y presionar <strong>Conectar Telegram</strong>.";
        } else {
            caja.className = "estado-telegram";
            caja.innerHTML = "ℹ️ Telegram todavía no está configurado (es opcional). Por ahora los avisos se ven en la campanita 🔔.";
        }
        document.getElementById("btnProbarCorreo").classList.toggle("oculto", !tg.CONECTADO && !r.CORREO_CONFIGURADO);
        document.getElementById("estadoCorreo").textContent = r.CORREO_CONFIGURADO
            ? `📧 También se envía por correo a ${r.PARA}.` : "";
    } catch (e) {
        caja.textContent = e.message;
    }
}

document.getElementById("btnProbarCorreo").addEventListener("click", async e => {
    try {
        const r = await conBoton(e.currentTarget, () => Api.enviarAviso());
        avisar(r.msj);
    } catch (err) {
        avisar(err.message, "error");
    }
});

document.getElementById("btnConectarTelegram").addEventListener("click", async e => {
    try {
        const r = await conBoton(e.currentTarget, () => Api.conectarTelegram());
        avisar(r.msj);
        cargarEstadoCorreo();
    } catch (err) {
        avisar(err.message, "error");
    }
});

function cargarAvisos() {
    cargarCapacidad();
    cargarPorVencer();
}

/* ---------------- Estadísticas ---------------- */
const rangoEst = document.getElementById("rangoEst");
const desdeEst = document.getElementById("desdeEst");
const hastaEst = document.getElementById("hastaEst");

function rangoElegido() {
    const v = rangoEst.value;
    if (v === "todo") return ["", ""];
    if (v === "otro") return [desdeEst.value, hastaEst.value];
    if (v === "mes") return [fechaISO(0).slice(0, 8) + "01", fechaISO(0)];
    return [fechaISO(-(Number(v) - 1)), fechaISO(0)];
}

function barras(filas, nombre, valor, formato, detalle) {
    if (!filas.length) return `<p class="vacio">Todavía no hay ventas en este período.</p>`;
    const max = Math.max(...filas.map(f => Number(f[valor])), 1);
    return filas.map((f, i) => `
        <div class="ranking-fila ${i === 0 ? "primero" : ""}">
            <span class="ranking-pos">${i === 0 ? "🏆" : i + 1}</span>
            <div class="ranking-cuerpo">
                <div class="ranking-texto"><strong>${escaparHTML(f[nombre])}</strong><span>${formato(f[valor])}</span></div>
                <div class="ranking-barra"><span style="width:${Math.max(2, Math.round(Number(f[valor]) / max * 100))}%"></span></div>
                ${detalle ? `<small class="texto-suave">${detalle(f)}</small>` : ""}
            </div>
        </div>`).join("");
}

async function cargarEstadisticas() {
    const [desde, hasta] = rangoElegido();
    try {
        const e = await Api.estadisticas(desde, hasta);
        document.getElementById("kpiVendido").textContent = moneda(e.TOTAL_VENDIDO);
        document.getElementById("kpiUnidades").textContent = numero(e.PANES_VENDIDOS, 0);
        document.getElementById("kpiPanTop").textContent = e.PAN_TOP ? e.PAN_TOP.NOMBRE : "—";
        document.getElementById("kpiSucTop").textContent = e.SUCURSAL_TOP ? e.SUCURSAL_TOP.SUCURSAL : "—";

        document.getElementById("rankingPanes").innerHTML = barras(e.PANES, "NOMBRE", "NETO_CANTIDAD",
            v => numero(v, 0) + " unid.", f => moneda(f.NETO_MONTO) + (Number(f.DEVUELTO_CANTIDAD) ? ` · ${numero(f.DEVUELTO_CANTIDAD, 0)} devueltos` : ""));
        const sucPorMonto = [...e.SUCURSALES].sort((a, b) => b.NETO_MONTO - a.NETO_MONTO);
        document.getElementById("rankingSucursales").innerHTML = barras(sucPorMonto, "SUCURSAL", "NETO_MONTO",
            v => moneda(v), f => `${f.PEDIDOS} pedido(s) · más pide: ${escaparHTML(f.PAN_TOP || "—")}`);

        document.getElementById("tablaEstSucursales").innerHTML = sucPorMonto.length ? sucPorMonto.map((s, i) => `
            <tr>
                <td>${i + 1}</td>
                <td><strong>${escaparHTML(s.SUCURSAL)}</strong></td>
                <td class="num">${s.PEDIDOS}</td>
                <td class="num">${numero(s.NETO_CANTIDAD, 0)}</td>
                <td class="num">${moneda(s.MONTO)}</td>
                <td class="num">${Number(s.DEVUELTO_MONTO) ? "− " + moneda(s.DEVUELTO_MONTO) : "—"}</td>
                <td class="num"><strong>${moneda(s.NETO_MONTO)}</strong></td>
                <td>${escaparHTML(s.PAN_TOP || "—")} ${s.PAN_TOP ? `<span class="texto-suave">(${numero(s.PAN_TOP_CANTIDAD, 0)})</span>` : ""}</td>
            </tr>`).join("") : `<tr><td colspan="8" class="vacio">Sin ventas en este período.</td></tr>`;

        document.getElementById("totalVentas").textContent = `${e.PEDIDOS} venta(s)`;
        document.getElementById("tablaVentas").innerHTML = e.VENTAS.length ? e.VENTAS.map(v => `
            <tr>
                <td>#${v.ID_PEDIDO}</td>
                <td class="nowrap">${escaparHTML(v.FECHA_ENTREGA || "")}</td>
                <td>${escaparHTML(v.SUCURSAL || "")}</td>
                <td>${badgeEstado(v.ESTADO)}</td>
                <td class="num">${moneda(v.TOTAL)}</td>
                <td class="num">${Number(v.DEVOLUCIONES) ? "− " + moneda(v.DEVOLUCIONES) : "—"}</td>
                <td class="num"><strong>${moneda(v.NETO)}</strong></td>
            </tr>`).join("") : `<tr><td colspan="7" class="vacio">Sin ventas en este período.</td></tr>`;

        const dif = Number(e.DIFERENCIA);
        document.getElementById("ventasCompras").innerHTML = `
            <div class="resumen-cuenta">
                <div><span>Ventas netas</span><strong>${moneda(e.TOTAL_VENDIDO)}</strong></div>
                <div><span>Compras de insumos (${e.COMPRAS})</span><strong>− ${moneda(e.COMPRAS_TOTAL)}</strong></div>
                <div class="total ${dif < 0 ? "negativo" : ""}"><span>Diferencia</span><strong>${moneda(dif)}</strong></div>
            </div>
            <p class="texto-suave" style="font-size:.8rem">Solo cuenta las compras que se registraron con precio.</p>`;
    } catch (err) {
        avisar(err.message, "error");
    }
}

rangoEst.addEventListener("change", () => {
    const otro = rangoEst.value === "otro";
    desdeEst.classList.toggle("oculto", !otro);
    hastaEst.classList.toggle("oculto", !otro);
    if (otro && !desdeEst.value) {
        desdeEst.value = fechaISO(-29);
        hastaEst.value = fechaISO(0);
    }
    cargarEstadisticas();
});
desdeEst.addEventListener("change", cargarEstadisticas);
hastaEst.addEventListener("change", cargarEstadisticas);

/* ---------------- Sucursales ---------------- */
function usuarioDe(nombre) {
    return String(nombre || "").normalize("NFD").replace(/[̀-ͯ]/g, "").trim().split(/\s+/)[0].toLowerCase();
}

async function cargarSucursalesAdmin() {
    const tbody = document.getElementById("tablaSucursales");
    try {
        const lista = await Api.sucursales();
        tbody.innerHTML = lista.map(s => `
            <tr>
                <td>${s.ID_SUCURSAL}</td>
                <td><strong>${escaparHTML(s.NOMBRE)}</strong></td>
                <td><code>${escaparHTML(usuarioDe(s.NOMBRE))}</code></td>
                <td>${escaparHTML(s.DIRECCION || "")}</td>
                <td>${escaparHTML(s.TELEFONO || "")}</td>
                <td>${s.ESTADO === "ACTIVO" ? '<span class="badge badge-ok">Activa</span>' : '<span class="badge badge-CANCELADO">Inactiva</span>'}</td>
                <td><button class="btn btn-claro btn-chico" type="button" onclick="editarSucursal(${s.ID_SUCURSAL})">✏️ Editar</button></td>
            </tr>`).join("");
        window.listaSucursales = lista;
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="7" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

function editarSucursal(id) {
    const s = (window.listaSucursales || []).find(x => x.ID_SUCURSAL === id);
    if (!s) return;
    abrirModal(`Sucursal ${s.NOMBRE}`, `
        <form id="formEditarSuc" novalidate>
            <div class="campo"><label for="edDir">Dirección</label><input type="text" id="edDir" maxlength="200" value="${escaparHTML(s.DIRECCION || "")}"></div>
            <div class="campo"><label for="edTel">Teléfono</label><input type="tel" id="edTel" maxlength="20" value="${escaparHTML(s.TELEFONO || "")}"></div>
            <div class="campo"><label for="edEstado">Estado</label>
                <select id="edEstado"><option value="ACTIVO">Activa</option><option value="INACTIVO">Inactiva (su usuario ya no puede entrar)</option></select></div>
            <button class="btn btn-primario" type="submit" id="btnEdSuc">Guardar cambios</button>
        </form>`);
    document.getElementById("edEstado").value = s.ESTADO;
    document.getElementById("formEditarSuc").addEventListener("submit", async ev => {
        ev.preventDefault();
        try {
            const r = await conBoton(document.getElementById("btnEdSuc"), () => Api.actualizarSucursal(id, {
                DIRECCION: document.getElementById("edDir").value.trim(),
                TELEFONO: document.getElementById("edTel").value.trim(),
                ESTADO: document.getElementById("edEstado").value
            }));
            avisar(r.msj);
            cerrarModal();
            cargarSucursalesAdmin();
        } catch (err) {
            avisar(err.message, "error");
        }
    });
}

const sucNombre = document.getElementById("sucNombre");
const sucClave = document.getElementById("sucClave");
sucNombre.addEventListener("input", () => {
    const u = usuarioDe(sucNombre.value);
    document.getElementById("sucUsuario").textContent = u ? `Usuario para entrar: ${u}` : "";
});

document.getElementById("formSucursal").addEventListener("submit", async ev => {
    ev.preventDefault();
    let ok = validarRequerido(sucNombre, "Escriba el nombre de la sucursal.");
    if (ok && sucNombre.value.trim().length < 3) ok = marcarError(sucNombre, "Mínimo 3 letras.");
    limpiarError(sucClave);
    if (sucClave.value && sucClave.value.length < 6) ok = marcarError(sucClave, "Mínimo 6 caracteres.") && ok;
    if (!ok) return;
    try {
        const r = await conBoton(document.getElementById("btnSucursal"), () => Api.crearSucursal({
            NOMBRE: sucNombre.value.trim(),
            DIRECCION: document.getElementById("sucDireccion").value.trim(),
            TELEFONO: document.getElementById("sucTelefono").value.trim(),
            CLAVE: sucClave.value
        }));
        avisar(`Sucursal creada. Usuario: ${r.USUARIO} · contraseña: ${sucClave.value ? "la que escribió" : "Panaderia2026"}`);
        ev.target.reset();
        document.getElementById("sucUsuario").textContent = "";
        cargarSucursalesAdmin();
    } catch (err) {
        avisar(err.message, "error");
    }
});

/* ---------------- Carga según la pestaña ---------------- */
function cargarPestanaNueva() {
    const tab = location.hash.slice(1);
    if (tab === "estadisticas") cargarEstadisticas();
    if (tab === "sucursales") cargarSucursalesAdmin();
    if (tab === "alertas") cargarAvisos();
}
window.addEventListener("hashchange", cargarPestanaNueva);
cargarPestanaNueva();
cargarAvisos();
cargarEstadoCorreo();
setInterval(cargarAvisos, 60000);
