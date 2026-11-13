export const TZ = "Asia/Novosibirsk";

export function clamp(n, a, b) {
  return Math.max(a, Math.min(b, n));
}

export function fmtPrice(n, digits) {
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString("en-US", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function fmtNum(n, digits = 2) {
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString("en-US", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function fmtQty(q) {
  if (!Number.isFinite(q)) return "—";
  const a = Math.abs(q);
  if (a >= 1e9) return (q / 1e9).toFixed(2) + "B";
  if (a >= 1e6) return (q / 1e6).toFixed(2) + "M";
  if (a >= 1e3) return (q / 1e3).toFixed(2) + "K";
  if (a >= 100) return q.toFixed(2);
  if (a >= 1) return q.toFixed(4);
  if (a >= 0.01) return q.toFixed(4);
  return q.toFixed(6);
}

export function fmtCompact(n) {
  if (!Number.isFinite(n)) return "—";
  const a = Math.abs(n);
  const sign = n < 0 ? "-" : "";
  if (a >= 1e12) return sign + (a / 1e12).toFixed(2) + "T";
  if (a >= 1e9) return sign + (a / 1e9).toFixed(2) + "B";
  if (a >= 1e6) return sign + (a / 1e6).toFixed(2) + "M";
  if (a >= 1e3) return sign + (a / 1e3).toFixed(2) + "K";
  return sign + a.toFixed(2);
}

export function fmtPct(x) {
  if (!Number.isFinite(x)) return "—";
  if (Math.abs(x) < 0.00005) x = 0;
  const sign = x > 0 ? "+" : "";
  return sign + (x * 100).toFixed(2) + "%";
}

export function fmtClock(ts = Date.now()) {
  return new Date(ts).toLocaleTimeString("ru-RU", {
    timeZone: TZ,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function fmtAxis(ts, tf) {
  const d = new Date(ts);
  if (tf === "1d") {
    return d.toLocaleDateString("ru-RU", { timeZone: TZ, day: "2-digit", month: "short" });
  }
  if (tf === "4h" || tf === "1h") {
    return d.toLocaleString("ru-RU", {
      timeZone: TZ,
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  return d.toLocaleTimeString("ru-RU", { timeZone: TZ, hour: "2-digit", minute: "2-digit" });
}

export function fmtTradeTime(ts) {
  return new Date(ts).toLocaleTimeString("ru-RU", {
    timeZone: TZ,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]));
}

export function parseNum(s) {
  if (typeof s === "number") return s;
  const n = Number(String(s).trim().replace(/\s/g, "").replace(",", "."));
  return Number.isFinite(n) ? n : NaN;
}
