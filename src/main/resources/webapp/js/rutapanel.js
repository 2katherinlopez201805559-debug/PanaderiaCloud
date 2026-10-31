/* =====================================================================
   Panel del gerente — pestaña 🚚 Ruta (vendedores en ruta)
   - Problemas reportados por las unidades → reasignar a otra unidad.
   - Pedidos despachados → asignar unidad de reparto.
   (Se carga después de panel.js)
   ===================================================================== */
const TIPOS_RUTA = {
    VEHICULO: "🛠️ Falla del vehículo", ACCIDENTE: "🚨 Accidente", PRODUCTO: "🍞 Problema con el pan",
    SUCURSAL_CERRADA: "🔒 Sucursal cerrada", OTRO: "❓ Otro"
};
let unidadesRuta = [];

function opcionesUnidad(excluir, elegida) {
    return `<option value="">— Elija unidad —</option>` + unidadesRuta
        .filter(u => u.ESTADO === "ACTIVO" && u.ID_UNIDAD !== excluir)
        .map(u => `<option value="${u.ID_UNIDAD}" ${u.ID_UNIDAD === elegida ? "selected" : ""}>${escaparHTML(u.NOMBRE)} (${u.PEDIDOS_EN_RUTA} en ruta)</option>`)
        .join("");
}

async function cargarRutaPanel() {
    try {
        const [unidades, enRuta, abiertos, todos] = await Promise.all([
            Api.unidades(), Api.pedidosEnRuta(), Api.incidentes("ABIERTO"), Api.incidentes()]);
        unidadesRuta = unidades;

        // Contador en la pestaña
        const cont = document.getElementById("contadorRuta");
        cont.textContent = abiertos.length;
        cont.classList.toggle("oculto", !abiertos.length);

        // Problemas abiertos
        document.getElementById("listaProblemas").innerHTML = abiertos.length ? abiertos.map(i => `
            <article class="tarjeta tarjeta-ruta con-problema">
                <div class="tarjeta-cabecera">
                    <h2>Pedido #${i.ID_PEDIDO} · ${escaparHTML(i.SUCURSAL || "")}</h2>
                    <span class="badge badge-alerta">${escaparHTML(TIPOS_RUTA[i.TIPO] || i.TIPO)}</span>
                </div>
                <p class="dato-ruta">🚚 ${escaparHTML(i.UNIDAD || "")} · ${escaparHTML(i.FECHA || "")}</p>
                <p>“${escaparHTML(i.DESCRIPCION)}”</p>
                <div class="fila-form">
                    <div class="campo"><label for="reasignar-${i.ID_INCIDENTE}">Enviar otra unidad</label>
                        <select id="reasignar-${i.ID_INCIDENTE}">${opcionesUnidad(i.ID_UNIDAD)}</select></div>
                    <div class="campo"><label for="nota-${i.ID_INCIDENTE}">Nota (opcional)</label>
                        <input id="nota-${i.ID_INCIDENTE}" maxlength="200" placeholder="Ej. La unidad 2 sale a las 9:30"></div>
                </div>
                <div class="botones-ruta">
                    <button class="btn btn-primario" type="button" onclick="resolverProblema(${i.ID_INCIDENTE}, true, this)">🔁 Reasignar pedido</button>
                    <button class="btn btn-claro" type="button" onclick="resolverProblema(${i.ID_INCIDENTE}, false, this)">Resuelto, sigue la misma unidad</button>
                </div>
            </article>`).join("")
            : `<p class="vacio">✅ Ninguna unidad ha reportado problemas.</p>`;

        // Pedidos en ruta
        document.getElementById("tablaEnRuta").innerHTML = enRuta.length ? enRuta.map(p => `
            <tr>
                <td>#${p.ID_PEDIDO}</td>
                <td><strong>${escaparHTML(p.SUCURSAL || "")}</strong></td>
                <td class="nowrap">${escaparHTML(p.FECHA_ENTREGA || "")}</td>
                <td class="num">${moneda(p.TOTAL)}</td>
                <td><select id="unidad-${p.ID_PEDIDO}" aria-label="Unidad del pedido ${p.ID_PEDIDO}">${opcionesUnidad(null, p.ID_UNIDAD)}</select></td>
                <td>${p.ID_INCIDENTE != null ? '<span class="badge badge-alerta">Problema</span>'
                    : p.ID_UNIDAD != null ? '<span class="badge badge-DESPACHADO">En ruta</span>'
                    : '<span class="badge badge-PENDIENTE">Sin unidad</span>'}</td>
                <td><button class="btn btn-claro btn-chico" type="button" onclick="asignarUnidad(${p.ID_PEDIDO}, this)">Asignar</button></td>
            </tr>`).join("")
            : `<tr><td colspan="7" class="vacio">No hay pedidos despachados. Despache pedidos en la pestaña Pedidos.</td></tr>`;

        // Unidades
        document.getElementById("tablaUnidades").innerHTML = unidades.map(u => `
            <tr>
                <td><strong>${escaparHTML(u.NOMBRE)}</strong></td>
                <td><code>${escaparHTML(u.USUARIO)}</code></td>
                <td class="num">${u.PEDIDOS_EN_RUTA}</td>
                <td class="num">${u.PROBLEMAS ? `<span class="badge badge-alerta">${u.PROBLEMAS}</span>` : "0"}</td>
            </tr>`).join("");

        // Historial
        const resueltos = todos.filter(i => i.ESTADO === "RESUELTO").slice(0, 8);
        document.getElementById("historialProblemas").innerHTML = resueltos.length ? resueltos.map(i => `
            <p class="historial-ruta"><strong>#${i.ID_PEDIDO} · ${escaparHTML(TIPOS_RUTA[i.TIPO] || i.TIPO)}</strong><br>
                <span class="texto-suave">${escaparHTML(i.UNIDAD || "")}${i.UNIDAD_NUEVA ? ` → ${escaparHTML(i.UNIDAD_NUEVA)}` : ""} · ${escaparHTML(i.FECHA_RESOLUCION || "")}</span></p>`).join("")
            : `<p class="vacio">Sin reportes resueltos.</p>`;
    } catch (e) {
        document.getElementById("listaProblemas").innerHTML = `<p class="vacio">${escaparHTML(e.message)}</p>`;
    }
}

async function asignarUnidad(idPedido, boton) {
    const sel = document.getElementById(`unidad-${idPedido}`);
    if (!sel.value) { avisar("Elija la unidad de reparto.", "error"); return; }
    try {
        const r = await conBoton(boton, () => Api.asignarUnidad(idPedido, Number(sel.value)));
        avisar(r.msj);
        cargarRutaPanel();
    } catch (e) {
        avisar(e.message, "error");
    }
}

async function resolverProblema(idIncidente, reasignar, boton) {
    const sel = document.getElementById(`reasignar-${idIncidente}`);
    const nota = document.getElementById(`nota-${idIncidente}`).value.trim();
    if (reasignar && !sel.value) { avisar("Elija la unidad que llevará el pedido.", "error"); return; }
    try {
        const r = await conBoton(boton, () => Api.resolverIncidente(idIncidente, reasignar ? Number(sel.value) : null, nota));
        avisar(r.msj);
        cargarRutaPanel();
        if (typeof revisarCampanita === "function") revisarCampanita();
    } catch (e) {
        avisar(e.message, "error");
    }
}

window.addEventListener("hashchange", () => { if (location.hash === "#ruta") cargarRutaPanel(); });
cargarRutaPanel();
setInterval(cargarRutaPanel, 60000);
