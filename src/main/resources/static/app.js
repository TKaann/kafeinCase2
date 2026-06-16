"use strict";

// ---- Output panel helpers ----
const logEl = document.getElementById("log");

function write(line = "") {
    logEl.textContent += line + "\n";
    logEl.scrollTop = logEl.scrollHeight;
}

function header(title) {
    write("\n=== " + title + " ===");
}

function verdict(ok, msg) {
    write((ok ? "✓ PASS" : "✗ FAIL") + " — " + msg);
}

function clearLog() {
    logEl.textContent = "";
}

// ---- API helper (same origin) ----
async function api(method, path, body) {
    const opts = { method, headers: {} };
    if (body !== undefined) {
        opts.headers["Content-Type"] = "application/json";
        opts.body = (typeof body === "string") ? body : JSON.stringify(body);
    }
    const res = await fetch(path, opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data };
}

function placeOrder(paymentMethod, items, customerId = 1) {
    return api("POST", "/api/orders", { customerId, paymentMethod, items });
}

async function setStock(id, qty) {
    return api("PATCH", `/api/products/${id}/stock`, { stockQuantity: qty });
}

async function stockOf(id) {
    const r = await api("GET", `/api/products/${id}`);
    return r.data.stockQuantity;
}

// Option markup for the manual-order product dropdowns; rebuilt on every product refresh.
let productOptionsHtml = "";

async function refreshProducts() {
    const r = await api("GET", "/api/products");
    const list = r.data || [];

    const tbody = document.getElementById("products");
    tbody.innerHTML = "";
    list.forEach(p => {
        const tr = document.createElement("tr");
        tr.innerHTML = `<td>${p.id}</td><td>${p.name}</td><td>${p.price}</td><td>${p.stockQuantity}</td>`;
        tbody.appendChild(tr);
    });

    productOptionsHtml = list
        .map(p => `<option value="${p.id}">${p.id} · ${p.name} (${p.price})</option>`)
        .join("");
}

// ---- Manual order: dynamic line items ----
function addOrderLine() {
    const line = document.createElement("div");
    line.className = "order-line";
    line.innerHTML =
        `<select class="line-product">${productOptionsHtml}</select>` +
        `<input class="line-qty" type="number" min="1" value="1">` +
        `<button type="button" class="line-remove ghost" title="Satırı sil">×</button>`;
    line.querySelector(".line-remove").addEventListener("click", () => {
        const container = document.getElementById("order-lines");
        if (container.children.length > 1) {
            container.removeChild(line);   // keep at least one line
        }
    });
    document.getElementById("order-lines").appendChild(line);
}

function collectOrderItems() {
    const items = [];
    document.querySelectorAll("#order-lines .order-line").forEach(line => {
        const productId = Number(line.querySelector(".line-product").value);
        const quantity = Number(line.querySelector(".line-qty").value);
        if (productId && quantity >= 1) {   // ignore empty/invalid rows
            items.push({ productId, quantity });
        }
    });
    return items;
}

// ---- Scenarios (live equivalents of the JUnit tests) ----
const scenarios = {
    async 1() {
        await setStock(2, 50);
        const r = await placeOrder("CREDIT_CARD", [{ productId: 2, quantity: 2 }]);
        write(`POST /api/orders -> HTTP ${r.status}`);
        write(JSON.stringify(r.data));
        verdict(r.status === 201 && r.data.status === "CONFIRMED" && r.data.paymentStatus === "COMPLETED",
            `beklenen 201 + CONFIRMED + COMPLETED; gelen ${r.status} + ${r.data.status} + ${r.data.paymentStatus}`);
    },

    async 2() {
        await setStock(1, 20);
        await setStock(2, 50);
        const before1 = await stockOf(1), before2 = await stockOf(2);
        const r = await placeOrder("CREDIT_CARD", [{ productId: 1, quantity: 1 }, { productId: 2, quantity: 1 }]);
        write(`POST [ürün1 x1, ürün2 x1] -> HTTP ${r.status}, total=${r.data.totalAmount}`);
        const after1 = await stockOf(1), after2 = await stockOf(2);
        write(`stoklar: ürün1 ${before1}->${after1}, ürün2 ${before2}->${after2}`);
        const expectedTotal = 1499.99 + 29.90;
        verdict(r.status === 201 && Math.abs(r.data.totalAmount - expectedTotal) < 0.001
            && after1 === before1 - 1 && after2 === before2 - 1,
            `201 + total ${expectedTotal.toFixed(2)} + her iki stok da 1 düşmeli`);
    },

    async 3() {
        await setStock(8, 5);
        const before = await stockOf(8);
        const r = await placeOrder("CRYPTO", [{ productId: 8, quantity: 100 }]);
        write(`POST ürün8 x100 (stok ${before}) -> HTTP ${r.status}`);
        write(JSON.stringify(r.data));
        const after = await stockOf(8);
        write(`stok: ${before} -> ${after} (rollback ile değişmemeli)`);
        verdict(r.status === 409 && after === before,
            `409 + stok ${before} sabit kalmalı; gelen ${r.status}, stok ${after}`);
    },

    async 4() {
        await setStock(1, 10);
        await setStock(8, 1);
        const before1 = await stockOf(1), before8 = await stockOf(8);
        const r = await placeOrder("CREDIT_CARD", [{ productId: 1, quantity: 2 }, { productId: 8, quantity: 5 }]);
        write(`POST [ürün1 x2, ürün8 x5] (ürün8 stok 1) -> HTTP ${r.status}`);
        write(JSON.stringify(r.data));
        const after1 = await stockOf(1), after8 = await stockOf(8);
        write(`stoklar: ürün1 ${before1}->${after1} (ilk kalem, yine de düşmemeli), ürün8 ${before8}->${after8}`);
        verdict(r.status === 409 && after1 === before1 && after8 === before8,
            `409 + tüm transaction geri sarmalı (ürün1 stoğu bile sabit)`);
    },

    async 5() {
        await setStock(1, 20);
        const before = await stockOf(1);
        // 7 x 1499.99 = 10499.93 > 10000 (CC limiti)
        const r = await placeOrder("CREDIT_CARD", [{ productId: 1, quantity: 7 }]);
        write(`POST ürün1 x7 (~10499.93, CC limit 10000) -> HTTP ${r.status}`);
        write(JSON.stringify(r.data));
        const after = await stockOf(1);
        write(`stok: ${before} -> ${after} (ödeme reddi rollback'i ile değişmemeli)`);
        verdict(r.status === 422 && after === before,
            `422 + stok ${before} sabit; gelen ${r.status}, stok ${after}`);
    },

    async 6() {
        const N = 500, STOCK = 10;
        await setStock(5, STOCK);
        write(`${N} eşzamanlı CRYPTO sipariş, ürün5 stok ${STOCK}...`);
        const tasks = Array.from({ length: N }, () => placeOrder("CRYPTO", [{ productId: 5, quantity: 1 }]));
        const results = await Promise.allSettled(tasks);
        let ok = 0, insufficient = 0, other = 0;
        results.forEach(x => {
            const s = x.status === "fulfilled" ? x.value.status : 0;
            if (s === 201) ok++; else if (s === 409) insufficient++; else other++;
        });
        const after = await stockOf(5);
        write(`başarılı=${ok}, yetersiz stok=${insufficient}, diğer=${other}, son stok=${after}`);
        verdict(ok === STOCK && after === 0 && other === 0 && (ok + insufficient === N),
            `tam ${STOCK} başarılı, oversell yok, son stok 0`);
    },

    async 7() {
        const PER = 50, STOCK = 300;
        await setStock(1, STOCK);
        await setStock(2, STOCK);
        write(`${PER} thread [ürün1,ürün2] + ${PER} thread [ürün2,ürün1] eşzamanlı (ters sıra)...`);
        const groupA = Array.from({ length: PER }, () =>
            placeOrder("CRYPTO", [{ productId: 1, quantity: 1 }, { productId: 2, quantity: 1 }]));
        const groupB = Array.from({ length: PER }, () =>
            placeOrder("CRYPTO", [{ productId: 2, quantity: 1 }, { productId: 1, quantity: 1 }]));
        const results = await Promise.allSettled([...groupA, ...groupB]);
        const ok = results.filter(x => x.status === "fulfilled" && x.value.status === 201).length;
        const after1 = await stockOf(1), after2 = await stockOf(2);
        const total = PER * 2;
        write(`tamamlanan başarılı=${ok}/${total} (hepsi resolve oldu -> deadlock yok)`);
        write(`stoklar: ürün1 ${STOCK}->${after1}, ürün2 ${STOCK}->${after2}`);
        verdict(ok === total && after1 === STOCK - total && after2 === STOCK - total,
            `deadlock yok + tüm ${total} sipariş başarılı + stoklar tutarlı`);
    },

    async 8() {
        await setStock(8, 5);
        await setStock(2, 50);
        const bulk = {
            orders: [
                { customerId: 1, paymentMethod: "CRYPTO", items: [{ productId: 8, quantity: 4 }] },
                { customerId: 2, paymentMethod: "CRYPTO", items: [{ productId: 8, quantity: 100 }] },
                { customerId: 3, paymentMethod: "CRYPTO", items: [{ productId: 2, quantity: 2 }] }
            ]
        };
        const r = await api("POST", "/api/orders/bulk", bulk);
        write(`POST /api/orders/bulk -> HTTP ${r.status}`);
        write(JSON.stringify(r.data, null, 2));
        verdict(r.status === 200 && r.data.succeeded === 2 && r.data.failed === 1,
            `200 + succeeded 2 + failed 1 (biri başarısız, diğerleri etkilenmez)`);
    },

    async 9() {
        const r404 = await api("GET", "/api/products/999");
        write(`GET /api/products/999 -> HTTP ${r404.status}`);
        write(JSON.stringify(r404.data));
        const r400 = await api("POST", "/api/orders", "{");
        write(`POST /api/orders (bozuk JSON) -> HTTP ${r400.status}`);
        write(JSON.stringify(r400.data));
        verdict(r404.status === 404 && r400.status === 400,
            `404 (olmayan ürün) + 400 (bozuk istek)`);
    }
};

// ---- Wiring (one run at a time) ----
let busy = false;

function setBusy(state) {
    busy = state;
    document.querySelectorAll("button").forEach(b => { b.disabled = state; });
}

async function guarded(fn) {
    if (busy) return;
    setBusy(true);
    try {
        await fn();
    } catch (e) {
        write("HATA: " + e.message);
    } finally {
        await refreshProducts();
        setBusy(false);
    }
}

document.querySelectorAll("[data-sc]").forEach(btn => {
    btn.addEventListener("click", () => guarded(async () => {
        header(btn.textContent.trim());
        await scenarios[btn.dataset.sc]();
    }));
});

document.getElementById("refresh").addEventListener("click", () => guarded(refreshProducts));
document.getElementById("clear-log").addEventListener("click", clearLog);

document.getElementById("set-stock").addEventListener("click", () => guarded(async () => {
    const id = Number(document.getElementById("stock-id").value);
    const qty = Number(document.getElementById("stock-qty").value);
    header(`Stok ayarla: ürün ${id} -> ${qty}`);
    const r = await setStock(id, qty);
    write(`HTTP ${r.status}`);
    write(JSON.stringify(r.data));
}));

document.getElementById("add-line").addEventListener("click", addOrderLine);

document.getElementById("place-order").addEventListener("click", () => guarded(async () => {
    const items = collectOrderItems();
    const pay = document.getElementById("order-pay").value;
    header(`Manuel sipariş: ${items.length} kalem (${pay})`);
    if (items.length === 0) {
        write("En az bir geçerli kalem girin (adet ≥ 1).");
        return;
    }
    write("items: " + JSON.stringify(items));
    const r = await placeOrder(pay, items);
    write(`HTTP ${r.status}`);
    write(JSON.stringify(r.data, null, 2));
    // Not: bu form stoğu sıfırlamaz; guarded() yalnızca tabloyu yeniler (kümülatif stok görünür).
}));

// Initial load: fetch products (fills dropdown markup), then add the first order line.
(async () => {
    await refreshProducts();
    addOrderLine();
})();
