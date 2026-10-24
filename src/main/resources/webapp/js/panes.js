/* =====================================================================
   Panes y recetas (solo gerencia) — pestaña "Panes y recetas" del panel
   - Crear un pan nuevo junto con su receta   POST Productos/CrearPan
   - Cambiar la receta de un pan              POST Productos/{id}/GuardarReceta
   - Cambiar precio / mínimo / activar        POST Productos/{id}/ActualizarPan
   Se carga DESPUÉS de panel.js (usa refrescarTodo y recetaCache de ahí).
   ===================================================================== */
let catalogoPanes = [];
let catalogoInsumos = [];
let recetasCatalogo = {};

/* ---------------- Editor de receta (se usa en "Nuevo pan" y en el modal) ---------------- */
function opcionesInsumo(seleccionado) {
    return `<option value="">— Insumo —</option>` + catalogoInsumos
        .filter(i => i.ESTADO === "ACTIVO")
        .map(i => `<option value="${i.ID_INSUMO}" data-unidad="${escaparHTML(i.UNIDAD_MEDIDA)}"
                    ${String(i.ID_INSUMO) === String(seleccionado) ? "selected" : ""}>${escaparHTML(i.NOMBRE)}</option>`)
        .join("");
}

function unidadDe(idInsumo) {
    const i = catalogoInsumos.find(x => String(x.ID_INSUMO) === String(idInsumo));
    return i ? i.UNIDAD_MEDIDA : "";
}

/** Agrega una línea (insumo + cantidad + quitar) al contenedor. */
function agregarLinea(contenedor, idInsumo = "", cantidad = "") {
    const fila = document.createElement("div");
    fila.className = "linea-receta";
    fila.innerHTML = `
        <select class="lr-insumo" aria-label="Insumo">${opcionesInsumo(idInsumo)}</select>
        <input class="lr-cantidad" type="number" min="0.001" step="0.001" placeholder="0.000"
               value="${cantidad}" aria-label="Cantidad por pan">
        <span class="lr-unidad">${escaparHTML(unidadDe(idInsumo))}</span>
        <button type="button" class="lr-quitar" title="Quitar insumo" aria-label="Quitar insumo">✕</button>`;
    fila.querySelector(".lr-insumo").addEventListener("change", e => {
        fila.querySelector(".lr-unidad").textContent = unidadDe(e.target.value);
        e.target.classList.remove("invalido");
    });
    fila.querySelector(".lr-cantidad").addEventListener("input", e => e.target.classList.remove("invalido"));
    fila.querySelector(".lr-quitar").addEventListener("click", () => {
        fila.remove();
        if (!contenedor.children.length) agregarLinea(contenedor);
    });
    contenedor.append(fila);
}

/**
 * Lee y valida las líneas. Devuelve la receta [{ID_INSUMO, CANTIDAD}]
 * o null si hay errores (y los marca en rojo).
 */
function leerReceta(contenedor, cajaError) {
    cajaError.textContent = "";
    const receta = [];
    const vistos = new Set();
    let valido = true;
    contenedor.querySelectorAll(".linea-receta").forEach(fila => {
        const sel = fila.querySelector(".lr-insumo");
        const cant = fila.querySelector(".lr-cantidad");
        sel.classList.remove("invalido"); cant.classList.remove("invalido");
        if (!sel.value && !cant.value) return;                      // línea vacía: se ignora
        if (!sel.value) { sel.classList.add("invalido"); valido = false; return; }
        const n = Number(cant.value);
        if (!Number.isFinite(n) || n <= 0 || n > 100) { cant.classList.add("invalido"); valido = false; return; }
        if (vistos.has(sel.value)) {
            sel.classList.add("invalido"); valido = false;
            cajaError.textContent = "Hay un insumo repetido en la receta.";
            return;
        }
        vistos.add(sel.value);
        receta.push({ ID_INSUMO: Number(sel.value), CANTIDAD: Math.round(n * 1000) / 1000 });
    });
    if (!valido) {
        cajaError.textContent = cajaError.textContent || "Revise los insumos marcados (cantidad mayor que 0, máximo 100).";
        return null;
    }
    if (!receta.length) {
        cajaError.textContent = "Agregue al menos un insumo a la receta.";
        return null;
    }
    return receta;
}

/* ---------------- Catálogo ---------------- */
async function cargarCatalogo() {
    const tbody = document.getElementById("tablaCatalogo");
    try {
        [catalogoPanes, catalogoInsumos] = await Promise.all([Api.productos(), Api.insumos()]);
        const recetas = await Promise.all(catalogoPanes.map(p => Api.receta(p.ID_PRODUCTO).catch(() => [])));
        recetasCatalogo = {};
        catalogoPanes.forEach((p, i) => { recetasCatalogo[p.ID_PRODUCTO] = recetas[i]; });

        tbody.innerHTML = catalogoPanes.map(p => {
            const n = recetasCatalogo[p.ID_PRODUCTO].length;
            return `<tr class="${p.ESTADO === "ACTIVO" ? "" : "fila-inactiva"}">
                <td><strong>${escaparHTML(p.NOMBRE)}</strong><br><span class="texto-suave" style="font-size:.8rem">por ${escaparHTML(p.UNIDAD_MEDIDA)}</span></td>
                <td class="num">${moneda(p.PRECIO)}</td>
                <td class="num">${numero(p.STOCK_MINIMO, 0)}</td>
                <td class="num">${n ? n : '<span class="badge badge-alerta">Sin receta</span>'}</td>
                <td>${p.ESTADO === "ACTIVO" ? '<span class="badge badge-ok">Activo</span>' : '<span class="badge badge-CANCELADO">Inactivo</span>'}</td>
                <td class="num" style="white-space:nowrap">
                    <button class="btn btn-claro btn-chico" type="button" onclick="editarReceta(${p.ID_PRODUCTO})">📋 Receta</button>
                    <button class="btn btn-claro btn-chico" type="button" onclick="editarPan(${p.ID_PRODUCTO})">✏️ Editar</button>
                </td>
            </tr>`;
        }).join("") || `<tr><td colspan="6" class="vacio">No hay panes.</td></tr>`;

        // Si el formulario aún no tiene líneas, se prepara con una vacía
        const editor = document.getElementById("npReceta");
        if (!editor.children.length) agregarLinea(editor);
        else editor.querySelectorAll(".lr-insumo").forEach(s => { const v = s.value; s.innerHTML = opcionesInsumo(v); });
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="vacio">${escaparHTML(e.message)}</td></tr>`;
    }
}

/** Después de cualquier cambio: recarga el catálogo y el resto del panel. */
async function trasCambio() {
    if (typeof recetaCache !== "undefined") recetaCache = {};
    await cargarCatalogo();
    if (typeof refrescarTodo === "function") refrescarTodo();
}

/* ---------------- Nuevo pan ---------------- */
const npNombre = document.getElementById("npNombre");
const npPrecio = document.getElementById("npPrecio");
const npMinimo = document.getElementById("npMinimo");
const npReceta = document.getElementById("npReceta");

document.getElementById("npAgregar").addEventListener("click", () => agregarLinea(npReceta));
[npNombre, npPrecio, npMinimo].forEach(i => i.addEventListener("input", () => limpiarError(i)));

document.getElementById("formNuevoPan").addEventListener("submit", async ev => {
    ev.preventDefault();
    let valido = validarRequerido(npNombre, "Escriba el nombre del pan.");
    const nombre = npNombre.value.trim().replace(/\s+/g, " ");
    if (valido && !/^[\p{L} .'-]{3,60}$/u.test(nombre)) {
        valido = marcarError(npNombre, "Solo letras y espacios (3 a 60).");
    }
    if (valido && catalogoPanes.some(p => p.NOMBRE.toLowerCase() === nombre.toLowerCase())) {
        valido = marcarError(npNombre, "Ya existe un pan con ese nombre.");
    }
    if (!validarCantidad(npPrecio, { maximo: 1000 })) valido = false;
    else if (!/^\d+(\.\d{1,2})?$/.test(npPrecio.value.trim())) valido = marcarError(npPrecio, "Máximo 2 decimales.");
    limpiarError(npMinimo);
    const minimo = Number(npMinimo.value);
    if (npMinimo.value.trim() === "" || !Number.isInteger(minimo) || minimo < 0) {
        valido = marcarError(npMinimo, "Número entero, 0 o más.");
    }
    const receta = leerReceta(npReceta, document.getElementById("npErrorReceta"));
    if (!receta) valido = false;
    if (!valido) { avisar("Revise los campos marcados en rojo.", "error"); return; }

    try {
        const r = await conBoton(document.getElementById("btnCrearPan"), () => Api.crearPan({
            NOMBRE: nombre,
            UNIDAD_MEDIDA: document.getElementById("npUnidad").value,
            PRECIO: Number(npPrecio.value),
            STOCK_MINIMO: minimo,
            RECETA: receta
        }));
        avisar(r.msj);
        ev.target.reset();
        npReceta.innerHTML = "";
        agregarLinea(npReceta);
        await trasCambio();
    } catch (e) {
        avisar(e.message, "error");
    }
});

/* ---------------- Editar receta (modal) ---------------- */
function editarReceta(id) {
    const p = catalogoPanes.find(x => x.ID_PRODUCTO === id);
    if (!p) return;
    abrirModal(`Receta de ${p.NOMBRE}`, `
        <p class="texto-suave" style="font-size:.88rem">Cantidad de cada insumo para <strong>1 ${escaparHTML(p.UNIDAD_MEDIDA)}</strong>.
            Al guardar se reemplaza la receta completa.</p>
        <div id="mrReceta" class="editor-receta"></div>
        <button type="button" class="btn btn-claro btn-chico" id="mrAgregar">+ Agregar insumo</button>
        <p class="msg-error" id="mrError"></p>
        <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:14px">
            <button type="button" class="btn btn-claro" onclick="cerrarModal()">Cancelar</button>
            <button type="button" class="btn btn-rojo" id="mrGuardar">Guardar receta</button>
        </div>`);
    const caja = document.getElementById("mrReceta");
    const actual = recetasCatalogo[id] || [];
    if (actual.length) actual.forEach(r => agregarLinea(caja, r.ID_INSUMO, Number(r.CANTIDAD)));
    else agregarLinea(caja);
    document.getElementById("mrAgregar").addEventListener("click", () => agregarLinea(caja));
    document.getElementById("mrGuardar").addEventListener("click", async e => {
        const receta = leerReceta(caja, document.getElementById("mrError"));
        if (!receta) return;
        try {
            const r = await conBoton(e.target, () => Api.guardarReceta(id, receta));
            avisar(r.msj);
            cerrarModal();
            await trasCambio();
        } catch (err) {
            document.getElementById("mrError").textContent = err.message;
        }
    });
}

/* ---------------- Editar precio / mínimo / estado (modal) ---------------- */
function editarPan(id) {
    const p = catalogoPanes.find(x => x.ID_PRODUCTO === id);
    if (!p) return;
    abrirModal(`Editar ${p.NOMBRE}`, `
        <form id="formEditarPan" novalidate>
            <div class="fila-form">
                <div class="campo">
                    <label for="epPrecio">Precio (Q) *</label>
                    <input type="number" id="epPrecio" min="0.01" step="0.01" value="${Number(p.PRECIO).toFixed(2)}">
                    <span class="msg-error"></span>
                </div>
                <div class="campo">
                    <label for="epMinimo">Existencia mínima *</label>
                    <input type="number" id="epMinimo" min="0" step="1" value="${Number(p.STOCK_MINIMO)}">
                    <span class="msg-error"></span>
                </div>
            </div>
            <div class="campo">
                <label for="epEstado">Estado</label>
                <select id="epEstado">
                    <option value="ACTIVO" ${p.ESTADO === "ACTIVO" ? "selected" : ""}>Activo (se produce y se pide)</option>
                    <option value="INACTIVO" ${p.ESTADO !== "ACTIVO" ? "selected" : ""}>Inactivo (ya no se hace)</option>
                </select>
            </div>
            <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:14px">
                <button type="button" class="btn btn-claro" onclick="cerrarModal()">Cancelar</button>
                <button type="submit" class="btn btn-rojo" id="epGuardar">Guardar cambios</button>
            </div>
        </form>`);
    document.getElementById("formEditarPan").addEventListener("submit", async ev => {
        ev.preventDefault();
        const precio = document.getElementById("epPrecio");
        const minimo = document.getElementById("epMinimo");
        let valido = validarCantidad(precio, { maximo: 1000 });
        if (valido && !/^\d+(\.\d{1,2})?$/.test(precio.value.trim())) valido = marcarError(precio, "Máximo 2 decimales.");
        limpiarError(minimo);
        if (minimo.value.trim() === "" || !Number.isInteger(Number(minimo.value)) || Number(minimo.value) < 0) {
            valido = marcarError(minimo, "Número entero, 0 o más.");
        }
        if (!valido) return;
        try {
            const r = await conBoton(document.getElementById("epGuardar"), () => Api.actualizarPan(id, {
                PRECIO: Number(precio.value),
                STOCK_MINIMO: Number(minimo.value),
                ESTADO: document.getElementById("epEstado").value
            }));
            avisar(r.msj);
            cerrarModal();
            await trasCambio();
        } catch (e) {
            avisar(e.message, "error");
        }
    });
}

cargarCatalogo();
