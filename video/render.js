// Desenha o vídeo frame a frame na página palco.html e codifica cada cena num MP4.
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const fs = require('fs'); const path = require('path');
const DIR = __dirname, FPS = 30;
const CENAS = require('./cenas.js');
const META = JSON.parse(fs.readFileSync(path.join(DIR, 'cap/meta.json')));
const modo = process.argv[2] || 'teste';       // teste | cena <id> | tudo
const temTel = c => !c.tipo || c.tipo === 'intro' || c.tipo === 'passo';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(DIR, 'palco.html'));
  await page.evaluate(m => Object.assign(window.META, m), META);
  await page.evaluate(() => document.fonts.ready);
  const info = i => ({ telEntra: temTel(CENAS[i]) && (i === 0 || !temTel(CENAS[i - 1])), telSai: temTel(CENAS[i]) && (i === CENAS.length - 1 || !temTel(CENAS[i + 1])) });
  const desenhar = async (i, t) => { await page.evaluate(([c, t, inf]) => window.render(c, t, inf), [CENAS[i], t, info(i)]); };
  const preparar = async i => { await page.evaluate(c => preparar(c), CENAS[i]); await page.waitForTimeout(50); };

  if (modo === 'teste') {
    const pedidos = process.argv.slice(3);    // id@t ...
    for (const p of pedidos) {
      const [id, t] = p.split('@'); const i = CENAS.findIndex(c => c.id === id);
      await preparar(i); await desenhar(i, +t);
      await page.waitForTimeout(100);
      await page.screenshot({ path: path.join(DIR, `teste-${id}-${t}.jpg`), type: 'jpeg', quality: 90 });
    }
  } else {
    fs.mkdirSync(path.join(DIR, 'cenas'), { recursive: true });
    const lista = modo === 'tudo' ? CENAS.map((_, i) => i) : [CENAS.findIndex(c => c.id === process.argv[3])];
    for (const i of lista) {
      const c = CENAS[i], n = Math.round(c.dur * FPS);
      const tmp = path.join(DIR, 'cenas', `${c.id}.tmp.mp4`), fim = path.join(DIR, 'cenas', `${c.id}.mp4`);
      await preparar(i);
      const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
        '-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-r', String(FPS), tmp], { stdio: ['pipe', 'inherit', 'inherit'] });
      const t0 = Date.now();
      for (let f = 0; f < n; f++) {
        await desenhar(i, f / FPS);
        const buf = await page.screenshot({ type: 'jpeg', quality: 92 });
        if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      }
      ff.stdin.end();
      const code = await new Promise(r => ff.on('close', r));
      if (code !== 0) throw new Error('ffmpeg falhou em ' + c.id);
      fs.renameSync(tmp, fim);
      console.log(c.id, n, 'frames', ((Date.now() - t0) / 1000).toFixed(1) + 's');
    }
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
