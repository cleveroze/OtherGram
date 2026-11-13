const COLLECTIONS = [
  { id: "relics", name: "Aureum Relics", artist: "Kite Studio", blurb: "Литые реликвии северного двора" },
  { id: "stations", name: "Void Stations", artist: "M. Arendt", blurb: "Пустые станции на краю ленты" },
  { id: "saints", name: "Glass Saints", artist: "Ivo Lane", blurb: "Стеклянные лики, пойманные в свет" },
  { id: "keys", name: "Northline Keys", artist: "VELORA Desk", blurb: "Ключи к бумажным хранилищам" },
];

const PALETTES = {
  relics: ["#140f0a", "#3a2a16", "#e4c27a", "#f4e7c8", "#8a5a2b"],
  stations: ["#0b1218", "#163044", "#8fd0ff", "#e5f3ff", "#3c6d99"],
  saints: ["#140c16", "#3c1844", "#e7b6ff", "#f7ecff", "#8d4e9c"],
  keys: ["#0c1412", "#14352c", "#8ef0c8", "#e9fff6", "#1d6a54"],
};

const FORMS = ["Monolith", "Ring", "Shard", "Veil", "Spire", "Orbit"];
const SIGNALS = ["Low", "Clear", "Broken", "Gold", "Night"];

const RARITY = {
  common: { label: "Обычная", price: 180 },
  rare: { label: "Редкая", price: 460 },
  epic: { label: "Эпик", price: 980 },
  mythic: { label: "Мифик", price: 2460 },
};

function mulberry32(seed) {
  let a = seed >>> 0;
  return function rand() {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hash(str) {
  let h = 2166136261;
  for (const c of str) {
    h ^= c.charCodeAt(0);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function buildCatalog() {
  const items = [];
  for (const col of COLLECTIONS) {
    for (let n = 1; n <= 6; n++) {
      const seed = hash(`${col.id}:${n}`);
      const rng = mulberry32(seed);
      const roll = rng();
      const rarity = roll > 0.93 ? "mythic" : roll > 0.78 ? "epic" : roll > 0.46 ? "rare" : "common";
      const price = Math.round(RARITY[rarity].price * (0.86 + rng() * 0.36));
      items.push({
        id: `${col.id}-${n}`,
        collection: col.id,
        name: `${col.name.split(" ")[0]} #${String(n).padStart(2, "0")}`,
        seed,
        rarity,
        price,
        form: FORMS[Math.floor(rng() * FORMS.length)],
        signal: SIGNALS[Math.floor(rng() * SIGNALS.length)],
        edition: n,
      });
    }
  }
  return items;
}

export const NFTS = buildCatalog();
export const NFT_BY_ID = Object.fromEntries(NFTS.map((n) => [n.id, n]));
export const COLLECTION_LIST = COLLECTIONS;

export function rarityLabel(id) {
  return RARITY[id]?.label || id;
}

export function collectionById(id) {
  return COLLECTIONS.find((c) => c.id === id);
}

export function nftSvg(nft) {
  const rng = mulberry32(nft.seed);
  const pal = PALETTES[nft.collection];
  const id = `n${nft.seed.toString(16)}`;
  const shapes = [];
  for (let i = 0; i < 6; i++) {
    const x = (rng() * 86 + 7).toFixed(2);
    const y = (rng() * 86 + 7).toFixed(2);
    const s = (10 + rng() * 34).toFixed(2);
    const rot = (rng() * 180).toFixed(1);
    const color = pal[2 + Math.floor(rng() * 3)];
    const op = (0.28 + rng() * 0.55).toFixed(2);
    if (rng() > 0.45) {
      shapes.push(`<circle cx="${x}" cy="${y}" r="${(s / 2).toFixed(2)}" fill="${color}" opacity="${op}"/>`);
    } else {
      shapes.push(
        `<rect x="${(x - s / 2).toFixed(2)}" y="${(y - s / 2).toFixed(2)}" width="${s}" height="${(s * (0.45 + rng())).toFixed(2)}" rx="${(rng() * 8).toFixed(2)}" fill="${color}" opacity="${op}" transform="rotate(${rot} ${x} ${y})"/>`
      );
    }
  }
  const ring = nft.rarity === "mythic" ? pal[2] : nft.rarity === "epic" ? pal[4] : nft.rarity === "rare" ? pal[2] : "rgba(255,255,255,0.08)";
  const ringW = nft.rarity === "common" ? 0.4 : 1.1;
  const label = String(nft.name).replace(/[<>&"]/g, "");
  return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${label}">
    <defs>
      <linearGradient id="${id}-g" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="${pal[1]}"/>
        <stop offset="1" stop-color="${pal[0]}"/>
      </linearGradient>
      <filter id="${id}-f" x="-20%" y="-20%" width="140%" height="140%">
        <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed="${nft.seed % 80}"/>
        <feColorMatrix type="saturate" values="0"/>
        <feComponentTransfer><feFuncA type="linear" slope="0.22"/></feComponentTransfer>
      </filter>
    </defs>
    <rect width="100" height="100" fill="url(#${id}-g)"/>
    ${shapes.join("")}
    <rect width="100" height="100" filter="url(#${id}-f)"/>
    <rect x="1" y="1" width="98" height="98" fill="none" stroke="${ring}" stroke-width="${ringW}"/>
    <rect x="0" y="0" width="100" height="28" fill="#fff" opacity="0.05"/>
  </svg>`;
}
