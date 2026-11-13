import { createChart } from "./chart.js";
import {
  clamp,
  esc,
  fmtClock,
  fmtCompact,
  fmtNum,
  fmtPct,
  fmtPrice,
  fmtQty,
  fmtTradeTime,
  parseNum,
} from "./format.js";
import { TIMEFRAMES, createMarket } from "./market.js";
import { COLLECTION_LIST, NFTS, NFT_BY_ID, collectionById, nftSvg, rarityLabel } from "./nft.js";

const KEY = "velora-sim-v1";
const POS_KEY = "velora-admin-pos";
const $ = (id) => document.getElementById(id);

const market = createMarket();
let feeBps = 4;
let floor = 1;
let audioOn = false;
let orderSide = "buy";
let orderType = "market";
let tf = "15m";
let emaOn = true;
let volOn = true;
let blotterTab = "pos";
let nftFilter = "all";
let modalId = null;
let modalPainted = null;
let seq = Date.now() % 1e9;
let resetArmed = false;
let dirtyBlotter = true;
let lastTradeKey = "";
let audioCtx = null;
let sessionBase = 0;
let eqSeries = [];

const activeSaved = sessionStorage.getItem("velora-sym");
let active = market.get(activeSaved) ? activeSaved : "BTC";

function starterPositions() {
  const spec = [
    ["BTC", 0.075, 0.984],
    ["ETH", 1.15, 1.018],
    ["SOL", 18, 0.972],
    ["TON", 140, 1.008],
  ];
  const positions = {};
  for (const [sym, qty, mult] of spec) {
    const pair = market.get(sym);
    positions[sym] = { qty, avg: pair.price * mult };
  }
  return positions;
}

function freshState() {
  return {
    usdt: 25000,
    positions: starterPositions(),
    orders: [],
    fills: [],
    owned: {},
    favorites: ["BTC", "ETH", "SOL", "TON", "PEPE"],
    realized: 0,
    fees: 0,
  };
}

function loadState() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return freshState();
    const data = JSON.parse(raw);
    if (!data || data.v !== 1) return freshState();
    const base = freshState();
    base.usdt = Number.isFinite(data.usdt) ? Math.max(0, data.usdt) : base.usdt;
    base.realized = Number(data.realized) || 0;
    base.fees = Number(data.fees) || 0;
    base.favorites = Array.isArray(data.favorites) ? data.favorites.filter((s) => market.get(s)) : base.favorites;
    base.positions = {};
    if (data.positions && typeof data.positions === "object") {
      for (const [sym, pos] of Object.entries(data.positions)) {
        if (!market.get(sym)) continue;
        const qty = Number(pos?.qty);
        const avg = Number(pos?.avg);
        if (qty > 0 && avg > 0) base.positions[sym] = { qty, avg };
      }
    }
    base.orders = Array.isArray(data.orders)
      ? data.orders.filter((o) => market.get(o.symbol) && o.qty > 0 && o.price > 0).slice(0, 40)
      : [];
    base.fills = Array.isArray(data.fills) ? data.fills.slice(0, 80) : [];
    base.owned = {};
    if (data.owned && typeof data.owned === "object") {
      for (const [id, own] of Object.entries(data.owned)) {
        if (NFT_BY_ID[id] && Number(own?.buy) > 0) base.owned[id] = { buy: Number(own.buy), ts: Number(own.ts) || Date.now() };
      }
    }
    if (Number.isFinite(data.feeBps)) feeBps = clamp(data.feeBps, 0, 20);
    if (Number.isFinite(data.floor)) floor = clamp(data.floor, 0.5, 2.5);
    audioOn = Boolean(data.audio);
    return base;
  } catch {
    return freshState();
  }
}

let state = loadState();

function feeRate() {
  return feeBps / 10000;
}

let saveTimer = 0;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(writeState, 150);
}
function writeState() {
  const payload = {
    v: 1,
    usdt: state.usdt,
    positions: state.positions,
    orders: state.orders,
    fills: state.fills.slice(0, 80),
    owned: state.owned,
    favorites: state.favorites,
    realized: state.realized,
    fees: state.fees,
    feeBps,
    floor,
    audio: audioOn,
  };
  try {
    localStorage.setItem(KEY, JSON.stringify(payload));
  } catch {
    /* private mode */
  }
}

function equity() {
  let value = state.usdt;
  for (const [sym, pos] of Object.entries(state.positions)) {
    const pair = market.get(sym);
    if (pair && pos.qty > 0) value += pos.qty * pair.price;
  }
  for (const id of Object.keys(state.owned)) {
    const nft = NFT_BY_ID[id];
    if (nft) value += nft.price * floor * 0.97;
  }
  return value;
}

function locked(asset) {
  let sum = 0;
  for (const order of state.orders) if (order.lockAsset === asset) sum += order.lockQty;
  return sum;
}
function availableUSDT() {
  return state.usdt - locked("USDT");
}
function availableBase(sym) {
  return (state.positions[sym]?.qty || 0) - locked(sym);
}

function toast(text) {
  const host = $("toasts");
  const el = document.createElement("div");
  el.className = "toast";
  el.textContent = text;
  host.append(el);
  while (host.children.length > 3) host.firstChild.remove();
  setTimeout(() => el.classList.add("out"), 3200);
  setTimeout(() => el.remove(), 3600);
}

function blip(up) {
  if (!audioOn) return;
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) return;
  audioCtx ||= new Ctx();
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  osc.frequency.value = up ? 740 : 360;
  gain.gain.value = 0.03;
  osc.connect(gain);
  gain.connect(audioCtx.destination);
  osc.start();
  gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 0.07);
  osc.stop(audioCtx.currentTime + 0.08);
}

function removeOrder(id) {
  state.orders = state.orders.filter((order) => order.id !== id);
}

function fill(order, price) {
  const notional = order.qty * price;
  const fee = notional * feeRate();
  if (order.side === "buy") {
    if (state.usdt + 1e-6 < notional + fee) {
      removeOrder(order.id);
      toast("Не хватило USDT");
      return;
    }
    state.usdt -= notional + fee;
    const pos = state.positions[order.symbol] || { qty: 0, avg: 0 };
    const next = pos.qty + order.qty;
    pos.avg = (pos.avg * pos.qty + notional) / next;
    pos.qty = next;
    state.positions[order.symbol] = pos;
  } else {
    const pos = state.positions[order.symbol];
    if (!pos || pos.qty + 1e-8 < order.qty) {
      removeOrder(order.id);
      toast("Не хватило монет");
      return;
    }
    state.realized += (price - pos.avg) * order.qty - fee;
    pos.qty -= order.qty;
    if (pos.qty < 1e-8) delete state.positions[order.symbol];
    state.usdt += notional - fee;
  }
  state.fees += fee;
  state.fills.unshift({
    id: order.id,
    ts: Date.now(),
    symbol: order.symbol,
    side: order.side,
    price,
    qty: order.qty,
    fee,
    notional,
  });
  if (state.fills.length > 100) state.fills.pop();
  removeOrder(order.id);
  dirtyBlotter = true;
  save();
  blip(order.side === "buy");
  toast(`${order.side === "buy" ? "Куплено" : "Продано"} ${order.symbol}`);
}

function matchOrders() {
  if (market.paused) return;
  for (const order of [...state.orders]) {
    const pair = market.get(order.symbol);
    if (!pair) continue;
    if (order.side === "buy" && pair.ask <= order.price) fill(order, Math.min(order.price, pair.ask));
    else if (order.side === "sell" && pair.bid >= order.price) fill(order, Math.max(order.price, pair.bid));
  }
}

function place() {
  const pair = market.get(active);
  const qty = parseNum($("qty-input").value);
  const px = orderType === "market" ? (orderSide === "buy" ? pair.ask : pair.bid) : parseNum($("price-input").value);
  if (!(qty > 0) || !(px > 0)) return toast("Укажите цену и количество");
  const notional = qty * px;
  if (notional < 5) return toast("Минимум 5 USDT");
  const fee = notional * feeRate();
  if (orderSide === "buy" && availableUSDT() + 1e-6 < notional + fee) return toast("Недостаточно USDT");
  if (orderSide === "sell" && availableBase(active) + 1e-8 < qty) return toast("Недостаточно монет");
  const order = {
    id: "o" + ++seq,
    ts: Date.now(),
    symbol: active,
    side: orderSide,
    type: orderType,
    price: px,
    qty,
    lockAsset: orderSide === "buy" ? "USDT" : active,
    lockQty: orderSide === "buy" ? notional + fee : qty,
  };
  const marketable =
    orderType === "market" ||
    (orderSide === "buy" && px >= pair.ask) ||
    (orderSide === "sell" && px <= pair.bid);
  if (marketable) {
    const fillPx = orderSide === "buy" ? Math.min(px, pair.ask) : Math.max(px, pair.bid);
    fill(order, orderType === "market" ? px : fillPx);
  } else {
    state.orders.unshift(order);
    dirtyBlotter = true;
    save();
    toast("Лимит в стакане");
  }
  $("qty-input").value = "";
}

function closePosition(sym) {
  const pair = market.get(sym);
  const qty = availableBase(sym);
  if (!pair || qty <= 0) return toast("Свободного объёма нет");
  fill(
    {
      id: "c" + ++seq,
      symbol: sym,
      side: "sell",
      type: "market",
      price: pair.bid,
      qty,
      lockAsset: sym,
      lockQty: qty,
    },
    pair.bid
  );
}

const chart = createChart($("chart"));
const rows = new Map();

function sparkPath(values) {
  if (!values || values.length < 2) return "";
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  return values
    .map((v, i) => {
      const x = (i / (values.length - 1)) * 54;
      const y = 18 - ((v - min) / span) * 16;
      return `${i ? "L" : "M"}${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(" ");
}

function buildMarkets() {
  const list = $("mlist");
  for (const pair of market.list()) {
    const row = document.createElement("button");
    row.type = "button";
    row.className = "mrow";
    row.dataset.sym = pair.symbol;
    const star = document.createElement("i");
    star.className = "star";
    star.textContent = "★";
    const ident = document.createElement("i");
    ident.className = "ident";
    ident.style.background = `linear-gradient(135deg, hsl(${pair.hue} 72% 62%), hsl(${(pair.hue + 36) % 360} 64% 40%))`;
    const sym = document.createElement("span");
    sym.className = "sym";
    const names = document.createElement("span");
    names.className = "names";
    const b = document.createElement("b");
    b.textContent = pair.symbol;
    const small = document.createElement("small");
    small.textContent = pair.name;
    names.append(b, small);
    sym.append(names);
    const px = document.createElement("span");
    px.className = "num";
    px.dataset.k = "px";
    const ch = document.createElement("span");
    ch.className = "num";
    ch.dataset.k = "ch";
    const quote = document.createElement("span");
    quote.className = "quote";
    quote.append(px, ch);
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "spark");
    svg.setAttribute("viewBox", "0 0 54 20");
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("fill", "none");
    path.setAttribute("stroke-width", "1.4");
    svg.append(path);
    sym.append(svg);
    row.append(star, ident, sym, quote);
    star.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleFav(pair.symbol);
    });
    row.addEventListener("click", () => select(pair.symbol));
    list.append(row);
    rows.set(pair.symbol, row);
  }
}

function buildTape() {
  const track = $("tape-track");
  for (let copy = 0; copy < 2; copy++) {
    const group = document.createElement("div");
    group.className = "tape-group";
    for (const pair of market.list()) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "tick";
      btn.dataset.sym = pair.symbol;
      const b = document.createElement("b");
      b.textContent = pair.symbol;
      const px = document.createElement("i");
      px.className = "num";
      px.dataset.k = "px";
      const ch = document.createElement("em");
      ch.className = "num";
      ch.dataset.k = "ch";
      btn.append(b, px, ch);
      btn.addEventListener("click", () => select(pair.symbol));
      group.append(btn);
    }
    track.append(group);
  }
}

function buildBook() {
  for (const id of ["asks", "bids"]) {
    const host = $(id);
    for (let i = 0; i < 14; i++) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = `lvl ${id === "asks" ? "ask" : "bid"}`;
      const bar = document.createElement("i");
      const px = document.createElement("span");
      px.className = "px";
      const q = document.createElement("span");
      const tot = document.createElement("span");
      btn.append(bar, px, q, tot);
      btn.addEventListener("click", () => {
        const price = Number(btn.dataset.price);
        if (!(price > 0)) return;
        orderType = "limit";
        syncTicket(false);
        $("price-input").value = price.toFixed(market.get(active).digits);
      });
      host.append(btn);
    }
  }
}

function buildTimeframes() {
  const host = $("tfs");
  for (const frame of TIMEFRAMES) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = frame.label;
    btn.dataset.tf = frame.id;
    if (frame.id === tf) btn.classList.add("on");
    btn.addEventListener("click", () => {
      tf = frame.id;
      host.querySelectorAll("button").forEach((el) => el.classList.toggle("on", el.dataset.tf === tf));
      chart.pin();
      chart.clearHover();
    });
    host.append(btn);
  }
}

function paintRow(row, pair, withSpark) {
  const chg = pair.price / pair.open24 - 1;
  const px = row.querySelector('[data-k="px"]');
  const ch = row.querySelector('[data-k="ch"]');
  px.textContent = fmtPrice(pair.price, pair.digits);
  ch.textContent = fmtPct(chg);
  px.classList.toggle("pos", chg >= 0);
  px.classList.toggle("neg", chg < 0);
  ch.classList.toggle("pos", chg >= 0);
  ch.classList.toggle("neg", chg < 0);
  row.querySelector(".star").classList.toggle("on", state.favorites.includes(pair.symbol));
  if (withSpark) {
    const path = row.querySelector("path");
    path.setAttribute("d", sparkPath(pair.spark));
    path.setAttribute("stroke", chg >= 0 ? "#1ad598" : "#ff5d73");
  }
}

function sortRows() {
  const query = $("q").value.trim().toLowerCase();
  const favOnly = $("only-fav").classList.contains("on");
  const mode = $("sort").value;
  const items = market.list().slice().sort((a, b) => {
    const fa = state.favorites.includes(a.symbol) ? 0 : 1;
    const fb = state.favorites.includes(b.symbol) ? 0 : 1;
    if (fa !== fb) return fa - fb;
    if (mode === "name") return a.symbol.localeCompare(b.symbol);
    if (mode === "chg") return b.price / b.open24 - a.price / a.open24;
    return b.volume24 - a.volume24;
  });
  const list = $("mlist");
  let shown = 0;
  for (const pair of items) {
    const row = rows.get(pair.symbol);
    const ok =
      (!query || pair.symbol.toLowerCase().includes(query) || pair.name.toLowerCase().includes(query)) &&
      (!favOnly || state.favorites.includes(pair.symbol));
    row.hidden = !ok;
    if (ok) shown += 1;
    list.append(row);
    paintRow(row, pair, true);
  }
  $("m-empty").hidden = shown !== 0;
}

function toggleFav(sym) {
  if (state.favorites.includes(sym)) state.favorites = state.favorites.filter((s) => s !== sym);
  else state.favorites.push(sym);
  save();
  sortRows();
}

function select(sym) {
  if (!market.get(sym)) return;
  active = sym;
  sessionStorage.setItem("velora-sym", sym);
  for (const [name, row] of rows) row.classList.toggle("on", name === sym);
  chart.pin();
  chart.clearHover();
  $("qty-input").value = "";
  lastTradeKey = "";
  syncTicket(true);
  paintPair();
  dirtyBlotter = true;
  if (window.innerWidth <= 1100) closeMarkets();
}

function syncTicket(forcePrice) {
  const pair = market.get(active);
  const price = $("price-input");
  price.readOnly = orderType === "market";
  price.classList.toggle("ro", orderType === "market");
  if (orderType === "market" || forcePrice) {
    const px = orderSide === "buy" ? pair.ask : pair.bid;
    price.value = px.toFixed(pair.digits);
  }
  $("qty-unit").textContent = pair.symbol;
  $("side-buy").classList.toggle("on", orderSide === "buy");
  $("side-sell").classList.toggle("on", orderSide === "sell");
  $("type-market").classList.toggle("on", orderType === "market");
  $("type-limit").classList.toggle("on", orderType === "limit");
  const submit = $("submit");
  submit.textContent = `${orderSide === "buy" ? "Купить" : "Продать"} ${pair.symbol}`;
  submit.classList.toggle("buy", orderSide === "buy");
  submit.classList.toggle("sell", orderSide === "sell");
}

function paintPair() {
  const pair = market.get(active);
  const chg = pair.price / pair.open24 - 1;
  $("pair-sym").textContent = `${pair.symbol}/USDT`;
  $("pair-full").textContent = pair.name;
  $("pair-ident").style.background = `linear-gradient(135deg, hsl(${pair.hue} 72% 62%), hsl(${(pair.hue + 36) % 360} 64% 40%))`;
  const px = $("pair-px");
  px.textContent = fmtPrice(pair.price, pair.digits);
  px.classList.toggle("pos", chg >= 0);
  px.classList.toggle("neg", chg < 0);
  const ch = $("pair-chg");
  ch.textContent = fmtPct(chg);
  ch.classList.toggle("pos", chg >= 0);
  ch.classList.toggle("neg", chg < 0);
  $("st-high").textContent = fmtPrice(pair.high24, pair.digits);
  $("st-low").textContent = fmtPrice(pair.low24, pair.digits);
  $("st-vol").textContent = fmtCompact(pair.volume24);
  $("adm-shock-label").textContent = `Импульс ${pair.symbol}`;
  document.title = `${fmtPrice(pair.price, pair.digits)} ${pair.symbol} · VELORA`;
}

function paintTape() {
  for (const el of $("tape-track").querySelectorAll(".tick")) {
    const pair = market.get(el.dataset.sym);
    const chg = pair.price / pair.open24 - 1;
    el.querySelector('[data-k="px"]').textContent = fmtPrice(pair.price, pair.digits);
    const ch = el.querySelector('[data-k="ch"]');
    ch.textContent = fmtPct(chg);
    ch.classList.toggle("pos", chg >= 0);
    ch.classList.toggle("neg", chg < 0);
  }
}

function paintMarkets() {
  for (const pair of market.list()) paintRow(rows.get(pair.symbol), pair, false);
}

function paintBook() {
  const pair = market.get(active);
  const levels = [
    ...accumulate(pair.book.asks).reverse(),
    ...accumulate(pair.book.bids),
  ];
  const max = Math.max(...levels.map((lvl) => lvl.cum), 1);
  paintSide($("asks"), accumulate(pair.book.asks).reverse(), pair, max);
  paintSide($("bids"), accumulate(pair.book.bids), pair, max);
  const last = pair.trades[0];
  const spread = $("spread-px");
  spread.textContent = fmtPrice(pair.price, pair.digits);
  spread.classList.toggle("pos", last ? last.side === "buy" : pair.up);
  spread.classList.toggle("neg", last ? last.side !== "buy" : !pair.up);
  const bps = ((pair.ask - pair.bid) / pair.price) * 10000;
  $("spread-bps").textContent = `${bps.toFixed(2)} б.п.`;
}

function accumulate(levels) {
  let cum = 0;
  return levels.map(([price, qty]) => {
    cum += qty;
    return { price, qty, cum };
  });
}

function paintSide(host, levels, pair, max) {
  const buttons = host.children;
  for (let i = 0; i < buttons.length; i++) {
    const lvl = levels[i];
    const btn = buttons[i];
    if (!lvl) {
      btn.hidden = true;
      continue;
    }
    btn.hidden = false;
    btn.dataset.price = String(lvl.price);
    btn.querySelector("i").style.setProperty("--w", `${Math.max(6, (lvl.cum / max) * 100)}%`);
    btn.children[1].textContent = fmtPrice(lvl.price, pair.digits);
    btn.children[2].textContent = fmtQty(lvl.qty);
    btn.children[3].textContent = fmtQty(lvl.cum);
  }
}

function paintTrades() {
  const pair = market.get(active);
  const key = pair.trades[0] ? `${pair.trades[0].ts}:${pair.trades[0].price}` : "";
  if (key === lastTradeKey) return;
  lastTradeKey = key;
  const list = $("trades");
  list.replaceChildren();
  for (const trade of pair.trades.slice(0, 18)) {
    const li = document.createElement("li");
    const t = document.createElement("span");
    t.textContent = fmtTradeTime(trade.ts);
    t.className = "dim";
    const px = document.createElement("span");
    px.textContent = fmtPrice(trade.price, pair.digits);
    px.className = trade.side === "buy" ? "pos" : "neg";
    const q = document.createElement("span");
    q.textContent = fmtQty(trade.qty);
    li.append(t, px, q);
    list.append(li);
  }
}

function paintTicket() {
  if (orderType === "market" && document.activeElement !== $("price-input")) {
    const pair = market.get(active);
    $("price-input").value = (orderSide === "buy" ? pair.ask : pair.bid).toFixed(pair.digits);
  }
  const pair = market.get(active);
  const px = parseNum($("price-input").value);
  const qty = parseNum($("qty-input").value);
  const notional = px > 0 && qty > 0 ? px * qty : 0;
  $("total").textContent = `${fmtNum(notional, 2)} USDT`;
  $("fee-line").textContent = `${fmtNum(notional * feeRate(), 4)} USDT`;
  if (orderSide === "buy") $("avail").textContent = `${fmtNum(Math.max(0, availableUSDT()), 2)} USDT`;
  else $("avail").textContent = `${fmtQty(Math.max(0, availableBase(active)))} ${pair.symbol}`;
  $("bid-px").textContent = fmtPrice(pair.bid, pair.digits);
  $("ask-px").textContent = fmtPrice(pair.ask, pair.digits);
}

function paintHeader() {
  const eq = equity();
  const pnl = eq - sessionBase;
  $("hdr-eq").textContent = fmtNum(eq, 2);
  const pnlEl = $("hdr-pnl");
  pnlEl.textContent = fmtPct(sessionBase ? pnl / sessionBase : 0);
  pnlEl.classList.toggle("pos", pnl >= 0);
  pnlEl.classList.toggle("neg", pnl < 0);
  $("hdr-clock").textContent = fmtClock();
  if (document.activeElement !== $("adm-usdt")) $("adm-usdt").value = state.usdt.toFixed(2);
  $("live-ind").classList.toggle("paused", market.paused);
  $("live-label").textContent = market.paused ? "пауза" : "поток";
  $("adm-status").textContent = `${market.paused ? "пауза" : "поток"} · время ${market.timeScale.toFixed(0)}× · ${fmtClock(market.simNow)}`;
}

function paintOhlc() {
  const candle = chart.legend();
  const pair = market.get(active);
  if (!candle) return;
  $("o-o").textContent = fmtPrice(candle.o, pair.digits);
  $("o-h").textContent = fmtPrice(candle.h, pair.digits);
  $("o-l").textContent = fmtPrice(candle.l, pair.digits);
  $("o-c").textContent = fmtPrice(candle.c, pair.digits);
  const chg = candle.o ? (candle.c - candle.o) / candle.o : 0;
  const node = $("o-ch");
  node.textContent = fmtPct(chg);
  node.classList.toggle("pos", chg >= 0);
  node.classList.toggle("neg", chg < 0);
}

function renderBlotter() {
  const host = $("blotter-scroll");
  const top = host.scrollTop;
  const pairName = (sym) => esc(sym);
  if (blotterTab === "pos") {
    const lines = Object.entries(state.positions).filter(([, pos]) => pos.qty > 0);
    host.innerHTML = `<table><thead><tr><th>Актив</th><th>Кол-во</th><th>Средняя</th><th>Цена</th><th>Стоимость</th><th>PnL</th><th></th></tr></thead><tbody>${
      lines.length
        ? lines
            .map(([sym, pos]) => {
              const pair = market.get(sym);
              const value = pos.qty * pair.price;
              const pnl = (pair.price - pos.avg) * pos.qty;
              return `<tr><td>${pairName(sym)}</td><td class="num">${fmtQty(pos.qty)}</td><td class="num">${fmtPrice(pos.avg, pair.digits)}</td><td class="num">${fmtPrice(pair.price, pair.digits)}</td><td class="num">${fmtNum(value, 2)}</td><td class="num ${pnl >= 0 ? "pos" : "neg"}">${fmtNum(pnl, 2)}</td><td><button class="linkish" type="button" data-close="${esc(sym)}">Закрыть</button></td></tr>`;
            })
            .join("")
        : `<tr><td colspan="7">Нет монет. Заявка справа покупает с бумажного USDT.</td></tr>`
    }</tbody></table>`;
  } else if (blotterTab === "ord") {
    host.innerHTML = `<table><thead><tr><th>Время</th><th>Пара</th><th>Сторона</th><th>Тип</th><th>Цена</th><th>Кол-во</th><th></th></tr></thead><tbody>${
      state.orders.length
        ? state.orders
            .map((order) => {
              const pair = market.get(order.symbol);
              return `<tr><td class="num">${fmtTradeTime(order.ts)}</td><td>${pairName(order.symbol)}</td><td class="${order.side === "buy" ? "pos" : "neg"}">${order.side === "buy" ? "Купля" : "Продажа"}</td><td>${order.type === "market" ? "Рынок" : "Лимит"}</td><td class="num">${fmtPrice(order.price, pair.digits)}</td><td class="num">${fmtQty(order.qty)}</td><td><button class="linkish" type="button" data-cancel="${esc(order.id)}">Снять</button></td></tr>`;
            })
            .join("")
        : `<tr><td colspan="7">Нет открытых ордеров.</td></tr>`
    }</tbody></table>`;
  } else {
    host.innerHTML = `<table><thead><tr><th>Время</th><th>Пара</th><th>Сторона</th><th>Цена</th><th>Кол-во</th><th>Сумма</th><th>Комиссия</th></tr></thead><tbody>${
      state.fills.length
        ? state.fills
            .map((fillRow) => {
              const pair = market.get(fillRow.symbol);
              const digits = pair ? pair.digits : 2;
              return `<tr><td class="num">${fmtTradeTime(fillRow.ts)}</td><td>${pairName(fillRow.symbol)}</td><td class="${fillRow.side === "buy" ? "pos" : "neg"}">${fillRow.side === "buy" ? "Купля" : "Продажа"}</td><td class="num">${fmtPrice(fillRow.price, digits)}</td><td class="num">${fmtQty(fillRow.qty)}</td><td class="num">${fmtNum(fillRow.notional, 2)}</td><td class="num">${fmtNum(fillRow.fee, 4)}</td></tr>`;
            })
            .join("")
        : `<tr><td colspan="7">Сделок ещё не было.</td></tr>`
    }</tbody></table>`;
  }
  host.scrollTop = top;
  $("ord-count").textContent = state.orders.length ? String(state.orders.length) : "";
}

function unrealized() {
  let pnl = 0;
  for (const [sym, pos] of Object.entries(state.positions)) {
    const pair = market.get(sym);
    if (pair) pnl += (pair.price - pos.avg) * pos.qty;
  }
  return pnl;
}

function renderPortfolio() {
  const eq = equity();
  const pnl = eq - sessionBase;
  $("eq").textContent = `${fmtNum(eq, 2)} USDT`;
  const pnlEl = $("eq-pnl");
  pnlEl.textContent = `${pnl >= 0 ? "+" : ""}${fmtNum(pnl, 2)}  ${fmtPct(sessionBase ? pnl / sessionBase : 0)} за сессию`;
  pnlEl.classList.toggle("pos", pnl >= 0);
  pnlEl.classList.toggle("neg", pnl < 0);
  const u = unrealized();
  $("eq-real").textContent = `Реализовано ${fmtNum(state.realized, 2)} · открыто ${fmtNum(u, 2)} · комиссии ${fmtNum(state.fees, 2)}`;
  const rows = [{ name: "USDT", qty: `${fmtNum(state.usdt, 2)}`, value: state.usdt, pnl: null }];
  for (const [sym, pos] of Object.entries(state.positions)) {
    if (pos.qty <= 0) continue;
    const pair = market.get(sym);
    rows.push({
      name: sym,
      sym,
      qty: fmtQty(pos.qty),
      value: pos.qty * pair.price,
      pnl: (pair.price - pos.avg) * pos.qty,
    });
  }
  let nftValue = 0;
  for (const id of Object.keys(state.owned)) {
    const nft = NFT_BY_ID[id];
    if (nft) nftValue += nft.price * floor * 0.97;
  }
  if (nftValue > 0) rows.push({ name: "NFT", qty: String(Object.keys(state.owned).length), value: nftValue, pnl: null });
  const host = $("holdings");
  host.replaceChildren();
  for (const row of rows) {
    const el = document.createElement("div");
    el.className = "hold";
    const name = document.createElement("b");
    name.textContent = row.name;
    const bar = document.createElement("div");
    bar.className = "bar";
    const fillBar = document.createElement("i");
    fillBar.style.width = `${eq > 0 ? Math.max(2, (row.value / eq) * 100) : 0}%`;
    bar.append(fillBar);
    const qty = document.createElement("span");
    qty.className = "num dim";
    qty.textContent = row.qty;
    const val = document.createElement("span");
    val.className = "num";
    val.textContent = fmtNum(row.value, 2);
    if (row.pnl != null) {
      val.classList.add(row.pnl >= 0 ? "pos" : "neg");
      val.textContent = `${fmtNum(row.value, 2)}  ${row.pnl >= 0 ? "+" : ""}${fmtNum(row.pnl, 2)}`;
    }
    el.append(name, bar, qty, val);
    if (row.sym) {
      const close = document.createElement("button");
      close.type = "button";
      close.className = "linkish";
      close.textContent = "Закрыть";
      close.dataset.close = row.sym;
      el.append(close);
    }
    host.append(el);
  }
  const owned = $("owned-nfts");
  owned.replaceChildren();
  const ids = Object.keys(state.owned);
  if (!ids.length) {
    const p = document.createElement("p");
    p.className = "dim";
    p.textContent = "Пока пусто. Работы лежат в маркете NFT.";
    owned.append(p);
  }
  for (const id of ids) {
    const nft = NFT_BY_ID[id];
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "mini-nft";
    const art = document.createElement("span");
    art.innerHTML = nftSvg(nft);
    const label = document.createElement("span");
    label.textContent = `${nft.name} · ${fmtNum(nft.price * floor * 0.97, 0)} USDT`;
    btn.append(art, label);
    btn.addEventListener("click", () => openModal(id));
    owned.append(btn);
  }
  drawEquity();
}

function drawEquity() {
  const canvas = $("eq-chart");
  const ctx = canvas.getContext("2d");
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (w < 8 || h < 8) return;
  canvas.width = Math.floor(w * dpr);
  canvas.height = Math.floor(h * dpr);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  if (eqSeries.length < 2) return;
  const vals = eqSeries.map((p) => p.v);
  let min = Math.min(...vals);
  let max = Math.max(...vals);
  if (max - min < 1) {
    min -= 1;
    max += 1;
  }
  const up = vals[vals.length - 1] >= vals[0];
  const xy = vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * (w - 8) + 4;
    const y = h - 10 - ((v - min) / (max - min)) * (h - 20);
    return [x, y];
  });
  ctx.beginPath();
  xy.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.strokeStyle = up ? "#1ad598" : "#ff5d73";
  ctx.lineWidth = 1.7;
  ctx.stroke();
  ctx.lineTo(xy[xy.length - 1][0], h);
  ctx.lineTo(xy[0][0], h);
  ctx.closePath();
  const grad = ctx.createLinearGradient(0, 0, 0, h);
  grad.addColorStop(0, up ? "rgba(26,213,152,0.28)" : "rgba(255,93,115,0.28)");
  grad.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = grad;
  ctx.fill();
}

function renderNfts() {
  const host = $("nft-grid");
  host.replaceChildren();
  const list = NFTS.filter((nft) => {
    if (nftFilter === "mine") return Boolean(state.owned[nft.id]);
    if (nftFilter === "all") return true;
    return nft.collection === nftFilter;
  });
  const floorPx = Math.min(...NFTS.map((nft) => nft.price)) * floor;
  $("nft-floor").textContent = `${fmtNum(floorPx, 0)} USDT`;
  if (!list.length) {
    const p = document.createElement("p");
    p.className = "empty";
    p.textContent = "В этой полке пусто.";
    host.append(p);
    return;
  }
  for (const nft of list) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "nft-card";
    const art = document.createElement("div");
    art.className = "art";
    art.innerHTML = nftSvg(nft);
    const meta = document.createElement("div");
    meta.className = "nft-meta";
    const rarity = document.createElement("span");
    rarity.className = `rarity ${nft.rarity}`;
    rarity.textContent = rarityLabel(nft.rarity);
    const title = document.createElement("h3");
    title.textContent = nft.name;
    const col = document.createElement("span");
    col.className = "dim";
    col.textContent = collectionById(nft.collection).name;
    const price = document.createElement("b");
    price.className = "num";
    price.textContent = `${fmtNum(nft.price * floor, 0)} USDT`;
    meta.append(rarity, title, col, price);
    if (state.owned[nft.id]) {
      const flag = document.createElement("span");
      flag.className = "owned-flag";
      flag.textContent = "У вас";
      meta.append(flag);
    }
    card.append(art, meta);
    card.addEventListener("click", () => openModal(nft.id));
    host.append(card);
  }
}

function buildChips() {
  const host = $("nft-chips");
  const chips = [{ id: "all", name: "Все" }, ...COLLECTION_LIST.map((c) => ({ id: c.id, name: c.name })), { id: "mine", name: "Мои" }];
  for (const chip of chips) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "chip";
    btn.textContent = chip.name;
    btn.dataset.filter = chip.id;
    if (chip.id === nftFilter) btn.classList.add("on");
    btn.addEventListener("click", () => {
      nftFilter = chip.id;
      host.querySelectorAll(".chip").forEach((el) => el.classList.toggle("on", el.dataset.filter === nftFilter));
      renderNfts();
    });
    host.append(btn);
  }
}

function openModal(id) {
  modalId = id;
  modalPainted = null;
  $("modal").hidden = false;
  paintModal();
}
function closeModal() {
  $("modal").hidden = true;
  modalId = null;
}
function paintModal() {
  if ($("modal").hidden || !modalId) return;
  const nft = NFT_BY_ID[modalId];
  if (!nft) return;
  if (modalPainted !== nft.id) {
    $("modal-art").innerHTML = nftSvg(nft);
    $("m-rarity").textContent = rarityLabel(nft.rarity);
    $("m-rarity").className = `rarity ${nft.rarity}`;
    $("m-name").textContent = nft.name;
    const col = collectionById(nft.collection);
    $("m-col").textContent = `${col.name} · ${col.artist}`;
    const traits = $("m-traits");
    traits.replaceChildren();
    for (const [k, v] of [
      ["Форма", nft.form],
      ["Сигнал", nft.signal],
      ["Тираж", String(nft.edition)],
      ["Коллекция", col.blurb],
    ]) {
      const wrap = document.createElement("div");
      const dt = document.createElement("dt");
      dt.textContent = k;
      const dd = document.createElement("dd");
      dd.textContent = v;
      wrap.append(dt, dd);
      traits.append(wrap);
    }
    modalPainted = nft.id;
  }
  const owned = state.owned[nft.id];
  const ask = nft.price * floor;
  const bid = ask * 0.97;
  $("m-price").textContent = `${fmtNum(owned ? bid : ask, 2)} USDT`;
  $("m-sub").textContent = owned ? "мгновенный выкуп деска" : "спишется с бумажного USDT";
  const action = $("m-action");
  if (owned) {
    action.textContent = "Продать";
    action.disabled = false;
    action.className = "submit sell";
  } else if (availableUSDT() < ask) {
    action.textContent = "Не хватает USDT";
    action.disabled = true;
    action.className = "submit buy";
  } else {
    action.textContent = "Купить";
    action.disabled = false;
    action.className = "submit buy";
  }
}

function buyOrSellNft() {
  const nft = NFT_BY_ID[modalId];
  if (!nft) return;
  if (state.owned[nft.id]) {
    const proceeds = nft.price * floor * 0.97;
    state.realized += proceeds - state.owned[nft.id].buy;
    state.usdt += proceeds;
    delete state.owned[nft.id];
    toast("NFT продан деску");
  } else {
    const cost = nft.price * floor;
    if (availableUSDT() < cost) return toast("Недостаточно свободных USDT");
    state.usdt -= cost;
    state.owned[nft.id] = { buy: cost, ts: Date.now() };
    toast("Работа в коллекции");
  }
  save();
  dirtyBlotter = true;
  renderNfts();
  paintModal();
  if (document.body.dataset.view === "portfolio") renderPortfolio();
}

function setView(name) {
  document.body.dataset.view = name;
  document.querySelectorAll(".nav [data-view]").forEach((btn) => btn.classList.toggle("on", btn.dataset.view === name));
  if (name === "trade") {
    dirtyBlotter = true;
    requestAnimationFrame(() => chart.resize());
  }
  if (name === "nft") renderNfts();
  if (name === "portfolio") renderPortfolio();
}

function openMarkets() {
  $("markets").classList.add("show");
  $("markets-backdrop").hidden = false;
}
function closeMarkets() {
  $("markets").classList.remove("show");
  $("markets-backdrop").hidden = true;
}

function applyPct(pct) {
  const pair = market.get(active);
  const px = parseNum($("price-input").value);
  if (!(px > 0)) return;
  let qty;
  if (orderSide === "buy") qty = (Math.max(0, availableUSDT()) * pct) / (px * (1 + feeRate()));
  else qty = Math.max(0, availableBase(active)) * pct;
  const digits = pair.price >= 100 ? 6 : pair.price >= 1 ? 4 : pair.price >= 0.01 ? 2 : 0;
  $("qty-input").value = qty > 0 ? qty.toFixed(digits) : "";
}

function readAdminPos() {
  try {
    const pos = JSON.parse(sessionStorage.getItem(POS_KEY) || "null");
    if (!pos || !Number.isFinite(pos.x) || !Number.isFinite(pos.y)) return null;
    return pos;
  } catch {
    return null;
  }
}

let adminOpen = false;
function positionAdmin(pos) {
  const panel = $("admin");
  const w = panel.offsetWidth || 318;
  const h = panel.offsetHeight || 420;
  const x = clamp(pos ? pos.x : window.innerWidth - w - 16, 8, Math.max(8, window.innerWidth - w - 8));
  const y = clamp(pos ? pos.y : 68, 8, Math.max(8, window.innerHeight - Math.min(h, window.innerHeight - 16) - 8));
  panel.style.left = `${x}px`;
  panel.style.top = `${y}px`;
  panel.style.right = "auto";
}
function openAdmin() {
  const panel = $("admin");
  panel.hidden = false;
  positionAdmin(readAdminPos());
  $("admin-toggle").setAttribute("aria-pressed", "true");
  requestAnimationFrame(() => {
    panel.classList.add("is-open");
    adminOpen = true;
  });
}
function closeAdmin() {
  const panel = $("admin");
  panel.classList.remove("is-open");
  $("admin-toggle").setAttribute("aria-pressed", "false");
  adminOpen = false;
  const hide = () => {
    if (!panel.classList.contains("is-open")) panel.hidden = true;
  };
  panel.addEventListener("transitionend", hide, { once: true });
  setTimeout(hide, 520);
}

function bindAdminDrag() {
  const panel = $("admin");
  const bar = $("admin-bar");
  let drag = null;
  bar.addEventListener("pointerdown", (event) => {
    if (event.target.closest("button")) return;
    const rect = panel.getBoundingClientRect();
    drag = { dx: event.clientX - rect.left, dy: event.clientY - rect.top };
    panel.classList.add("dragging");
    bar.setPointerCapture(event.pointerId);
  });
  bar.addEventListener("pointermove", (event) => {
    if (!drag) return;
    const w = panel.offsetWidth;
    const h = panel.offsetHeight;
    const x = clamp(event.clientX - drag.dx, 8, Math.max(8, window.innerWidth - w - 8));
    const y = clamp(event.clientY - drag.dy, 8, Math.max(8, window.innerHeight - h - 8));
    panel.style.left = `${x}px`;
    panel.style.top = `${y}px`;
  });
  const end = () => {
    if (!drag) return;
    drag = null;
    panel.classList.remove("dragging");
    sessionStorage.setItem(POS_KEY, JSON.stringify({ x: parseFloat(panel.style.left), y: parseFloat(panel.style.top) }));
  };
  bar.addEventListener("pointerup", end);
  bar.addEventListener("pointercancel", end);
}

function syncAdminInputs() {
  $("adm-usdt").value = String(Math.round(state.usdt));
  $("adm-vol").value = "1";
  $("adm-speed").value = "1";
  $("adm-fee").value = String(feeBps);
  $("adm-floor").value = String(floor);
  $("adm-fee-v").textContent = `${feeBps.toFixed(1)} б.п.`;
  $("adm-floor-v").textContent = `${floor.toFixed(2)}×`;
  $("adm-audio").checked = audioOn;
}

function resetAccount() {
  const button = $("adm-reset");
  if (!resetArmed) {
    resetArmed = true;
    button.textContent = "Ещё раз — сбросить";
    setTimeout(() => {
      resetArmed = false;
      button.textContent = "Сбросить счёт";
    }, 2800);
    return;
  }
  resetArmed = false;
  button.textContent = "Сбросить счёт";
  state = freshState();
  feeBps = 4;
  floor = 1;
  sessionBase = equity();
  eqSeries = [
    { t: Date.now(), v: sessionBase },
    { t: Date.now(), v: sessionBase },
  ];
  syncAdminInputs();
  save();
  dirtyBlotter = true;
  renderNfts();
  toast("Счёт возвращён к стартовому");
}

function wire() {
  document.body.addEventListener("click", (event) => {
    const view = event.target.closest("[data-view]");
    if (view) setView(view.dataset.view);
    if (event.target.closest("[data-close='1']") || event.target.id === "modal-close") closeModal();
  });
  $("blotter-tabs").addEventListener("click", (event) => {
    const btn = event.target.closest("[data-tab]");
    if (!btn) return;
    blotterTab = btn.dataset.tab;
    $("blotter-tabs").querySelectorAll("button").forEach((el) => el.classList.toggle("on", el === btn));
    dirtyBlotter = true;
    renderBlotter();
  });
  $("blotter-scroll").addEventListener("click", (event) => {
    const cancel = event.target.closest("[data-cancel]");
    if (cancel) {
      removeOrder(cancel.dataset.cancel);
      dirtyBlotter = true;
      save();
      toast("Ордер снят");
      renderBlotter();
    }
    const close = event.target.closest("[data-close]");
    if (close && close.dataset.close !== "1") closePosition(close.dataset.close);
  });
  $("holdings").addEventListener("click", (event) => {
    const close = event.target.closest("[data-close]");
    if (close) closePosition(close.dataset.close);
  });
  $("side-buy").addEventListener("click", () => {
    orderSide = "buy";
    syncTicket(true);
  });
  $("side-sell").addEventListener("click", () => {
    orderSide = "sell";
    syncTicket(true);
  });
  $("type-market").addEventListener("click", () => {
    orderType = "market";
    syncTicket(true);
  });
  $("type-limit").addEventListener("click", () => {
    orderType = "limit";
    syncTicket(false);
  });
  $("bid-btn").addEventListener("click", () => {
    orderType = "limit";
    orderSide = "buy";
    syncTicket(true);
    $("price-input").value = market.get(active).bid.toFixed(market.get(active).digits);
  });
  $("ask-btn").addEventListener("click", () => {
    orderType = "limit";
    orderSide = "sell";
    syncTicket(true);
    $("price-input").value = market.get(active).ask.toFixed(market.get(active).digits);
  });
  $("pcts").addEventListener("click", (event) => {
    const btn = event.target.closest("[data-pct]");
    if (btn) applyPct(Number(btn.dataset.pct));
  });
  $("submit").addEventListener("click", place);
  $("m-action").addEventListener("click", buyOrSellNft);
  $("markets-btn").addEventListener("click", openMarkets);
  $("markets-close").addEventListener("click", closeMarkets);
  $("markets-backdrop").addEventListener("click", closeMarkets);
  $("q").addEventListener("input", sortRows);
  $("sort").addEventListener("change", sortRows);
  $("only-fav").addEventListener("click", () => {
    $("only-fav").classList.toggle("on");
    sortRows();
  });
  $("toggle-ema").addEventListener("click", () => {
    emaOn = !emaOn;
    $("toggle-ema").classList.toggle("on", emaOn);
  });
  $("toggle-vol").addEventListener("click", () => {
    volOn = !volOn;
    $("toggle-vol").classList.toggle("on", volOn);
  });
  $("pin-btn").addEventListener("click", () => chart.pin());
  $("dock-tabs").addEventListener("click", (event) => {
    const btn = event.target.closest("[data-dock]");
    if (!btn) return;
    $("dock").dataset.tab = btn.dataset.dock;
    $("dock-tabs").querySelectorAll("button").forEach((el) => el.classList.toggle("on", el === btn));
  });
  $("admin-toggle").addEventListener("click", () => (adminOpen ? closeAdmin() : openAdmin()));
  $("admin-close").addEventListener("click", closeAdmin);
  $("adm-usdt-apply").addEventListener("click", () => {
    const value = parseNum($("adm-usdt").value);
    if (!(value >= 0)) return;
    state.usdt = value;
    save();
    toast("Баланс USDT обновлён");
  });
  $("adm-vol").addEventListener("input", () => {
    const value = Number($("adm-vol").value);
    market.setVol(value);
    $("adm-vol-v").textContent = `${value.toFixed(1)}×`;
  });
  $("adm-speed").addEventListener("input", () => {
    const value = Number($("adm-speed").value);
    market.setTimeScale(value);
    $("adm-speed-v").textContent = `${value.toFixed(0)}×`;
  });
  $("adm-fee").addEventListener("input", () => {
    feeBps = Number($("adm-fee").value);
    $("adm-fee-v").textContent = `${feeBps.toFixed(1)} б.п.`;
    save();
  });
  $("adm-floor").addEventListener("input", () => {
    floor = Number($("adm-floor").value);
    $("adm-floor-v").textContent = `${floor.toFixed(2)}×`;
    save();
    if (document.body.dataset.view === "nft") renderNfts();
    paintModal();
  });
  $("adm-audio").addEventListener("change", () => {
    audioOn = $("adm-audio").checked;
    save();
  });
  $("adm-pause").addEventListener("click", () => {
    const paused = market.togglePause();
    $("adm-pause").textContent = paused ? "Продолжить" : "Пауза";
  });
  $("adm-dump").addEventListener("click", () => {
    market.shock(active, -0.08);
    toast(`${active} −8%`);
  });
  $("adm-pump").addEventListener("click", () => {
    market.shock(active, 0.08);
    toast(`${active} +8%`);
  });
  $("adm-reset").addEventListener("click", resetAccount);
  window.addEventListener("resize", () => {
    chart.resize();
    if (!$("admin").hidden) positionAdmin({ x: parseFloat($("admin").style.left), y: parseFloat($("admin").style.top) });
    if (document.body.dataset.view === "portfolio") drawEquity();
  });
  document.addEventListener("keydown", (event) => {
    const tag = document.activeElement?.tagName;
    if (event.key === "Escape") {
      if (!$("modal").hidden) closeModal();
      else if ($("markets").classList.contains("show")) closeMarkets();
      else if (adminOpen) closeAdmin();
    }
    if (event.key === "/" && tag !== "INPUT" && tag !== "SELECT" && tag !== "TEXTAREA") {
      event.preventDefault();
      setView("trade");
      $("q").focus();
    }
  });
  document.querySelectorAll('input[type="number"]').forEach((input) => {
    input.addEventListener("wheel", () => input.blur(), { passive: true });
  });
  window.addEventListener("beforeunload", writeState);
  new ResizeObserver(() => chart.resize()).observe($("chart").parentElement);
}

function boot() {
  buildMarkets();
  buildTape();
  buildBook();
  buildTimeframes();
  buildChips();
  bindAdminDrag();
  syncAdminInputs();
  wire();
  sessionBase = equity();
  eqSeries = [
    { t: Date.now(), v: sessionBase },
    { t: Date.now(), v: sessionBase },
  ];
  sortRows();
  select(active);
  renderNfts();
  let prev = 0;
  let acc = 0;
  let lastDom = 0;
  let lastSort = 0;
  let lastBlotter = 0;
  let lastEq = 0;
  const loop = (now) => {
    if (!prev) prev = now;
    acc += now - prev;
    prev = now;
    const speed = clamp(market.timeScale, 1, 8);
    if (acc >= 400 / speed) {
      acc = 0;
      market.tick(now);
      matchOrders();
    }
    const pair = market.get(active);
    chart.state.candles = pair.candles[tf];
    chart.state.digits = pair.digits;
    chart.state.tf = tf;
    chart.state.last = pair.price;
    chart.state.up = pair.price >= pair.open24;
    chart.state.showEma = emaOn;
    chart.state.showVol = volOn;
    chart.state.orders = state.orders.filter((order) => order.symbol === active);
    if (document.body.dataset.view === "trade") chart.draw();
    if (now - lastDom > 150) {
      lastDom = now;
      paintHeader();
      paintTape();
      paintMarkets();
      if (document.body.dataset.view === "trade") {
        paintPair();
        paintBook();
        paintTrades();
        paintTicket();
        paintOhlc();
      }
      if (!$("modal").hidden) paintModal();
    }
    if (now - lastSort > 1800) {
      lastSort = now;
      if (document.body.dataset.view === "trade") sortRows();
    }
    if (dirtyBlotter || now - lastBlotter > 900) {
      if (document.body.dataset.view === "trade") renderBlotter();
      lastBlotter = now;
      dirtyBlotter = false;
    }
    if (now - lastEq > 2000) {
      lastEq = now;
      eqSeries.push({ t: Date.now(), v: equity() });
      if (eqSeries.length > 180) eqSeries.shift();
      if (document.body.dataset.view === "portfolio") renderPortfolio();
    }
    $("pin-btn").hidden = chart.state.pinned;
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(() => {
    chart.resize();
    requestAnimationFrame(loop);
  });
}

boot();
