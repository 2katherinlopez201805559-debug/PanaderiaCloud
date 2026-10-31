/* =====================================================================
   App del vendedor en ruta (unidad de reparto, rol VENDEDOR)
   - Ve los pedidos que gerencia le asignó (despachados).
   - Marca ENTREGADO o REPORTA UN PROBLEMA en ruta.
   - El problema llega al gerente (campanita + Telegram), que reasigna
     el pedido a otra unidad.
   ===================================================================== */
montarSistema(["VENDEDOR"]);
seccionesPorHash("entregas");

const TIPOS_PROBLEMA = {
    VEHICULO: { icono: "🛠️", texto: "Falla del vehículo" },
    ACCIDENTE: { icono: "🚨", texto: "Accidente" },
    PRODUCTO: { icono: "🍞", texto: "Problema con el pan" },
    SUCURSAL_CERRADA: { icono: "🔒", texto: "Sucursal cerrada" },
    OTRO: { icono: "❓", texto: "Otro" }
};

let pedidosRuta = [];

/* ---------------- Mis entregas ---------------- */
async function cargarRuta() {
    const caja = document.getElementById("listaRuta");
    try {
        pedidosRuta = await Api.misPedidosRuta();
        document.getElementById("horaRuta").textContent = "Actualizado " + new Date().toLocaleTimeString("es-GT", { hour: "2-digit", minute: "2-digit" });
        if (!pedidosRuta.length) {
            caja.innerHTML = `<div class="tarjeta vacio">✅ No tiene pedidos pendientes de entregar.</div>`;
            return;
        }
        caja.innerHTML = pedidosRuta.map(p => {
            const lineas = (p.DETALLE || []).map(d => `<li><strong>${numero(d.CANTIDAD, 0)}</strong> ${escaparHTML(d.PRODUCTO)}</li>`).join("");
            const tel = String(p.TELEFONO || "").replace(/[^0-9+]/g, "");
            const conProblema = p.ID_INCIDENTE != null;
            return `
            <article class="tarjeta tarjeta-ruta ${conProblema ? "con-problema" : ""}">
                <div class="tarjeta-cabecera">
                    <h2>Pedido #${p.ID_PEDIDO} · ${escaparHTML(p.SUCURSAL)}</h2>
                    ${conProblema ? '<span class="badge badge-alerta">Problema reportado</span>' : '<span class="badge badge-DESPACHADO">En ruta</span>'}
                </div>
                <p class="dato-ruta">📍 ${escaparHTML(p.DIRECCION || "Dirección por definir")}</p>
                <p class="dato-ruta">📞 ${tel.length >= 7 ? `<a href="tel:${escaparHTML(tel)}">${escaparHTML(p.TELEFONO)}</a>` : escaparHTML(p.TELEFONO || "Sin teléfono")}</p>
                <p class="dato-ruta">📅 Entrega: ${escaparHTML(p.FECHA_ENTREGA || "")} · Total ${moneda(p.TOTAL)}</p>
                <ul class="lista-panes">${lineas}</ul>
                ${conProblema ? '<p class="texto-suave" style="font-size:.85rem">Gerencia está revisando el problema; si reasigna el pedido, desaparecerá de su lista.</p>' : ""}
                <div class="botones-ruta">
                    <button class="btn btn-verde" type="button" onclick="entregar(${p.ID_PEDIDO}, this)">✅ Entregado</button>
                    ${conProblema ? "" : `<button class="btn btn-rojo" type="button" onclick="abrirReporte(${p.ID_PEDIDO})">⚠️ Reportar problema</button>`}
                </div>
            </article>`;
        }).join("");
    } catch (e) {
        caja.innerHTML = `<div class="tarjeta vacio">${escaparHTML(e.message)}</div>`;
    }
}

async function entregar(id, boton) {
    if (!confirm(`¿Confirma que entregó el pedido #${id}?`)) return;
    try {
        const r = await conBoton(boton, () => Api.entregarRuta(id));
        avisar(r.msj);
        cargarRuta();
    } catch (e) {
        avisar(e.message, "error");
    }
}

/* ---------------- Reportar problema ---------------- */
function abrirReporte(id) {
    const p = pedidosRuta.find(x => x.ID_PEDIDO === id);
    abrirModal(`Reportar problema · Pedido #${id}`, `
        <form id="formReporte" novalidate>
            <p class="texto-suave" style="font-size:.9rem">${escaparHTML(p ? p.SUCURSAL : "")} · Gerencia recibe el aviso al instante y puede mandar otra unidad.</p>
            <div class="tipos-problema">
                ${Object.entries(TIPOS_PROBLEMA).map(([k, v]) => `
                    <label class="metodo-pago"><input type="radio" name="tipoProblema" value="${k}">
                        <span class="ico">${v.icono}</span><strong>${v.texto}</strong></label>`).join("")}
            </div>
            <p class="msg-error" id="errorTipo"></p>
            <div class="campo">
                <label for="descProblema">¿Qué pasó? *</label>
                <textarea id="descProblema" rows="3" maxlength="300" placeholder="Ej. Se pinchó una llanta en la 4a. calle, no puedo continuar."></textarea>
                <span class="msg-error"></span>
            </div>
            <button class="btn btn-rojo" type="submit" id="btnReporte" style="width:100%;justify-content:center">Enviar reporte a gerencia</button>
        </form>`);
    document.getElementById("formReporte").addEventListener("submit", async ev => {
        ev.preventDefault();
        const tipo = document.querySelector('input[name="tipoProblema"]:checked');
        const desc = document.getElementById("descProblema");
        document.getElementById("errorTipo").textContent = tipo ? "" : "Elija qué tipo de problema tiene.";
        let ok = !!tipo;
        limpiarError(desc);
        if (desc.value.trim().length < 5) {
            marcarError(desc, "Describa el problema (mínimo 5 letras).");
            ok = false;
        }
        if (!ok) return;
        try {
            const r = await conBoton(document.getElementById("btnReporte"), () => Api.reportarProblema(id, tipo.value, desc.value.trim()));
            avisar(r.msj);
            cerrarModal();
            cargarRuta();
            cargarReportes();
        } catch (e) {
            avisar(e.message, "error");
        }
    });
}

/* ---------------- Mis reportes ---------------- */
async function cargarReportes() {
    const caja = document.getElementById("listaReportes");
    try {
        const lista = await Api.misReportesRuta();
        caja.innerHTML = lista.length ? lista.map(i => {
            const t = TIPOS_PROBLEMA[i.TIPO] || TIPOS_PROBLEMA.OTRO;
            const resuelto = i.ESTADO === "RESUELTO";
            return `
            <article class="tarjeta tarjeta-ruta">
                <div class="tarjeta-cabecera">
                    <h2>${t.icono} ${escaparHTML(t.texto)} · Pedido #${i.ID_PEDIDO}</h2>
                    ${resuelto ? '<span class="badge badge-ok">Resuelto</span>' : '<span class="badge badge-alerta">Abierto</span>'}
                </div>
                <p class="dato-ruta">${escaparHTML(i.SUCURSAL || "")} · ${escaparHTML(i.FECHA || "")}</p>
                <p>${escaparHTML(i.DESCRIPCION)}</p>
                ${resuelto ? `<p class="texto-suave" style="font-size:.88rem">✔ ${i.UNIDAD_NUEVA ? `Gerencia mandó a <strong>${escaparHTML(i.UNIDAD_NUEVA)}</strong>.` : "Resuelto."} ${escaparHTML(i.NOTA || "")}</p>` : ""}
            </article>`;
        }).join("") : `<div class="tarjeta vacio">No ha reportado problemas.</div>`;
    } catch (e) {
        caja.innerHTML = `<div class="tarjeta vacio">${escaparHTML(e.message)}</div>`;
    }
}

document.getElementById("btnRecargar").addEventListener("click", () => { cargarRuta(); cargarReportes(); });
cargarRuta();
cargarReportes();
setInterval(cargarRuta, 60000);
