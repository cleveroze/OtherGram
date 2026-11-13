(function () {
  const $ = (id) => document.getElementById(id);
  const engine = new NX.Engine();
  const chart = new CandleChart($("chart"));

  const STORE = "nexora-sim-v1";
  const defaultState = () => ({
    view: "trade",
    mode: "spot",
    symbol: "BTC",
    tf: "15m",
    cat: "all",
    side: "buy",
    otype: "limit",
    leverage: 10,
    god: false,
    balances: { USDT: 25000 },
    positions: [],
    orders: [],
    history: [],
    fills: [],
    ownedNfts: [],
    realized: 0,
    startEquity: 25000
  });

  let state = load() || defaultState();
  let adminOpen = false;
  let adminDragging = false;
  let dragDx = 0, dragDy = 0;
  let blotterTab = "positions";
  let nftCol = "all";
  let nftSort = "new";
  let search = "";

  function load() {
    try { return JSON.parse(localStorage.getItem(STORE) || "null"); } catch { return null; }
  }
  function save() {
    try { localStorage.setItem(STORE, JSON.stringify(state)); } catch { /* ignore quota */ }
  }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
  function isDark(hex) {
    const h = hex.replace("#", "");
    if (h.length < 6) return false;
    const r = parseInt(h.slice(0, 2), 16), g = parseInt(h.slice(2, 4), 16), b = parseInt(h.slice(4, 6), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 < 140;
  }
  function coin(sym) { return engine.markets[sym]; }
  function toast(title, msg, kind) {
    const el = document.createElement("div");
    el.className = "toast glass " + (kind || "");
    el.innerHTML = `<b>${esc(title)}</b>${esc(msg || "")}`;
    $("toasts").appendChild(el);
    setTimeout(() => el.remove(), 3200);
  }
  function flash(el, up) {
    if (!el) return;
    el.classList.remove("flash-up", "flash-down");
    void el.offsetWidth;
    el.classList.add(up ? "flash-up" : "flash-down");
  }

  function equity() {
    let eq = state.balances.USDT || 0;
    for (const [sym, qty] of Object.entries(state.balances)) {
      if (sym === "USDT" || !qty) continue;
      const m = engine.markets[sym];
      if (m) eq += qty * m.price;
    }
    for (const p of state.positions) {
      const px = engine.markets[p.symbol].price;
      const dir = p.side === "long" ? 1 : -1;
      eq += p.margin + dir * (px - p.entry) * p.qty;
    }
    const ethPx = engine.markets.ETH?.price || 0;
    for (const n of state.ownedNfts) eq += n.price * ethPx;
    return eq;
  }
  function avail() { return state.god ? 1e12 : (state.balances.USDT || 0); }

  function setView(v) {
    state.view = v;
    $("viewTrade").classList.toggle("hidden", v !== "trade");
    $("viewNft").classList.toggle("hidden", v !== "nft");
    $("viewWallet").classList.toggle("hidden", v !== "wallet");
    document.querySelectorAll("#mainNav .nav-btn").forEach((b) => b.classList.toggle("active", b.dataset.view === v));
    if (v === "trade") requestAnimationFrame(() => chart.resize());
    if (v === "nft") renderNft();
    if (v === "wallet") renderWallet();
    save();
  }

  function renderTape() {
    const items = NX.COINS.map((c) => {
      const m = engine.markets[c.symbol];
      const ch = engine.chg(c.symbol);
      return `<span class="tape-item"><b>${c.symbol}</b> ${NX.fmt(m.price, c.decimals)} <span class="${ch >= 0 ? "up" : "down"}">${ch >= 0 ? "+" : ""}${ch.toFixed(2)}%</span></span>`;
    }).join("");
    $("tape").innerHTML = `<div class="tape-track">${items}${items}</div>`;
  }

  function renderMarkets() {
    const q = search.toLowerCase();
    const rows = NX.COINS.filter((c) => {
      if (state.cat !== "all" && c.cat !== state.cat) return false;
      return !q || c.symbol.toLowerCase().includes(q) || c.name.toLowerCase().includes(q);
    }).map((c) => {
      const m = engine.markets[c.symbol];
      const ch = engine.chg(c.symbol);
      const active = state.symbol === c.symbol ? "active" : "";
      const fg = isDark(c.color) ? "#f8fafc" : "#081018";
      return `<div class="mrow ${active}" data-sym="${c.symbol}">
        <div class="pair-cell"><span class="coin" style="background:${c.color};color:${fg}">${c.symbol.slice(0, 2)}</span>${c.symbol}<span class="muted">/USDT</span></div>
        <div class="px">${NX.fmt(m.price, c.decimals)}</div>
        <div class="chg ${ch >= 0 ? "up" : "down"}">${ch >= 0 ? "+" : ""}${ch.toFixed(2)}%</div>
      </div>`;
    }).join("");
    $("marketList").innerHTML = rows || `<div class="empty">Ничего не найдено</div>`;
  }

  function renderPair() {
    const m = coin(state.symbol);
    const ch = engine.chg(state.symbol);
    $("pairName").textContent = `${m.symbol}/USDT`;
    $("pairMeta").textContent = `${m.name} · ${state.mode === "spot" ? "Спот" : "Бессрочный фьючерс"} · ${m.cat}`;
    const fg = isDark(m.color) ? "#f8fafc" : "#081018";
    $("pairIcons").innerHTML = `<span class="coin" style="background:${m.color};color:${fg}">${m.symbol.slice(0, 2)}</span><span class="coin" style="background:#26a17b;color:#081018">₮</span>`;
    const lp = $("lastPrice");
    const prev = lp.dataset.px;
    lp.textContent = NX.fmt(m.price, m.decimals);
    lp.dataset.px = m.price;
    lp.className = "last-price " + (ch >= 0 ? "up" : "down");
    if (prev && Number(prev) !== m.price) flash(lp, m.price > Number(prev));
    $("lastChg").textContent = `${ch >= 0 ? "+" : ""}${ch.toFixed(2)}%`;
    $("lastChg").className = "last-chg " + (ch >= 0 ? "up" : "down");
    $("pairKpis").innerHTML = [
      ["24ч макс", NX.fmt(m.high24, m.decimals)],
      ["24ч мин", NX.fmt(m.low24, m.decimals)],
      ["Объём 24ч", NX.fmt(m.vol24, 0)],
      ["Funding", (m.funding >= 0 ? "+" : "") + m.funding.toFixed(4) + "%"]
    ].map(([k, v]) => `<div class="kpi"><span class="muted">${k}</span><b>${v}</b></div>`).join("");
    $("qtyUnit").textContent = m.symbol;
    if (state.otype !== "market" && document.activeElement !== $("priceInput")) {
      $("priceInput").value = NX.fmt(m.price, m.decimals).replace(/,/g, "");
    }
  }

  function renderBook() {
    const m = coin(state.symbol);
    const maxS = Math.max(...m.book.asks.map((x) => x.s), ...m.book.bids.map((x) => x.s), 0.01);
    const row = (x, side) => {
      const tot = x.p * x.s;
      return `<div class="brow">
        <i class="bar" style="width:${(x.s / maxS) * 100}%"></i>
        <span class="px">${NX.fmt(x.p, m.decimals)}</span>
        <span>${x.s.toFixed(4)}</span>
        <span>${NX.fmt(tot, 0)}</span>
      </div>`;
    };
    $("asks").innerHTML = m.book.asks.slice().reverse().map((x) => row(x, "ask")).join("");
    $("bids").innerHTML = m.book.bids.map((x) => row(x, "bid")).join("");
    const spread = m.book.asks[0].p - m.book.bids[0].p;
    const mid = (m.book.asks[0].p + m.book.bids[0].p) / 2;
    $("spreadRow").innerHTML = `<span class="${engine.chg(m.symbol) >= 0 ? "up" : "down"}">${NX.fmt(m.price, m.decimals)}</span><span class="muted">${NX.fmt(spread, m.decimals)} · ${((spread / mid) * 100).toFixed(3)}%</span>`;
    $("spreadLbl").textContent = `спред ${NX.fmt(spread, m.decimals)}`;
  }

  function renderTrades() {
    const m = coin(state.symbol);
    $("tradeList").innerHTML = engine.trades[m.symbol].map((t) => {
      const d = new Date(t.t);
      const tm = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
      return `<div class="trow ${t.side}"><span class="px">${NX.fmt(t.px, m.decimals)}</span><span>${t.qty}</span><span class="muted">${tm}</span></div>`;
    }).join("");
  }

  function renderChart() {
    const m = coin(state.symbol);
    chart.setData(m.candles[state.tf]);
    const hud = chart.hud;
    if (hud) {
      const ch = hud.c - hud.o;
      $("chartHud").innerHTML = `O ${NX.fmt(hud.o)}  H ${NX.fmt(hud.h)}  L ${NX.fmt(hud.l)}  C <span class="${ch >= 0 ? "up" : "down"}">${NX.fmt(hud.c)}</span>  V ${NX.fmt(hud.v, 0)}`;
    }
  }

  function renderHeader() {
    const eq = equity();
    const pnl = eq - state.startEquity;
    $("hdrEquity").textContent = NX.fmtUsd(eq);
    $("hdrAvail").textContent = NX.fmtUsd(state.balances.USDT || 0);
    const p = $("hdrPnl");
    p.textContent = `${pnl >= 0 ? "+" : ""}${NX.fmtUsd(pnl)}`;
    p.className = pnl >= 0 ? "up" : "down";
    $("ticketAvail").textContent = NX.fmt(state.balances.USDT || 0, 2) + " USDT";
    $("ordBadge").textContent = String(state.orders.length);
  }

  function renderTicket() {
    const fut = state.mode === "futures";
    $("levRow").classList.toggle("hidden", !fut);
    $("priceField").classList.toggle("hidden", state.otype === "market");
    $("stopField").classList.toggle("hidden", state.otype !== "stop");
    $("levVal").textContent = state.leverage + "x";
    const buy = state.side === "buy";
    const label = fut
      ? (buy ? `Открыть лонг ${state.symbol}` : `Открыть шорт ${state.symbol}`)
      : (buy ? `Купить ${state.symbol}` : `Продать ${state.symbol}`);
    const btn = $("submitBtn");
    btn.textContent = label;
    btn.className = "submit " + (buy ? "buy" : "sell");
    updateTotal();
  }

  function updateTotal() {
    const m = coin(state.symbol);
    const qty = Number($("qtyInput").value) || 0;
    const px = state.otype === "market" ? m.price : Number($("priceInput").value) || m.price;
    $("orderTotal").textContent = NX.fmt(qty * px, 2) + " USDT";
  }

  function renderBlotter() {
    const body = $("blotterBody");
    if (blotterTab === "positions") {
      if (!state.positions.length) { body.innerHTML = `<div class="empty">Нет открытых позиций</div>`; return; }
      body.innerHTML = `<table class="table"><thead><tr><th>Пара</th><th>Сторона</th><th>Размер</th><th>Вход</th><th>Mark</th><th>PnL</th><th>Liq</th><th></th></tr></thead><tbody>${
        state.positions.map((p, i) => {
          const px = engine.markets[p.symbol].price;
          const dir = p.side === "long" ? 1 : -1;
          const pnl = dir * (px - p.entry) * p.qty;
          const roe = (pnl / p.margin) * 100;
          return `<tr>
            <td>${p.symbol}USDT</td>
            <td class="${p.side === "long" ? "up" : "down"}">${p.side} ${p.leverage}x</td>
            <td>${p.qty}</td>
            <td>${NX.fmt(p.entry)}</td>
            <td>${NX.fmt(px)}</td>
            <td class="${pnl >= 0 ? "up" : "down"}">${pnl >= 0 ? "+" : ""}${NX.fmt(pnl, 2)} (${roe.toFixed(1)}%)</td>
            <td>${NX.fmt(p.liq)}</td>
            <td><button class="tiny" data-close="${i}">Закрыть</button></td>
          </tr>`;
        }).join("")
      }</tbody></table>`;
    } else if (blotterTab === "orders") {
      if (!state.orders.length) { body.innerHTML = `<div class="empty">Нет активных ордеров</div>`; return; }
      body.innerHTML = `<table class="table"><thead><tr><th>Пара</th><th>Тип</th><th>Сторона</th><th>Цена</th><th>Кол-во</th><th></th></tr></thead><tbody>${
        state.orders.map((o) => `<tr>
          <td>${o.symbol}</td><td>${o.otype}</td>
          <td class="${o.side === "buy" || o.side === "long" ? "up" : "down"}">${o.side}</td>
          <td>${NX.fmt(o.price)}</td><td>${o.qty}</td>
          <td><button class="tiny" data-cancel="${o.id}">Отмена</button></td>
        </tr>`).join("")
      }</tbody></table>`;
    } else if (blotterTab === "history") {
      if (!state.history.length) { body.innerHTML = `<div class="empty">История пуста</div>`; return; }
      body.innerHTML = `<table class="table"><thead><tr><th>Время</th><th>Пара</th><th>Сторона</th><th>Цена</th><th>Кол-во</th><th>Итого</th></tr></thead><tbody>${
        state.history.slice(0, 40).map((h) => {
          const d = new Date(h.t);
          return `<tr>
            <td>${d.toLocaleTimeString("ru-RU")}</td>
            <td>${h.symbol}</td>
            <td class="${h.side.includes("sell") || h.side === "short" ? "down" : "up"}">${h.side}</td>
            <td>${NX.fmt(h.price)}</td><td>${h.qty}</td><td>${NX.fmt(h.total, 2)}</td>
          </tr>`;
        }).join("")
      }</tbody></table>`;
    } else {
      body.innerHTML = (state.fills.length ? state.fills.slice(0, 30).map((f) =>
        `<div class="trow ${f.ok ? "buy" : "sell"}" style="grid-template-columns:1fr"><span>${esc(f.msg)}</span></div>`
      ).join("") : `<div class="empty">Лента событий пуста</div>`);
    }
  }

  function addFill(msg, ok) {
    state.fills.unshift({ msg, ok, t: Date.now() });
    if (state.fills.length > 80) state.fills.pop();
  }

  function placeOrder() {
    const m = coin(state.symbol);
    let qty = Number($("qtyInput").value);
    if (!qty || qty <= 0) { toast("Ордер", "Укажите количество", "down"); return; }
    const px = state.otype === "market" ? m.price : Number($("priceInput").value);
    if (!px || px <= 0) { toast("Ордер", "Некорректная цена"); return; }
    const stop = Number($("stopInput").value);
    if (state.otype === "stop" && !stop) { toast("Стоп", "Нужна стоп-цена"); return; }

    if (state.mode === "spot") {
      if (state.side === "buy") {
        const cost = qty * (state.otype === "market" ? m.price : px);
        if (!state.god && cost > (state.balances.USDT || 0)) { toast("Маржа", "Недостаточно USDT"); return; }
        if (state.otype === "market") fillSpotBuy(qty, m.price);
        else state.orders.push({ id: Date.now(), symbol: m.symbol, side: "buy", otype: state.otype, price: px, stop, qty });
      } else {
        const have = state.balances[m.symbol] || 0;
        if (!state.god && qty > have) { toast("Баланс", `Недостаточно ${m.symbol}`); return; }
        if (state.otype === "market") fillSpotSell(qty, m.price);
        else state.orders.push({ id: Date.now(), symbol: m.symbol, side: "sell", otype: state.otype, price: px, stop, qty });
      }
    } else {
      const side = state.side === "buy" ? "long" : "short";
      const notional = qty * (state.otype === "market" ? m.price : px);
      const margin = notional / state.leverage;
      if (!state.god && margin > (state.balances.USDT || 0)) { toast("Маржа", "Недостаточно маржи"); return; }
      if (state.otype === "market") openPos(side, qty, m.price);
      else state.orders.push({ id: Date.now(), symbol: m.symbol, side, otype: state.otype, price: px, stop, qty, leverage: state.leverage });
    }
    toast("Ордер принят", `${state.otype.toUpperCase()} ${state.symbol}`);
    addFill(`Ордер ${state.otype} ${state.symbol} ${qty}`, true);
    $("qtyInput").value = "";
    save(); renderAll();
  }

  function fillSpotBuy(qty, price) {
    const cost = qty * price;
    if (!state.god) state.balances.USDT = (state.balances.USDT || 0) - cost;
    state.balances[state.symbol] = (state.balances[state.symbol] || 0) + qty;
    state.history.unshift({ t: Date.now(), symbol: state.symbol, side: "buy", price, qty, total: cost });
    addFill(`Куплено ${qty} ${state.symbol} @ ${NX.fmt(price)}`, true);
  }
  function fillSpotSell(qty, price) {
    const have = state.balances[state.symbol] || 0;
    const use = Math.min(qty, have);
    state.balances[state.symbol] = have - use;
    const total = use * price;
    state.balances.USDT = (state.balances.USDT || 0) + total;
    state.history.unshift({ t: Date.now(), symbol: state.symbol, side: "sell", price, qty: use, total });
    addFill(`Продано ${use} ${state.symbol} @ ${NX.fmt(price)}`, true);
  }
  function openPos(side, qty, price) {
    const notional = qty * price;
    const margin = notional / state.leverage;
    if (!state.god) state.balances.USDT = (state.balances.USDT || 0) - margin;
    const dir = side === "long" ? 1 : -1;
    const liq = price * (1 - dir * (0.9 / state.leverage));
    state.positions.push({ symbol: state.symbol, side, qty, entry: price, leverage: state.leverage, margin, liq });
    state.history.unshift({ t: Date.now(), symbol: state.symbol, side, price, qty, total: notional });
    addFill(`Позиция ${side} ${qty} ${state.symbol}`, true);
  }
  function closePos(i) {
    const p = state.positions[i];
    if (!p) return;
    const px = engine.markets[p.symbol].price;
    const dir = p.side === "long" ? 1 : -1;
    const pnl = dir * (px - p.entry) * p.qty;
    state.balances.USDT = (state.balances.USDT || 0) + p.margin + pnl;
    state.realized += pnl;
    state.positions.splice(i, 1);
    toast("Позиция закрыта", `PnL ${pnl >= 0 ? "+" : ""}${NX.fmt(pnl, 2)} USDT`);
    addFill(`Закрытие ${p.symbol} PnL ${NX.fmt(pnl, 2)}`, pnl >= 0);
    save(); renderAll();
  }

  function matchOrders() {
    const keep = [];
    for (const o of state.orders) {
      const m = engine.markets[o.symbol];
      const px = m.price;
      let hit = false;
      if (o.otype === "stop") {
        if (o.side === "buy" || o.side === "long") hit = px >= o.stop;
        else hit = px <= o.stop;
        if (hit) o.otype = "market";
      }
      if (o.otype === "limit") {
        if (o.side === "buy" || o.side === "long") hit = px <= o.price;
        else hit = px >= o.price;
      }
      if (o.otype === "market") hit = true;
      if (hit) {
        const fillPx = o.otype === "limit" ? o.price : px;
        const prev = state.symbol;
        state.symbol = o.symbol;
        if (o.side === "buy") fillSpotBuy(o.qty, fillPx);
        else if (o.side === "sell") fillSpotSell(o.qty, fillPx);
        else openPos(o.side, o.qty, fillPx);
        state.symbol = prev;
        toast("Исполнено", `${o.symbol} ${o.qty}`);
      } else keep.push(o);
    }
    state.orders = keep;
  }

  function checkLiq() {
    const left = [];
    for (const p of state.positions) {
      const px = engine.markets[p.symbol].price;
      const bust = p.side === "long" ? px <= p.liq : px >= p.liq;
      if (bust && !state.god) {
        state.realized -= p.margin;
        addFill(`Ликвидация ${p.symbol} ${p.side}`, false);
        toast("Ликвидация", `${p.symbol} позиция закрыта биржей`);
      } else left.push(p);
    }
    state.positions = left;
  }

  function renderNft() {
    const listed = engine.nfts.filter((n) => n.listed);
    const vol = listed.reduce((s, n) => s + n.price, 0);
    $("nftKpis").innerHTML = [
      ["Лотов", listed.length],
      ["Коллекций", NX.COLLECTIONS.length],
      ["Floor ETH", NX.fmt(Math.min(...listed.map((n) => n.price)), 3)],
      ["Объём", NX.fmt(vol, 1) + " ETH"]
    ].map(([k, v]) => `<div class="kpi"><span class="muted">${k}</span><b>${v}</b></div>`).join("");
    $("collectionList").innerHTML = `<div class="col-item ${nftCol === "all" ? "active" : ""}" data-col="all"><div class="col-swatch" style="background:#1b2030"></div><div><b>Все</b><div class="muted">${listed.length} лотов</div></div></div>` +
      NX.COLLECTIONS.map((c) => {
        const n = listed.filter((x) => x.collection === c.id).length;
        return `<div class="col-item ${nftCol === c.id ? "active" : ""}" data-col="${c.id}">
          <div class="col-swatch" style="background:linear-gradient(135deg,${c.tint[0]},${c.tint[1]})"></div>
          <div><b>${c.name}</b><div class="muted">floor ${c.floor} ETH · ${n}</div></div>
        </div>`;
      }).join("");
    let items = engine.nfts.filter((n) => n.listed && (nftCol === "all" || n.collection === nftCol));
    if (nftSort === "priceAsc") items.sort((a, b) => a.price - b.price);
    if (nftSort === "priceDesc") items.sort((a, b) => b.price - a.price);
    if (nftSort === "rare") {
      const rank = { legendary: 5, epic: 4, rare: 3, uncommon: 2, common: 1 };
      items.sort((a, b) => rank[b.rarity] - rank[a.rarity]);
    }
    $("nftGridTitle").textContent = nftCol === "all" ? "Все лоты" : NX.COLLECTIONS.find((c) => c.id === nftCol).name;
    $("nftGrid").innerHTML = items.map(nftCard).join("");
    $("admNft").innerHTML = engine.nfts.map((n) => `<option value="${n.id}">${n.name} · ${n.rarity}</option>`).join("");
  }
  function nftCard(n) {
    return `<article class="nft-card" data-nft="${n.id}">
      <div class="nft-art">${n.art}</div>
      <div class="nft-info">
        <div class="nm">${esc(n.name)}</div>
        <div class="col">${esc(n.collectionName)}</div>
        <div class="pr"><span class="rare ${n.rarity}">${n.rarity}</span><span>${n.listed ? n.price + " ETH" : "held"}</span></div>
      </div>
    </article>`;
  }
  function openNft(id) {
    const n = engine.nfts.find((x) => x.id === id) || state.ownedNfts.find((x) => x.id === id);
    if (!n) return;
    const owned = state.ownedNfts.some((x) => x.id === n.id);
    $("modal").classList.remove("hidden");
    $("modal").innerHTML = `<div class="sheet glass">
      <div class="art">${n.art}</div>
      <h3>${esc(n.name)}</h3>
      <p>${esc(n.collectionName)} · <span class="rare ${n.rarity}">${n.rarity}</span></p>
      <p>Цена ${n.price} ETH ≈ ${NX.fmt(n.price * 3521, 0)} USDT. Покупка списывает бумажный USDT.</p>
      <div class="row">
        ${owned ? `<button class="ghost-cta" data-sellnft="${n.id}">Продать</button>` : `<button class="buy-cta" data-buynft="${n.id}">Купить</button>`}
        <button class="ghost-cta" id="modalClose">Закрыть</button>
      </div>
    </div>`;
  }
  function buyNft(id) {
    const n = engine.nfts.find((x) => x.id === id);
    if (!n || !n.listed) return;
    const cost = n.price * engine.markets.ETH.price;
    if (!state.god && cost > (state.balances.USDT || 0)) { toast("NFT", "Недостаточно USDT"); return; }
    if (!state.god) state.balances.USDT -= cost;
    n.listed = false;
    state.ownedNfts.push({ ...n });
    toast("NFT", `Куплен ${n.name}`);
    addFill(`NFT buy ${n.name}`, true);
    $("modal").classList.add("hidden");
    save(); renderNft(); renderHeader();
  }
  function sellNft(id) {
    const i = state.ownedNfts.findIndex((x) => x.id === id);
    if (i < 0) return;
    const n = state.ownedNfts[i];
    const credit = n.price * engine.markets.ETH.price * 0.97;
    state.balances.USDT += credit;
    state.ownedNfts.splice(i, 1);
    const src = engine.nfts.find((x) => x.id === n.id);
    if (src) src.listed = true;
    toast("NFT", `Продан ${n.name}`);
    $("modal").classList.add("hidden");
    save(); renderNft(); renderWallet(); renderHeader();
  }

  function renderWallet() {
    const eq = equity();
    $("walletTotal").textContent = NX.fmtUsd(eq);
    const pnl = eq - state.startEquity;
    $("walletPnl").textContent = `PnL ${pnl >= 0 ? "+" : ""}${NX.fmtUsd(pnl)}`;
    $("walletPnl").className = pnl >= 0 ? "up" : "down";
    const rows = [{ symbol: "USDT", qty: state.balances.USDT || 0, px: 1, color: "#26a17b" }];
    for (const c of NX.COINS) {
      const q = state.balances[c.symbol] || 0;
      if (q > 0) rows.push({ symbol: c.symbol, qty: q, px: engine.markets[c.symbol].price, color: c.color });
    }
    const total = rows.reduce((s, r) => s + r.qty * r.px, 0) || 1;
    $("allocBar").innerHTML = rows.map((r) => `<i style="width:${(r.qty * r.px / total) * 100}%;background:${r.color}"></i>`).join("");
    $("assetTable").innerHTML = `<table class="table"><thead><tr><th>Актив</th><th>Кол-во</th><th>Цена</th><th>Стоимость</th><th>Доля</th></tr></thead><tbody>${
      rows.map((r) => `<tr>
        <td>${r.symbol}</td><td>${NX.fmt(r.qty, r.symbol === "USDT" ? 2 : 6)}</td>
        <td>${NX.fmt(r.px)}</td><td>${NX.fmtUsd(r.qty * r.px)}</td>
        <td>${((r.qty * r.px / total) * 100).toFixed(1)}%</td>
      </tr>`).join("")
    }</tbody></table>`;
    $("ownedGrid").innerHTML = state.ownedNfts.length
      ? state.ownedNfts.map(nftCard).join("")
      : `<div class="empty">NFT пока нет — купите на маркетплейсе или выдайте из админки</div>`;
  }

  function renderTf() {
    $("tfRow").innerHTML = NX.TFS.map((t) => `<button class="tool-btn ${state.tf === t.id ? "active" : ""}" data-tf="${t.id}">${t.label}</button>`).join("");
  }

  function renderAll() {
    renderHeader();
    if (state.view === "trade") {
      renderMarkets();
      renderPair();
      renderBook();
      renderTrades();
      renderChart();
      renderTicket();
      renderBlotter();
    } else if (state.view === "nft") renderNft();
    else renderWallet();
  }

  function newsFlash() {
    const headlines = [
      "ETF по BTC получил приток $410M за сессию",
      "Крупный кит перевёл 12,400 ETH на холодный кошелёк",
      "Фандинг на перпах ушёл в отрицательную зону",
      "Слух: листинг нового L2 токена на NEXORA",
      "Ликвидации шортов за час превысили $86M"
    ];
    const h = headlines[Math.floor(Math.random() * headlines.length)];
    toast("Лента новостей", h);
    addFill(h, true);
    engine.pump(state.symbol, Math.random() > 0.4 ? 1 : -1);
  }

  function bind() {
    $("mainNav").addEventListener("click", (e) => {
      const b = e.target.closest("[data-view]");
      if (b) setView(b.dataset.view);
    });
    $("modeSwitch").addEventListener("click", (e) => {
      const b = e.target.closest("[data-mode]");
      if (!b) return;
      state.mode = b.dataset.mode;
      document.querySelectorAll("#modeSwitch .mode-btn").forEach((x) => x.classList.toggle("active", x === b));
      renderPair(); renderTicket(); save();
    });
    $("marketCats").addEventListener("click", (e) => {
      const b = e.target.closest("[data-cat]");
      if (!b) return;
      state.cat = b.dataset.cat;
      document.querySelectorAll("#marketCats .chip").forEach((x) => x.classList.toggle("active", x === b));
      renderMarkets();
    });
    $("marketSearch").addEventListener("input", (e) => { search = e.target.value; renderMarkets(); });
    $("marketList").addEventListener("click", (e) => {
      const row = e.target.closest("[data-sym]");
      if (!row) return;
      state.symbol = row.dataset.sym;
      save(); renderAll();
    });
    $("tfRow").addEventListener("click", (e) => {
      const b = e.target.closest("[data-tf]");
      if (!b) return;
      state.tf = b.dataset.tf;
      renderTf(); renderChart(); save();
    });
    document.querySelector(".ind-row").addEventListener("click", (e) => {
      const b = e.target.closest("[data-ind]");
      if (b) {
        chart.inds[b.dataset.ind] = !chart.inds[b.dataset.ind];
        b.classList.toggle("active", chart.inds[b.dataset.ind]);
        chart.draw();
      }
      if (e.target.id === "fitBtn") chart.fit();
    });
    $("sideTabs").addEventListener("click", (e) => {
      const b = e.target.closest("[data-side]");
      if (!b) return;
      state.side = b.dataset.side;
      document.querySelectorAll("#sideTabs button").forEach((x) => x.classList.toggle("active", x === b));
      renderTicket();
    });
    $("otypeRow").addEventListener("click", (e) => {
      const b = e.target.closest("[data-otype]");
      if (!b) return;
      state.otype = b.dataset.otype;
      document.querySelectorAll("#otypeRow .chip").forEach((x) => x.classList.toggle("active", x === b));
      renderTicket();
    });
    $("levSlider").addEventListener("input", (e) => { state.leverage = Number(e.target.value); renderTicket(); });
    $("priceInput").addEventListener("input", updateTotal);
    $("qtyInput").addEventListener("input", updateTotal);
    $("pctRow").addEventListener("click", (e) => {
      const b = e.target.closest("[data-pct]");
      if (!b) return;
      const pct = Number(b.dataset.pct) / 100;
      const m = coin(state.symbol);
      if (state.mode === "spot" && state.side === "buy") {
        const px = state.otype === "market" ? m.price : Number($("priceInput").value) || m.price;
        $("qtyInput").value = ((avail() * pct) / px).toFixed(m.decimals > 4 ? 2 : 6);
      } else if (state.mode === "spot") {
        $("qtyInput").value = (((state.balances[m.symbol] || 0) * pct)).toFixed(6);
      } else {
        const px = m.price;
        $("qtyInput").value = ((avail() * pct * state.leverage) / px).toFixed(6);
      }
      updateTotal();
    });
    $("submitBtn").addEventListener("click", placeOrder);
    $("blotterTabs").addEventListener("click", (e) => {
      const b = e.target.closest("[data-tab]");
      if (!b) return;
      blotterTab = b.dataset.tab;
      document.querySelectorAll("#blotterTabs button").forEach((x) => x.classList.toggle("active", x === b));
      renderBlotter();
    });
    $("blotterBody").addEventListener("click", (e) => {
      const c = e.target.closest("[data-close]");
      const k = e.target.closest("[data-cancel]");
      if (c) closePos(Number(c.dataset.close));
      if (k) {
        state.orders = state.orders.filter((o) => String(o.id) !== k.dataset.cancel);
        save(); renderBlotter(); renderHeader();
      }
    });
    $("collectionList").addEventListener("click", (e) => {
      const b = e.target.closest("[data-col]");
      if (!b) return;
      nftCol = b.dataset.col;
      renderNft();
    });
    $("nftSort").addEventListener("change", (e) => { nftSort = e.target.value; renderNft(); });
    $("nftGrid").addEventListener("click", (e) => {
      const c = e.target.closest("[data-nft]");
      if (c) openNft(Number(c.dataset.nft));
    });
    $("ownedGrid").addEventListener("click", (e) => {
      const c = e.target.closest("[data-nft]");
      if (c) openNft(Number(c.dataset.nft));
    });
    $("modal").addEventListener("click", (e) => {
      if (e.target.id === "modal" || e.target.id === "modalClose") $("modal").classList.add("hidden");
      const b = e.target.closest("[data-buynft]");
      const s = e.target.closest("[data-sellnft]");
      if (b) buyNft(Number(b.dataset.buynft));
      if (s) sellNft(Number(s.dataset.sellnft));
    });

    const fab = $("adminFab");
    const panel = $("adminPanel");
    const openAdmin = () => {
      adminOpen = !adminOpen;
      fab.classList.toggle("open", adminOpen);
      panel.classList.toggle("show", adminOpen);
      panel.setAttribute("aria-hidden", adminOpen ? "false" : "true");
    };
    fab.addEventListener("click", openAdmin);
    $("adminClose").addEventListener("click", openAdmin);
    const drag = $("adminDrag");
    drag.addEventListener("pointerdown", (e) => {
      if (e.target.closest(".admin-x")) return;
      adminDragging = true;
      const r = panel.getBoundingClientRect();
      dragDx = e.clientX - r.left;
      dragDy = e.clientY - r.top;
      panel.style.right = "auto";
      panel.style.bottom = "auto";
      panel.style.left = r.left + "px";
      panel.style.top = r.top + "px";
      panel.style.transformOrigin = "center";
      drag.setPointerCapture(e.pointerId);
    });
    drag.addEventListener("pointermove", (e) => {
      if (!adminDragging) return;
      const x = NX.clamp(e.clientX - dragDx, 8, window.innerWidth - panel.offsetWidth - 8);
      const y = NX.clamp(e.clientY - dragDy, 8, window.innerHeight - 48);
      panel.style.left = x + "px";
      panel.style.top = y + "px";
    });
    drag.addEventListener("pointerup", () => { adminDragging = false; });

    $("admAddCash").addEventListener("click", () => {
      const n = Number($("admCash").value) || 0;
      state.balances.USDT = (state.balances.USDT || 0) + n;
      toast("Админ", `Начислено ${NX.fmt(n, 0)} USDT`);
      save(); renderHeader();
    });
    $("admVol").addEventListener("input", (e) => {
      engine.volatility = Number(e.target.value);
      $("admVolLbl").textContent = engine.volatility.toFixed(1) + "×";
    });
    $("admSpeed").addEventListener("input", (e) => {
      engine.speed = Number(e.target.value);
      $("admSpeedLbl").textContent = String(engine.speed);
    });
    $("admPump").addEventListener("click", () => { engine.pump(state.symbol, 1); toast("Памп", state.symbol); });
    $("admDump").addEventListener("click", () => { engine.pump(state.symbol, -1); toast("Дамп", state.symbol); });
    $("admFreeze").addEventListener("click", () => {
      engine.freeze(!engine.frozen);
      $("admFreeze").textContent = engine.frozen ? "Анфриз" : "Фриз";
      toast("Рынок", engine.frozen ? "заморожен" : "снова живой");
    });
    $("admNews").addEventListener("click", newsFlash);
    $("admGrantNft").addEventListener("click", () => {
      const id = Number($("admNft").value);
      const n = engine.nfts.find((x) => x.id === id);
      if (!n) return;
      n.listed = false;
      if (!state.ownedNfts.some((x) => x.id === n.id)) state.ownedNfts.push({ ...n });
      toast("NFT", `Выдан ${n.name}`);
      save();
    });
    $("admGod").addEventListener("change", (e) => { state.god = e.target.checked; save(); });
    $("admReset").addEventListener("click", () => {
      if (!confirm("Сбросить бумажный аккаунт?")) return;
      const keep = { view: state.view, symbol: state.symbol };
      state = Object.assign(defaultState(), keep);
      engine.nfts.forEach((n) => { n.listed = true; });
      save(); renderAll(); toast("Аккаунт", "сброшен к $25,000");
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        if (adminOpen) openAdmin();
        $("modal").classList.add("hidden");
      }
    });
  }

  function loop() {
    const delay = [220, 160, 110, 80, 55, 40, 28, 18][NX.clamp(engine.speed, 1, 8) - 1];
    engine.tick();
    matchOrders();
    checkLiq();
    renderHeader();
    if (state.view === "trade") {
      renderPair();
      renderBook();
      renderTrades();
      renderChart();
      document.querySelectorAll("#marketList .mrow").forEach((row) => {
        const m = engine.markets[row.dataset.sym];
        if (!m) return;
        const ch = engine.chg(m.symbol);
        const px = row.querySelector(".px");
        const cg = row.querySelector(".chg");
        if (px) px.textContent = NX.fmt(m.price, m.decimals);
        if (cg) {
          cg.textContent = `${ch >= 0 ? "+" : ""}${ch.toFixed(2)}%`;
          cg.className = "chg " + (ch >= 0 ? "up" : "down");
        }
      });
      if (blotterTab === "positions" && state.positions.length) renderBlotter();
    }
    setTimeout(loop, delay);
  }

  bind();
  document.querySelectorAll("#modeSwitch .mode-btn").forEach((x) => x.classList.toggle("active", x.dataset.mode === state.mode));
  document.querySelectorAll("#sideTabs button").forEach((x) => x.classList.toggle("active", x.dataset.side === state.side));
  document.querySelectorAll("#otypeRow .chip").forEach((x) => x.classList.toggle("active", x.dataset.otype === state.otype));
  $("levSlider").value = String(state.leverage);
  $("admGod").checked = !!state.god;
  renderTf();
  renderTape();
  renderAll();
  setView(state.view);
  setTimeout(() => $("splash").classList.add("hide"), 700);
  setTimeout(loop, 400);
  setInterval(save, 4000);
})();
