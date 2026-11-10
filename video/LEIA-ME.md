# Vídeo explicativo da FutApp

Vídeo vertical 1080x1920, 30 fps, ~2 min 15 s, para partilhar no grupo de WhatsApp.
Falas (texto para narração com tempos): https://claude.ai/artifact/JmrKD93DnmwR3ftbqBKovW

## Ficheiros
- `captura.js` — com a app a correr em http://localhost:8099 (perfil dev, base de dados vazia, admin/admin123),
  percorre um jogo completo e guarda os ecrãs em `cap/` (`<nome>-v.jpg` ecrã visível, `<nome>-f.jpg` página inteira
  sem o rodapé fixo) e `cap/meta.json` (altura da página, altura do rodapé, posição dos botões).
  Uso: `node captura.js cap`
- `palco.html` — a página 1080x1920 onde cada frame é desenhado (`window.render(cena, t, info)`).
- `cenas.js` — o guião: cenas, duração, legendas, ecrã do telefone, scroll, toques e destaques.
- `render.js` — desenha os frames no Playwright e codifica uma cena por ficheiro em `cenas/`.
  Uso: `node render.js tudo` · `node render.js cena 04-convocatoria` · `node render.js teste 04-convocatoria@7`
- Juntar as cenas: `ffmpeg -f concat -safe 0 -i lista.txt -c copy futapp-como-funciona.mp4`

## Narração (próximo passo)
Sem voz ainda: a sessão onde foi feito não tinha rede para instalar um sintetizador.
Com rede: voz sintética pt-PT (por exemplo Piper, voz pt_PT "tugão"), um áudio por cena a partir do texto das falas;
ajustar `dur` (e os `t0` dos beats) em `cenas.js` à duração de cada áudio, voltar a renderizar as cenas que mudaram,
juntar e acrescentar o som com `-c:v copy`.
