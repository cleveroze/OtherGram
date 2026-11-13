import { clamp, fmtAxis, fmtPrice } from "./format.js";

export function createChart(canvas) {
  const ctx = canvas.getContext("2d");
  const state = {
    candles: [],
    digits: 2,
    tf: "15m",
    last: 0,
    up: true,
    orders: [],
    showEma: true,
    showVol: true,
    pinned: true,
    viewCount: 96,
    rightOffset: 0,
    hover: null,
    box: null,
    view: [],
  };

  let drag = null;

  function plotBox(w, h) {
    ctx.font = "11px 'IBM Plex Mono', ui-monospace, monospace";
    const sample = fmtPrice(state.last || 1, state.digits);
    const padR = Math.ceil(ctx.measureText(sample).width) + 18;
    return { x: 8, y: 12, w: Math.max(10, w - padR - 8), h: Math.max(10, h - 26), padR };
  }

  function ema(values, period) {
    const k = 2 / (period + 1);
    const out = new Array(values.length);
    let prev = values[0] ?? 0;
    for (let i = 0; i < values.length; i++) {
      prev = i === 0 ? values[0] : values[i] * k + prev * (1 - k);
      out[i] = prev;
    }
    return out;
  }

  function sliceView() {
    const n = state.candles.length;
    const count = clamp(Math.round(state.viewCount), 16, Math.max(16, n));
    state.viewCount = count;
    const maxOffset = Math.max(0, n - 8);
    state.rightOffset = clamp(state.rightOffset, 0, maxOffset);
    if (state.pinned) state.rightOffset = 0;
    const end = n - Math.round(state.rightOffset);
    const start = Math.max(0, end - count);
    state.view = state.candles.slice(start, Math.max(start + 1, end));
    return { start, end };
  }

  function draw() {
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (w < 8 || h < 8) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    if (canvas.width !== Math.floor(w * dpr) || canvas.height !== Math.floor(h * dpr)) {
      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    const { start } = sliceView();
    const view = state.view;
    if (!view.length) return;

    const box = plotBox(w, h);
    const volH = state.showVol ? Math.max(36, box.h * 0.18) : 0;
    const gap = state.showVol ? 8 : 0;
    const priceH = box.h - volH - gap;
    box.priceH = priceH;
    box.volH = volH;
    state.box = box;

    let min = Infinity;
    let max = -Infinity;
    let maxV = 0;
    for (const c of view) {
      min = Math.min(min, c.l);
      max = Math.max(max, c.h);
      maxV = Math.max(maxV, c.v);
    }
    min = Math.min(min, state.last);
    max = Math.max(max, state.last);
    const pad = (max - min) * 0.08 || Math.abs(max) * 0.002 || 1;
    min -= pad;
    max += pad;
    const yPrice = (p) => box.y + ((max - p) / (max - min)) * priceH;
    const slot = box.w / view.length;

    ctx.strokeStyle = "rgba(255,255,255,0.045)";
    ctx.lineWidth = 1;
    ctx.font = "11px 'IBM Plex Mono', ui-monospace, monospace";
    ctx.fillStyle = "rgba(154,166,184,0.9)";
    const ticks = 5;
    for (let i = 0; i <= ticks; i++) {
      const p = min + ((max - min) * i) / ticks;
      const y = yPrice(p);
      ctx.beginPath();
      ctx.moveTo(box.x, y);
      ctx.lineTo(box.x + box.w, y);
      ctx.stroke();
      ctx.fillText(fmtPrice(p, state.digits), box.x + box.w + 8, y + 4);
    }

    const closes = state.candles.map((c) => c.c);
    const e9 = state.showEma ? ema(closes, 9) : null;
    const e21 = state.showEma ? ema(closes, 21) : null;

    ctx.save();
    ctx.beginPath();
    ctx.rect(box.x, box.y, box.w, priceH);
    ctx.clip();

    const bull = "#1ad598";
    const bear = "#ff5d73";
    for (let i = 0; i < view.length; i++) {
      const c = view[i];
      const x = box.x + i * slot + slot / 2;
      const up = c.c >= c.o;
      const color = up ? bull : bear;
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(x, yPrice(c.h));
      ctx.lineTo(x, yPrice(c.l));
      ctx.stroke();
      const bodyW = Math.max(1.5, Math.min(16, slot * 0.64));
      const y1 = yPrice(c.o);
      const y2 = yPrice(c.c);
      const top = Math.min(y1, y2);
      const bh = Math.max(1, Math.abs(y2 - y1));
      ctx.fillStyle = color;
      ctx.globalAlpha = up ? 0.92 : 0.95;
      ctx.fillRect(x - bodyW / 2, top, bodyW, bh);
      ctx.globalAlpha = 1;
    }

    function strokeEma(series, color) {
      ctx.beginPath();
      let moved = false;
      for (let i = 0; i < view.length; i++) {
        const v = series[start + i];
        if (!Number.isFinite(v)) continue;
        const x = box.x + i * slot + slot / 2;
        const y = yPrice(v);
        if (!moved) {
          ctx.moveTo(x, y);
          moved = true;
        } else ctx.lineTo(x, y);
      }
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.25;
      ctx.stroke();
    }
    if (e9) strokeEma(e9, "rgba(228,194,122,0.9)");
    if (e21) strokeEma(e21, "rgba(126,196,255,0.85)");

    ctx.setLineDash([4, 4]);
    for (const order of state.orders) {
      if (order.price < min || order.price > max) continue;
      const y = yPrice(order.price);
      ctx.strokeStyle = order.side === "buy" ? "rgba(26,213,152,0.7)" : "rgba(255,93,115,0.75)";
      ctx.beginPath();
      ctx.moveTo(box.x, y);
      ctx.lineTo(box.x + box.w, y);
      ctx.stroke();
    }
    ctx.setLineDash([]);

    const ly = yPrice(state.last);
    ctx.strokeStyle = state.up ? "rgba(26,213,152,0.55)" : "rgba(255,93,115,0.6)";
    ctx.setLineDash([2, 3]);
    ctx.beginPath();
    ctx.moveTo(box.x, ly);
    ctx.lineTo(box.x + box.w, ly);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();

    const tag = fmtPrice(state.last, state.digits);
    const tw = ctx.measureText(tag).width + 12;
    roundRect(ctx, box.x + box.w + 4, ly - 9, tw, 18, 4);
    ctx.fillStyle = state.up ? bull : bear;
    ctx.fill();
    ctx.fillStyle = "#06110c";
    ctx.fillText(tag, box.x + box.w + 10, ly + 4);

    if (state.showVol && maxV > 0) {
      const base = box.y + priceH + gap + volH;
      for (let i = 0; i < view.length; i++) {
        const c = view[i];
        const x = box.x + i * slot + slot / 2;
        const bw = Math.max(1, slot * 0.64);
        const vh = (c.v / maxV) * (volH - 2);
        ctx.globalAlpha = 0.38;
        ctx.fillStyle = c.c >= c.o ? bull : bear;
        ctx.fillRect(x - bw / 2, base - vh, bw, vh);
      }
      ctx.globalAlpha = 1;
      ctx.strokeStyle = "rgba(255,255,255,0.06)";
      ctx.beginPath();
      ctx.moveTo(box.x, box.y + priceH + gap / 2);
      ctx.lineTo(box.x + box.w, box.y + priceH + gap / 2);
      ctx.stroke();
    }

    ctx.fillStyle = "rgba(154,166,184,0.85)";
    const labels = 6;
    for (let i = 0; i < labels; i++) {
      const idx = Math.min(view.length - 1, Math.round((i * (view.length - 1)) / (labels - 1)));
      const c = view[idx];
      if (!c) continue;
      const x = box.x + idx * slot;
      ctx.fillText(fmtAxis(c.t, state.tf), x, h - 6);
    }

    ctx.font = "600 42px Manrope, sans-serif";
    ctx.fillStyle = "rgba(255,255,255,0.035)";
    ctx.fillText("VELORA", box.x + 12, box.y + priceH * 0.55);

    if (state.hover && state.hover.x >= box.x && state.hover.x <= box.x + box.w) {
      ctx.strokeStyle = "rgba(255,255,255,0.28)";
      ctx.setLineDash([3, 3]);
      ctx.beginPath();
      ctx.moveTo(state.hover.x, box.y);
      ctx.lineTo(state.hover.x, box.y + priceH);
      ctx.moveTo(box.x, state.hover.y);
      ctx.lineTo(box.x + box.w, state.hover.y);
      ctx.stroke();
      ctx.setLineDash([]);
    }
  }

  function hoverAt(clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    const x = clientX - rect.left;
    const y = clientY - rect.top;
    const box = state.box;
    if (!box || !state.view.length) {
      state.hover = null;
      return;
    }
    const slot = box.w / state.view.length;
    const i = Math.floor((x - box.x) / slot);
    const candle = i >= 0 && i < state.view.length ? state.view[i] : null;
    state.hover = candle ? { x, y, candle } : null;
  }

  canvas.addEventListener("pointerdown", (e) => {
    drag = { x: e.clientX, offset: state.rightOffset, moved: false };
    canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener("pointermove", (e) => {
    hoverAt(e.clientX, e.clientY);
    if (!drag || !state.box) return;
    const dx = e.clientX - drag.x;
    if (Math.abs(dx) > 2) drag.moved = true;
    const slot = state.box.w / state.viewCount;
    state.rightOffset = clamp(drag.offset + dx / slot, 0, Math.max(0, state.candles.length - 8));
    state.pinned = state.rightOffset < 0.6;
  });
  canvas.addEventListener("pointerup", () => {
    drag = null;
  });
  canvas.addEventListener("pointerleave", () => {
    if (!drag) state.hover = null;
  });
  canvas.addEventListener("dblclick", () => {
    state.pinned = true;
    state.rightOffset = 0;
    state.viewCount = 96;
  });
  canvas.addEventListener(
    "wheel",
    (e) => {
      e.preventDefault();
      const factor = e.deltaY > 0 ? 1.12 : 0.9;
      state.viewCount = clamp(state.viewCount * factor, 16, Math.max(16, state.candles.length));
    },
    { passive: false }
  );

  return {
    state,
    draw,
    pin() {
      state.pinned = true;
      state.rightOffset = 0;
    },
    clearHover() {
      state.hover = null;
    },
    legend() {
      const c = state.hover?.candle || state.candles[state.candles.length - 1 - Math.round(state.rightOffset)] || state.candles.at(-1);
      if (!c) return null;
      return c;
    },
    resize() {
      draw();
    },
  };
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
