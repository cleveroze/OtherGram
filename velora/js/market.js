export const TIMEFRAMES = [
  { id: "1m", label: "1м", ms: 60_000 },
  { id: "5m", label: "5м", ms: 300_000 },
  { id: "15m", label: "15м", ms: 900_000 },
  { id: "1h", label: "1ч", ms: 3_600_000 },
  { id: "4h", label: "4ч", ms: 14_400_000 },
  { id: "1d", label: "1д", ms: 86_400_000 },
];

const SPECS = [
  { symbol: "BTC", name: "Bitcoin", price: 97482.35, digits: 2, dailyVol: 0.028, tickVol: 0.0001, beta: 1, dayQuoteVol: 18.4e9 },
  { symbol: "ETH", name: "Ethereum", price: 3512.48, digits: 2, dailyVol: 0.036, tickVol: 0.00014, beta: 1.12, dayQuoteVol: 8.6e9 },
  { symbol: "SOL", name: "Solana", price: 187.62, digits: 2, dailyVol: 0.048, tickVol: 0.00018, beta: 1.28, dayQuoteVol: 2.4e9 },
  { symbol: "BNB", name: "BNB", price: 614.2, digits: 2, dailyVol: 0.03, tickVol: 0.00012, beta: 0.9, dayQuoteVol: 1.1e9 },
  { symbol: "XRP", name: "XRP", price: 2.1842, digits: 4, dailyVol: 0.04, tickVol: 0.00016, beta: 1.05, dayQuoteVol: 1.8e9 },
  { symbol: "TON", name: "Toncoin", price: 5.428, digits: 3, dailyVol: 0.05, tickVol: 0.0002, beta: 1.15, dayQuoteVol: 420e6 },
  { symbol: "DOGE", name: "Dogecoin", price: 0.3842, digits: 4, dailyVol: 0.055, tickVol: 0.00022, beta: 1.2, dayQuoteVol: 900e6 },
  { symbol: "ADA", name: "Cardano", price: 0.9124, digits: 4, dailyVol: 0.045, tickVol: 0.00018, beta: 1.1, dayQuoteVol: 480e6 },
  { symbol: "AVAX", name: "Avalanche", price: 38.74, digits: 2, dailyVol: 0.05, tickVol: 0.0002, beta: 1.25, dayQuoteVol: 390e6 },
  { symbol: "TRX", name: "TRON", price: 0.1642, digits: 4, dailyVol: 0.028, tickVol: 0.00012, beta: 0.8, dayQuoteVol: 600e6 },
  { symbol: "LINK", name: "Chainlink", price: 22.186, digits: 3, dailyVol: 0.048, tickVol: 0.00019, beta: 1.18, dayQuoteVol: 520e6 },
  { symbol: "DOT", name: "Polkadot", price: 7.842, digits: 3, dailyVol: 0.046, tickVol: 0.00018, beta: 1.12, dayQuoteVol: 210e6 },
  { symbol: "BCH", name: "Bitcoin Cash", price: 482.16, digits: 2, dailyVol: 0.036, tickVol: 0.00015, beta: 1, dayQuoteVol: 210e6 },
  { symbol: "LTC", name: "Litecoin", price: 104.62, digits: 2, dailyVol: 0.034, tickVol: 0.00014, beta: 0.95, dayQuoteVol: 340e6 },
  { symbol: "NEAR", name: "NEAR", price: 6.284, digits: 3, dailyVol: 0.055, tickVol: 0.00022, beta: 1.3, dayQuoteVol: 280e6 },
  { symbol: "APT", name: "Aptos", price: 11.426, digits: 3, dailyVol: 0.058, tickVol: 0.00024, beta: 1.32, dayQuoteVol: 310e6 },
  { symbol: "SUI", name: "Sui", price: 3.8624, digits: 4, dailyVol: 0.06, tickVol: 0.00024, beta: 1.35, dayQuoteVol: 640e6 },
  { symbol: "ARB", name: "Arbitrum", price: 1.1284, digits: 4, dailyVol: 0.055, tickVol: 0.00022, beta: 1.22, dayQuoteVol: 260e6 },
  { symbol: "OP", name: "Optimism", price: 1.8642, digits: 4, dailyVol: 0.056, tickVol: 0.00022, beta: 1.24, dayQuoteVol: 190e6 },
  { symbol: "INJ", name: "Injective", price: 28.46, digits: 2, dailyVol: 0.062, tickVol: 0.00026, beta: 1.4, dayQuoteVol: 180e6 },
  { symbol: "TIA", name: "Celestia", price: 8.924, digits: 3, dailyVol: 0.065, tickVol: 0.00026, beta: 1.38, dayQuoteVol: 150e6 },
  { symbol: "ATOM", name: "Cosmos", price: 9.142, digits: 3, dailyVol: 0.048, tickVol: 0.0002, beta: 1.15, dayQuoteVol: 140e6 },
  { symbol: "UNI", name: "Uniswap", price: 13.728, digits: 3, dailyVol: 0.05, tickVol: 0.0002, beta: 1.18, dayQuoteVol: 160e6 },
  { symbol: "AAVE", name: "Aave", price: 312.48, digits: 2, dailyVol: 0.052, tickVol: 0.00022, beta: 1.2, dayQuoteVol: 120e6 },
  { symbol: "SEI", name: "Sei", price: 0.4826, digits: 4, dailyVol: 0.07, tickVol: 0.0003, beta: 1.45, dayQuoteVol: 220e6 },
  { symbol: "PEPE", name: "Pepe", price: 0.00001284, digits: 8, dailyVol: 0.08, tickVol: 0.00034, beta: 1.55, dayQuoteVol: 780e6 },
  { symbol: "WIF", name: "dogwifhat", price: 2.468, digits: 3, dailyVol: 0.085, tickVol: 0.00036, beta: 1.6, dayQuoteVol: 240e6 },
  { symbol: "SHIB", name: "Shiba Inu", price: 0.00002418, digits: 8, dailyVol: 0.07, tickVol: 0.0003, beta: 1.4, dayQuoteVol: 320e6 },
  { symbol: "FET", name: "FET", price: 1.624, digits: 3, dailyVol: 0.06, tickVol: 0.00024, beta: 1.3, dayQuoteVol: 200e6 },
  { symbol: "RENDER", name: "Render", price: 7.356, digits: 3, dailyVol: 0.058, tickVol: 0.00024, beta: 1.28, dayQuoteVol: 170e6 },
];

function gaussian() {
  let u = 1 - Math.random();
  if (u < 1e-12) u = 1e-12;
  const v = Math.random();
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

function align(ts, ms) {
  return ts - (ts % ms);
}

function genSeries(tfMs, count, endPrice, dailyVol, dayQuoteVol, endTime) {
  const barVol = Math.max(0.0004, dailyVol * Math.sqrt(tfMs / 86_400_000));
  const closes = new Array(count);
  closes[count - 1] = endPrice;
  for (let i = count - 2; i >= 0; i--) {
    const ret = Math.max(-0.18, Math.min(0.18, gaussian() * barVol));
    closes[i] = Math.max(endPrice * 1e-6, closes[i + 1] / (1 + ret));
  }
  const start = align(endTime, tfMs) - (count - 1) * tfMs;
  const candles = new Array(count);
  for (let i = 0; i < count; i++) {
    const close = closes[i];
    const open = i === 0 ? close / (1 + Math.max(-0.12, Math.min(0.12, gaussian() * barVol))) : closes[i - 1];
    const upper = Math.abs(gaussian()) * barVol * Math.max(open, close);
    const lower = Math.abs(gaussian()) * barVol * Math.min(open, close);
    let high = Math.max(open, close) + upper;
    let low = Math.min(open, close) - lower;
    if (low <= 0) low = Math.min(open, close) * 0.5;
    high = Math.max(high, open, close);
    low = Math.min(low, open, close);
    const vol = dayQuoteVol * (tfMs / 86_400_000) * (0.45 + Math.random() * 1.1);
    candles[i] = { t: start + i * tfMs, o: open, h: high, l: low, c: close, v: vol };
  }
  return candles;
}

function hue(symbol) {
  let h = 0;
  for (const c of symbol) h = (h * 33 + c.charCodeAt(0)) >>> 0;
  return h % 360;
}

export function createMarket() {
  const simStart = Date.now();
  let simNow = simStart;
  let lastReal = 0;
  let paused = false;
  let volMult = 1;
  let timeScale = 1;
  const pairs = new Map();

  for (const spec of SPECS) {
    const candles = {};
    for (const tf of TIMEFRAMES) {
      const count = tf.id === "1d" ? 180 : tf.id === "4h" ? 200 : 240;
      candles[tf.id] = genSeries(tf.ms, count, spec.price, spec.dailyVol, spec.dayQuoteVol, simStart);
    }
    const daily = candles["1d"][candles["1d"].length - 1];
    const pair = {
      ...spec,
      anchor: spec.price,
      price: spec.price,
      open24: daily.o,
      high24: Math.max(daily.h, spec.price),
      low24: Math.min(daily.l, spec.price),
      volume24: spec.dayQuoteVol * (0.42 + Math.random() * 0.45),
      bid: spec.price,
      ask: spec.price,
      up: daily.c >= daily.o,
      book: { asks: [], bids: [] },
      trades: [],
      candles,
      spark: candles["1m"].slice(-32).map((c) => c.c),
      lastSpark: 0,
      hue: hue(spec.symbol),
    };
    refreshBook(pair);
    for (let i = 0; i < 16; i++) pushTrade(pair, simStart - (16 - i) * 1700);
    pairs.set(spec.symbol, pair);
  }

  function refreshBook(pair) {
    const tick = Math.max(pair.price * 0.000045, Math.pow(10, -pair.digits));
    const spread = Math.max(pair.price * (0.00004 + pair.tickVol * 0.05), tick);
    pair.bid = pair.price - spread / 2;
    pair.ask = pair.price + spread / 2;
    if (pair.bid <= 0) pair.bid = tick;
    const asks = [];
    const bids = [];
    let a = pair.ask;
    let b = pair.bid;
    for (let i = 0; i < 14; i++) {
      const wobble = 0.7 + Math.random() * 1.7;
      const weight = pair.symbol === "BTC" ? 3.4 : pair.symbol === "ETH" ? 2.1 : 1;
      const quote = (5000 + i * 2200) * wobble * weight;
      asks.push([a, quote / a]);
      bids.push([b, quote / b]);
      a += tick * (1 + Math.random() * 1.5);
      b -= tick * (1 + Math.random() * 1.5);
      if (b <= 0) b = tick;
    }
    pair.book = { asks, bids };
  }

  function pushTrade(pair, ts) {
    const side = Math.random() < 0.5 ? "buy" : "sell";
    const price = side === "buy" ? pair.ask : pair.bid;
    const quote = (80 + Math.random() * 14000) * (pair.symbol === "BTC" ? 2.4 : 1);
    pair.trades.unshift({ price, qty: quote / price, side, ts: ts ?? simNow });
    if (pair.trades.length > 40) pair.trades.pop();
    pair.up = side === "buy";
  }

  function touchCandles(pair, price, quoteVol) {
    for (const tf of TIMEFRAMES) {
      const series = pair.candles[tf.id];
      let last = series[series.length - 1];
      let guard = 0;
      while (simNow >= last.t + tf.ms && guard < 4) {
        guard += 1;
        const nextT = last.t + tf.ms;
        const t = simNow - nextT > tf.ms * 2 ? align(simNow, tf.ms) : nextT;
        last = { t, o: last.c, h: price, l: price, c: price, v: 0 };
        series.push(last);
        if (series.length > 340) series.shift();
      }
      last.c = price;
      last.h = Math.max(last.h, price);
      last.l = Math.min(last.l, price);
      last.v += quoteVol;
    }
  }

  function step(pair, leaderRet, noiseScale, now) {
    const z = gaussian();
    let ret = z * pair.tickVol * volMult * noiseScale;
    if (leaderRet) ret += leaderRet * pair.beta * 0.62;
    ret += ((pair.anchor - pair.price) / pair.anchor) * 0.00012;
    ret = Math.max(-0.012, Math.min(0.012, ret));
    const prev = pair.price;
    pair.price = Math.max(pair.price * (1 + ret), pair.anchor * 1e-8);
    pair.high24 = Math.max(pair.high24, pair.price);
    pair.low24 = Math.min(pair.low24, pair.price);
    const quote = Math.abs(pair.price - prev) * pair.dayQuoteVol * 0.015 + pair.price * (0.2 + Math.random());
    pair.volume24 += Math.abs(ret) * pair.dayQuoteVol * 0.01;
    refreshBook(pair);
    touchCandles(pair, pair.price, quote * 0.001);
    if (now - pair.lastSpark > 1400) {
      pair.spark.push(pair.price);
      if (pair.spark.length > 32) pair.spark.shift();
      pair.lastSpark = now;
    }
    if (Math.random() < 0.45) pushTrade(pair);
    return pair.price / prev - 1;
  }

  function tick(now) {
    if (!lastReal) {
      lastReal = now;
      return;
    }
    const dt = Math.min(1.5, Math.max(0, (now - lastReal) / 1000));
    lastReal = now;
    if (paused || dt <= 0) return;
    simNow += dt * 1000 * timeScale;
    const noiseScale = Math.sqrt(Math.min(4, Math.max(0.25, timeScale)));
    const btc = pairs.get("BTC");
    const btcRet = step(btc, 0, noiseScale, now);
    for (const pair of pairs.values()) {
      if (pair.symbol === "BTC") continue;
      step(pair, btcRet, noiseScale, now);
    }
  }

  function shock(symbol, pct) {
    const pair = pairs.get(symbol);
    if (!pair) return;
    const next = Math.max(pair.price * (1 + pct), pair.anchor * 1e-8);
    pair.anchor = pair.anchor * 0.72 + next * 0.28;
    pair.price = next;
    pair.up = pct >= 0;
    pair.high24 = Math.max(pair.high24, next);
    pair.low24 = Math.min(pair.low24, next);
    for (const series of Object.values(pair.candles)) {
      const last = series[series.length - 1];
      last.c = next;
      last.h = Math.max(last.h, next);
      last.l = Math.min(last.l, next);
      last.v += pair.dayQuoteVol * 0.00002;
    }
    refreshBook(pair);
    pushTrade(pair);
    pair.spark.push(next);
    if (pair.spark.length > 32) pair.spark.shift();
  }

  return {
    list: () => SPECS.map((s) => pairs.get(s.symbol)),
    get: (symbol) => pairs.get(symbol),
    tick,
    shock,
    setPaused: (v) => {
      paused = v;
    },
    get paused() {
      return paused;
    },
    togglePause() {
      paused = !paused;
      return paused;
    },
    setVol: (v) => {
      volMult = v;
    },
    setTimeScale: (v) => {
      timeScale = v;
    },
    get volMult() {
      return volMult;
    },
    get timeScale() {
      return timeScale;
    },
    get simNow() {
      return simNow;
    },
  };
}
