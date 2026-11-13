(function (global) {
  function ema(data, period) {
    const k = 2 / (period + 1);
    const out = new Array(data.length);
    let prev = data[0]?.c ?? 0;
    for (let i = 0; i < data.length; i++) {
      prev = i === 0 ? data[i].c : data[i].c * k + prev * (1 - k);
      out[i] = prev;
    }
    return out;
  }
  function sma(data, period, key = "c") {
    const out = new Array(data.length).fill(null);
    let sum = 0;
    for (let i = 0; i < data.length; i++) {
      sum += data[i][key];
      if (i >= period) sum -= data[i - period][key];
      if (i >= period - 1) out[i] = sum / period;
    }
    return out;
  }
  function stdev(data, period, meanArr) {
    const out = new Array(data.length).fill(null);
    for (let i = period - 1; i < data.length; i++) {
      const mean = meanArr[i];
      let s = 0;
      for (let j = 0; j < period; j++) s += (data[i - j].c - mean) ** 2;
      out[i] = Math.sqrt(s / period);
    }
    return out;
  }

  class CandleChart {
    constructor(canvas) {
      this.canvas = canvas;
      this.ctx = canvas.getContext("2d");
      this.data = [];
      this.offset = 0;
      this.scale = 1;
      this.hover = null;
      this.inds = { ema: true, vol: true, bb: false };
      this.dpr = 1;
      this.pad = { l: 8, r: 72, t: 12, b: 22 };
      this.dragging = false;
      this.lastX = 0;
      this.bind();
      this.resize();
    }

    bind() {
      const c = this.canvas;
      const onMove = (e) => {
        const r = c.getBoundingClientRect();
        const x = e.clientX - r.left;
        const y = e.clientY - r.top;
        if (this.dragging) {
          this.offset += (x - this.lastX) / Math.max(4, this.barW());
          this.lastX = x;
          this.draw();
          return;
        }
        this.hover = { x, y };
        this.draw();
      };
      c.addEventListener("pointermove", onMove);
      c.addEventListener("pointerdown", (e) => {
        this.dragging = true;
        this.lastX = e.clientX - c.getBoundingClientRect().left;
        c.setPointerCapture(e.pointerId);
      });
      c.addEventListener("pointerup", () => { this.dragging = false; });
      c.addEventListener("pointerleave", () => { this.hover = null; this.dragging = false; this.draw(); });
      c.addEventListener("wheel", (e) => {
        e.preventDefault();
        this.scale = NX.clamp(this.scale * (e.deltaY > 0 ? 0.9 : 1.12), 0.4, 6);
        this.draw();
      }, { passive: false });
      c.addEventListener("dblclick", () => this.fit());
      window.addEventListener("resize", () => this.resize());
    }

    fit() {
      this.offset = 0;
      this.scale = 1;
      this.draw();
    }

    resize() {
      const r = this.canvas.getBoundingClientRect();
      this.dpr = Math.min(2, window.devicePixelRatio || 1);
      this.canvas.width = Math.max(1, r.width * this.dpr);
      this.canvas.height = Math.max(1, r.height * this.dpr);
      this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
      this.w = r.width;
      this.h = r.height;
      this.draw();
    }

    setData(data) {
      this.data = data;
      this.draw();
    }

    barW() {
      const n = Math.max(20, Math.round(72 / this.scale));
      const plotW = this.w - this.pad.l - this.pad.r;
      return plotW / n;
    }

    visible() {
      const n = Math.max(20, Math.round(72 / this.scale));
      const end = Math.min(this.data.length, this.data.length - Math.round(this.offset));
      const start = Math.max(0, end - n);
      return { start, end: Math.max(start + 1, end), n };
    }

    draw() {
      const { ctx, w, h } = this;
      if (!w || !h) return;
      ctx.clearRect(0, 0, w, h);
      const data = this.data;
      if (!data.length) return;
      const { start, end } = this.visible();
      const slice = data.slice(start, end);
      const volH = this.inds.vol ? h * 0.18 : 0;
      const plotT = this.pad.t;
      const plotB = h - this.pad.b - volH - 8;
      const plotL = this.pad.l;
      const plotR = w - this.pad.r;
      const plotW = plotR - plotL;
      const plotH = plotB - plotT;

      let lo = Infinity, hi = -Infinity, vmax = 0;
      for (const c of slice) {
        lo = Math.min(lo, c.l);
        hi = Math.max(hi, c.h);
        vmax = Math.max(vmax, c.v);
      }
      const e7 = this.inds.ema ? ema(data, 7) : null;
      const e25 = this.inds.ema ? ema(data, 25) : null;
      const e99 = this.inds.ema ? ema(data, 99) : null;
      const mid = this.inds.bb ? sma(data, 20) : null;
      const sd = this.inds.bb ? stdev(data, 20, mid) : null;
      if (this.inds.ema) {
        for (let i = start; i < end; i++) {
          if (i >= 6 && Number.isFinite(e7[i])) { lo = Math.min(lo, e7[i]); hi = Math.max(hi, e7[i]); }
          if (i >= 24 && Number.isFinite(e25[i])) { lo = Math.min(lo, e25[i]); hi = Math.max(hi, e25[i]); }
          if (i >= 98 && Number.isFinite(e99[i])) { lo = Math.min(lo, e99[i]); hi = Math.max(hi, e99[i]); }
        }
      }
      const padPx = (hi - lo) * 0.08 || 1;
      lo -= padPx; hi += padPx;
      const y = (p) => plotT + (1 - (p - lo) / (hi - lo)) * plotH;
      const bw = plotW / slice.length;
      const body = Math.max(1.2, bw * 0.62);

      ctx.strokeStyle = "rgba(255,255,255,0.045)";
      ctx.lineWidth = 1;
      ctx.font = "10px IBM Plex Mono";
      ctx.fillStyle = "#5c6478";
      for (let i = 0; i < 6; i++) {
        const py = plotT + (plotH / 5) * i;
        ctx.beginPath(); ctx.moveTo(plotL, py); ctx.lineTo(plotR, py); ctx.stroke();
        const val = hi - ((hi - lo) / 5) * i;
        ctx.fillText(NX.fmt(val, val > 100 ? 2 : 4), plotR + 8, py + 3);
      }

      if (this.inds.bb && mid) {
        ctx.beginPath();
        let started = false;
        for (let i = 0; i < slice.length; i++) {
          const idx = start + i;
          if (mid[idx] == null || sd[idx] == null) continue;
          const x = plotL + i * bw + bw / 2;
          const yu = y(mid[idx] + 2 * sd[idx]);
          if (!started) { ctx.moveTo(x, yu); started = true; } else ctx.lineTo(x, yu);
        }
        for (let i = slice.length - 1; i >= 0; i--) {
          const idx = start + i;
          if (mid[idx] == null || sd[idx] == null) continue;
          const x = plotL + i * bw + bw / 2;
          ctx.lineTo(x, y(mid[idx] - 2 * sd[idx]));
        }
        ctx.closePath();
        ctx.fillStyle = "rgba(124,108,255,0.08)";
        ctx.fill();
      }

      const drawLine = (arr, color) => {
        ctx.beginPath();
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.15;
        let s = false;
        for (let i = 0; i < slice.length; i++) {
          const v = arr[start + i];
          if (v == null) continue;
          const x = plotL + i * bw + bw / 2;
          if (!s) { ctx.moveTo(x, y(v)); s = true; } else ctx.lineTo(x, y(v));
        }
        ctx.stroke();
      };
      if (this.inds.ema) {
        drawLine(e7, "rgba(240,185,11,.9)");
        drawLine(e25, "rgba(0,224,184,.85)");
        drawLine(e99, "rgba(124,108,255,.8)");
      }

      for (let i = 0; i < slice.length; i++) {
        const cndl = slice[i];
        const x = plotL + i * bw + bw / 2;
        const up = cndl.c >= cndl.o;
        const col = up ? "#0ecb81" : "#f6465d";
        ctx.strokeStyle = col;
        ctx.fillStyle = col;
        ctx.beginPath();
        ctx.moveTo(x, y(cndl.h));
        ctx.lineTo(x, y(cndl.l));
        ctx.stroke();
        const top = y(Math.max(cndl.o, cndl.c));
        const bot = y(Math.min(cndl.o, cndl.c));
        const bh = Math.max(1, bot - top);
        ctx.globalAlpha = up ? 0.95 : 1;
        ctx.fillRect(x - body / 2, top, body, bh);
        ctx.globalAlpha = 1;
      }

      if (this.inds.vol) {
        const vTop = h - this.pad.b - volH;
        for (let i = 0; i < slice.length; i++) {
          const cndl = slice[i];
          const x = plotL + i * bw + bw / 2;
          const vh = (cndl.v / vmax) * (volH - 4);
          ctx.fillStyle = cndl.c >= cndl.o ? "rgba(14,203,129,.35)" : "rgba(246,70,93,.35)";
          ctx.fillRect(x - body / 2, vTop + volH - vh, body, vh);
        }
      }

      const last = data[data.length - 1];
      const ly = y(last.c);
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = last.c >= last.o ? "rgba(14,203,129,.7)" : "rgba(246,70,93,.7)";
      ctx.beginPath(); ctx.moveTo(plotL, ly); ctx.lineTo(plotR, ly); ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = last.c >= last.o ? "#0ecb81" : "#f6465d";
      ctx.fillRect(plotR + 4, ly - 8, 64, 16);
      ctx.fillStyle = "#081018";
      ctx.font = "600 10px IBM Plex Mono";
      ctx.fillText(NX.fmt(last.c), plotR + 8, ly + 3);

      const step = Math.max(1, Math.floor(slice.length / 6));
      ctx.fillStyle = "#5c6478";
      ctx.font = "10px IBM Plex Mono";
      for (let i = 0; i < slice.length; i += step) {
        const t = new Date(slice[i].t);
        const label = `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}`;
        ctx.fillText(label, plotL + i * bw, h - 6);
      }

      if (this.hover) {
        const i = NX.clamp(Math.floor((this.hover.x - plotL) / bw), 0, slice.length - 1);
        const cndl = slice[i];
        if (cndl) {
          const x = plotL + i * bw + bw / 2;
          ctx.strokeStyle = "rgba(232,237,247,.25)";
          ctx.setLineDash([3, 3]);
          ctx.beginPath(); ctx.moveTo(x, plotT); ctx.lineTo(x, plotB); ctx.stroke();
          ctx.beginPath(); ctx.moveTo(plotL, this.hover.y); ctx.lineTo(plotR, this.hover.y); ctx.stroke();
          ctx.setLineDash([]);
          this.hud = cndl;
          this.hudIndex = start + i;
        }
      } else {
        this.hud = last;
        this.hudIndex = data.length - 1;
      }
    }
  }

  global.CandleChart = CandleChart;
})(window);
