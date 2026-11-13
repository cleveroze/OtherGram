(function (global) {
  const clamp = (n, a, b) => Math.max(a, Math.min(b, n));
  const rnd = (a, b) => a + Math.random() * (b - a);
  const gauss = () => {
    let u = 0, v = 0;
    while (!u) u = Math.random();
    while (!v) v = Math.random();
    return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  };
  const fmt = (n, d = 2) => {
    if (n === 0) return (0).toFixed(d);
    const abs = Math.abs(n);
    if (abs >= 1000) return n.toLocaleString("en-US", { minimumFractionDigits: d, maximumFractionDigits: d });
    if (abs >= 1) return n.toFixed(Math.max(d, 2));
    if (abs >= 0.01) return n.toFixed(4);
    return n.toFixed(6);
  };
  const fmtUsd = (n) => "$" + fmt(n, 2);
  const now = () => Date.now();

  const COINS = [
    { symbol: "BTC", name: "Bitcoin", price: 97240.5, vol: 0.011, decimals: 2, cat: "L1", color: "#F7931A" },
    { symbol: "ETH", name: "Ethereum", price: 3521.4, vol: 0.014, decimals: 2, cat: "L1", color: "#627EEA" },
    { symbol: "SOL", name: "Solana", price: 178.62, vol: 0.022, decimals: 2, cat: "L1", color: "#9945FF" },
    { symbol: "BNB", name: "BNB", price: 612.18, vol: 0.015, decimals: 2, cat: "L1", color: "#F3BA2F" },
    { symbol: "XRP", name: "XRP", price: 2.184, vol: 0.02, decimals: 4, cat: "L1", color: "#23292F" },
    { symbol: "TON", name: "Toncoin", price: 6.42, vol: 0.021, decimals: 4, cat: "L1", color: "#0098EA" },
    { symbol: "ADA", name: "Cardano", price: 0.612, vol: 0.018, decimals: 4, cat: "L1", color: "#0033AD" },
    { symbol: "AVAX", name: "Avalanche", price: 38.41, vol: 0.02, decimals: 2, cat: "L1", color: "#E84142" },
    { symbol: "DOT", name: "Polkadot", price: 7.84, vol: 0.019, decimals: 3, cat: "L1", color: "#E6007A" },
    { symbol: "NEAR", name: "NEAR", price: 5.12, vol: 0.023, decimals: 3, cat: "L1", color: "#00C08B" },
    { symbol: "SUI", name: "Sui", price: 3.46, vol: 0.024, decimals: 3, cat: "L1", color: "#4DA2FF" },
    { symbol: "APT", name: "Aptos", price: 9.72, vol: 0.023, decimals: 3, cat: "L1", color: "#2DD4BF" },
    { symbol: "LINK", name: "Chainlink", price: 18.44, vol: 0.017, decimals: 3, cat: "DeFi", color: "#2A5ADA" },
    { symbol: "UNI", name: "Uniswap", price: 9.18, vol: 0.02, decimals: 3, cat: "DeFi", color: "#FF007A" },
    { symbol: "AAVE", name: "Aave", price: 214.6, vol: 0.021, decimals: 2, cat: "DeFi", color: "#B6509E" },
    { symbol: "ONDO", name: "Ondo", price: 1.12, vol: 0.026, decimals: 4, cat: "DeFi", color: "#1B4DFF" },
    { symbol: "JUP", name: "Jupiter", price: 0.842, vol: 0.028, decimals: 4, cat: "DeFi", color: "#22D3EE" },
    { symbol: "INJ", name: "Injective", price: 24.18, vol: 0.024, decimals: 2, cat: "DeFi", color: "#00F2FE" },
    { symbol: "FET", name: "Fetch.ai", price: 1.64, vol: 0.03, decimals: 4, cat: "AI", color: "#1E40AF" },
    { symbol: "RENDER", name: "Render", price: 7.28, vol: 0.027, decimals: 3, cat: "AI", color: "#EF4444" },
    { symbol: "TAO", name: "Bittensor", price: 412.8, vol: 0.032, decimals: 2, cat: "AI", color: "#F8FAFC" },
    { symbol: "WLD", name: "Worldcoin", price: 2.41, vol: 0.029, decimals: 3, cat: "AI", color: "#111111" },
    { symbol: "DOGE", name: "Dogecoin", price: 0.1684, vol: 0.03, decimals: 4, cat: "Meme", color: "#C2A633" },
    { symbol: "SHIB", name: "Shiba Inu", price: 0.00001842, vol: 0.034, decimals: 8, cat: "Meme", color: "#FFA409" },
    { symbol: "PEPE", name: "Pepe", price: 0.00000912, vol: 0.04, decimals: 8, cat: "Meme", color: "#3D9A3D" },
    { symbol: "WIF", name: "dogwifhat", price: 2.08, vol: 0.038, decimals: 3, cat: "Meme", color: "#E7C08A" },
    { symbol: "BONK", name: "Bonk", price: 0.0000214, vol: 0.036, decimals: 8, cat: "Meme", color: "#F5A623" },
    { symbol: "ARB", name: "Arbitrum", price: 0.842, vol: 0.021, decimals: 4, cat: "L1", color: "#28A0F0" },
    { symbol: "OP", name: "Optimism", price: 1.74, vol: 0.022, decimals: 3, cat: "L1", color: "#FF0420" },
    { symbol: "LTC", name: "Litecoin", price: 92.4, vol: 0.016, decimals: 2, cat: "L1", color: "#345D9D" }
  ];

  const TFS = [
    { id: "1m", label: "1м", ms: 60_000 },
    { id: "5m", label: "5м", ms: 300_000 },
    { id: "15m", label: "15м", ms: 900_000 },
    { id: "1h", label: "1ч", ms: 3_600_000 },
    { id: "4h", label: "4ч", ms: 14_400_000 },
    { id: "1D", label: "1д", ms: 86_400_000 }
  ];

  const COLLECTIONS = [
    { id: "neon", name: "Neon Relics", tint: ["#00e0b8", "#7c6cff"], floor: 1.8 },
    { id: "cipher", name: "Cipher Punks", tint: ["#f0b90b", "#ff6b7d"], floor: 3.4 },
    { id: "void", name: "Void Walkers", tint: ["#4c1d95", "#22d3ee"], floor: 2.1 },
    { id: "orbital", name: "Orbital Keys", tint: ["#38bdf8", "#f472b6"], floor: 0.92 },
    { id: "glass", name: "Glass Gods", tint: ["#e2e8f0", "#00e0b8"], floor: 5.6 },
    { id: "chrono", name: "Chrono Masks", tint: ["#fb7185", "#f59e0b"], floor: 1.25 }
  ];

  function seedRand(seed) {
    let s = seed >>> 0;
    return () => {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  function nftArt(id, col) {
    const r = seedRand(id * 9973 + col.id.length * 13);
    const [a, b] = col.tint;
    const shapes = [];
    for (let i = 0; i < 7; i++) {
      const x = (r() * 100).toFixed(1);
      const y = (r() * 100).toFixed(1);
      const rad = (8 + r() * 38).toFixed(1);
      const op = (0.18 + r() * 0.5).toFixed(2);
      shapes.push(`<circle cx="${x}" cy="${y}" r="${rad}" fill="${r() > 0.5 ? a : b}" opacity="${op}"/>`);
    }
    for (let i = 0; i < 5; i++) {
      const x = (r() * 90).toFixed(1);
      const y = (r() * 90).toFixed(1);
      const w = (10 + r() * 40).toFixed(1);
      shapes.push(`<rect x="${x}" y="${y}" width="${w}" height="${(w * (0.3 + r())).toFixed(1)}" rx="4" fill="white" opacity="${(0.04 + r() * 0.12).toFixed(2)}" transform="rotate(${(r() * 60 - 30).toFixed(1)} ${x} ${y})"/>`);
    }
    return `<svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
      <defs><linearGradient id="g${id}" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/>
      </linearGradient></defs>
      <rect width="100" height="100" fill="url(#g${id})"/>
      ${shapes.join("")}
      <text x="8" y="92" fill="white" font-size="7" font-family="IBM Plex Mono" opacity=".8">#${String(id).padStart(4, "0")}</text>
    </svg>`;
  }

  const RARITIES = [
    { id: "common", w: 48 },
    { id: "uncommon", w: 28 },
    { id: "rare", w: 14 },
    { id: "epic", w: 7 },
    { id: "legendary", w: 3 }
  ];
  function pickRarity(r) {
    const t = r() * 100;
    let acc = 0;
    for (const x of RARITIES) {
      acc += x.w;
      if (t <= acc) return x.id;
    }
    return "common";
  }

  function buildNfts() {
    const list = [];
    let n = 1001;
    for (const col of COLLECTIONS) {
      for (let i = 0; i < 8; i++) {
        const r = seedRand(n * 17);
        const rarity = pickRarity(r);
        const mult = { common: 1, uncommon: 1.4, rare: 2.1, epic: 3.6, legendary: 7.2 }[rarity];
        list.push({
          id: n,
          collection: col.id,
          collectionName: col.name,
          name: `${col.name.split(" ")[0]} #${n}`,
          rarity,
          price: +(col.floor * mult * (0.85 + r() * 0.4)).toFixed(3),
          art: nftArt(n, col),
          listed: true
        });
        n++;
      }
    }
    return list;
  }

  function genHistory(base, vol, tfMs, count) {
    const candles = [];
    let price = base;
    let t = now() - tfMs * count;
    for (let i = 0; i < count; i++) {
      const open = price;
      const ret = gauss() * vol * 0.62 + Math.sin(i / 21) * vol * 0.12;
      let close = open * (1 + ret);
      close = Math.max(open * 0.972, Math.min(open * 1.028, close));
      close = Math.max(base * 0.86, Math.min(base * 1.14, close));
      const wick = Math.abs(gauss()) * vol * open * 0.28;
      const high = Math.max(open, close) + wick;
      const low = Math.min(open, close) - wick * 0.85;
      const volume = 18 + Math.abs(gauss()) * (70 + Math.sqrt(Math.max(base, 1)) * 6);
      candles.push({ t, o: open, h: high, l: low, c: close, v: volume });
      price = close;
      t += tfMs;
    }
    return candles;
  }

  function makeBook(price, decimals) {
    const asks = [];
    const bids = [];
    let pa = price;
    let pb = price;
    for (let i = 0; i < 14; i++) {
      pa *= 1 + rnd(0.00012, 0.0009);
      pb *= 1 - rnd(0.00012, 0.0009);
      const sa = rnd(0.02, 4.8) * (1 + i * 0.12);
      const sb = rnd(0.02, 4.8) * (1 + i * 0.12);
      asks.push({ p: +pa.toFixed(decimals), s: +sa.toFixed(4) });
      bids.push({ p: +pb.toFixed(decimals), s: +sb.toFixed(4) });
    }
    return { asks, bids };
  }

  class Engine {
    constructor() {
      this.volatility = 1;
      this.speed = 3;
      this.frozen = false;
      this.news = [];
      this.markets = {};
      this.trades = {};
      this.nfts = buildNfts();
      for (const c of COINS) {
        const candles = {};
        for (const tf of TFS) candles[tf.id] = genHistory(c.price, c.vol, tf.ms, 160);
        const last = candles["15m"][candles["15m"].length - 1];
        const first = candles["15m"][Math.max(0, candles["15m"].length - 96)];
        this.markets[c.symbol] = {
          ...c,
          price: last.c,
          open24: first.c,
          high24: Math.max(...candles["1h"].slice(-24).map((x) => x.h)),
          low24: Math.min(...candles["1h"].slice(-24).map((x) => x.l)),
          vol24: candles["1h"].slice(-24).reduce((s, x) => s + x.v, 0),
          candles,
          book: makeBook(last.c, c.decimals),
          funding: rnd(-0.012, 0.018)
        };
        this.trades[c.symbol] = [];
        for (let i = 0; i < 18; i++) this.pushTrade(c.symbol, last.c, true);
      }
    }

    pushTrade(sym, px, silent) {
      const m = this.markets[sym];
      const side = Math.random() > 0.48 ? "buy" : "sell";
      const qty = +(rnd(0.001, m.price > 1000 ? 0.8 : 24)).toFixed(4);
      const row = { px, qty, side, t: now() };
      const arr = this.trades[sym];
      arr.unshift(row);
      if (arr.length > 40) arr.pop();
      return silent ? row : row;
    }

    chg(sym) {
      const m = this.markets[sym];
      return ((m.price - m.open24) / m.open24) * 100;
    }

    tick() {
      if (this.frozen) return;
      const btcMove = gauss() * 0.00035 * this.volatility;
      for (const c of COINS) {
        const m = this.markets[c.symbol];
        const beta = c.symbol === "BTC" ? 1 : 0.35 + c.vol * 8;
        let move = gauss() * c.vol * 0.0018 * this.volatility + btcMove * beta * 0.45;
        if (Math.random() < 0.004 * this.volatility) move += rnd(-0.012, 0.012) * this.volatility;
        if (m.shock) {
          move += m.shock;
          m.shock *= 0.72;
          if (Math.abs(m.shock) < 0.0002) m.shock = 0;
        }
        const next = Math.max(c.price * 0.0000001, m.price * (1 + move));
        m.price = next;
        m.high24 = Math.max(m.high24, next);
        m.low24 = Math.min(m.low24, next);
        m.vol24 += Math.abs(move) * 80;
        for (const tf of TFS) {
          const arr = m.candles[tf.id];
          let last = arr[arr.length - 1];
          const bucket = Math.floor(now() / tf.ms) * tf.ms;
          if (last.t < bucket) {
            arr.push({ t: bucket, o: last.c, h: next, l: next, c: next, v: rnd(4, 90) });
            if (arr.length > 240) arr.shift();
            last = arr[arr.length - 1];
          } else {
            last.c = next;
            last.h = Math.max(last.h, next);
            last.l = Math.min(last.l, next);
            last.v += rnd(0.2, 6);
          }
        }
        m.book = makeBook(m.price, m.decimals);
        if (Math.random() < 0.55) this.pushTrade(c.symbol, m.price);
      }
    }

    pump(sym, dir = 1) {
      const m = this.markets[sym];
      m.shock = (0.018 + Math.random() * 0.03) * dir;
    }

    freeze(on) {
      this.frozen = on;
    }
  }

  global.NX = {
    COINS, TFS, COLLECTIONS, Engine, fmt, fmtUsd, clamp, now, seedRand
  };
})(window);
