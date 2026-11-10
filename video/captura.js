// Percorre um jogo completo na app real e guarda os ecrãs (viewport + página inteira) para o vídeo.
const { chromium, request } = require('playwright');
const fs = require('fs');
const BASE = 'http://localhost:8099';
const OUT = process.argv[2];
const BOOTSTRAP = '/usr/local/lib/python3.13/dist-packages/mkdocs/themes/mkdocs/css/bootstrap.min.css';
const csrfDe = html => (html.match(/name="_csrf" value="([^"]+)"/) || [])[1];
const meta = {};

async function postForm(ctx, getUrl, postUrl, form) {
  const html = await (await ctx.get(getUrl)).text();
  return ctx.post(postUrl, { form: { ...form, _csrf: csrfDe(html) } });
}

(async () => {
  // ------------------------------------------------ dados de exemplo
  const api = await request.newContext({ baseURL: BASE });
  { const html = await (await api.get('/login')).text();
    await api.post('/login', { form: { username: 'admin', password: 'admin123', _csrf: csrfDe(html) } }); }
  const convPage = await (await postForm(api, '/admin/convites', '/admin/convites', { dias: '7' })).text();
  const token = convPage.match(/\/registo\?convite=([A-Za-z0-9_-]+)/)[1];
  const pessoas = [
    ['rui', 'Rui Costa'], ['nuno', 'Nuno Silva'], ['pedro', 'Pedro Alves'], ['andre', 'André Santos'],
    ['bruno', 'Bruno Faria'], ['diogo', 'Diogo Lopes'], ['tiago', 'Tiago Rocha'], ['hugo', 'Hugo Marques'],
    ['miguel', 'Miguel Pinto'], ['joao', 'João Ferreira'], ['ricardo', 'Ricardo Gomes'],
    ['sergio', 'Sérgio Matos'], ['luis', 'Luís Carvalho'], ['carlos', 'Carlos Dias'],
  ];
  const id = u => pessoas.findIndex(p => p[0] === u) + 2;
  const anon = await request.newContext({ baseURL: BASE });
  for (const [u, nome] of pessoas) {
    await postForm(anon, `/registo?convite=${token}`, '/registo', { convite: token, nome, username: u, password: 'segredo123' });
  }
  const cartas = {
    rui: { alcunha: 'Maestro', posicao: 'MEDIO', posicaoSecundaria: 'AVANCADO', pe: 'DIREITO', velocidade: 7, tecnica: 9, remate: 7, passe: 9, defesa: 5 },
    nuno: { posicao: 'DEFESA', pe: 'ESQUERDO', velocidade: 6, tecnica: 6, remate: 5, passe: 7, defesa: 9 },
    pedro: { posicao: 'GUARDA_REDES', pe: 'DIREITO', velocidade: 5, tecnica: 6, remate: 4, passe: 6, defesa: 8 },
    andre: { posicao: 'AVANCADO', pe: 'AMBOS', velocidade: 9, tecnica: 8, remate: 9, passe: 6, defesa: 4 },
    diogo: { alcunha: 'Didi', posicao: 'MEDIO', pe: 'DIREITO', velocidade: 7, tecnica: 7, remate: 6, passe: 8, defesa: 7 },
    tiago: { posicao: 'DEFESA', pe: 'DIREITO', velocidade: 6, tecnica: 5, remate: 5, passe: 6, defesa: 8 },
    hugo: { posicao: 'AVANCADO', pe: 'DIREITO', velocidade: 8, tecnica: 7, remate: 8, passe: 6, defesa: 4 },
    miguel: { posicao: 'GUARDA_REDES', pe: 'ESQUERDO', velocidade: 5, tecnica: 5, remate: 4, passe: 5, defesa: 7 },
    joao: { posicao: 'MEDIO', pe: 'DIREITO', velocidade: 7, tecnica: 6, remate: 6, passe: 7, defesa: 6 },
  };
  for (const [u, c] of Object.entries(cartas)) {
    const nome = pessoas.find(p => p[0] === u)[1];
    await postForm(api, `/jogadores/${id(u)}/editar`, `/jogadores/${id(u)}/editar`,
      { nome, alcunha: '', posicaoSecundaria: '', pe: '', velocidade: '', tecnica: '', remate: '', passe: '', defesa: '', ...c });
  }
  await postForm(api, '/jogos/novo', '/jogos/novo', { dataHora: '2026-11-12T21:00', local: 'Pavilhão da Escola Básica', vagas: '12' });
  for (const u of ['rui', 'nuno', 'pedro', 'andre', 'bruno', 'diogo', 'tiago', 'hugo', 'miguel', 'joao', 'ricardo']) {
    await postForm(api, '/jogos/1', '/jogos/1/gestao/jogadores', { utilizadorId: String(id(u)) });
  }
  await postForm(api, '/jogos/1', '/jogos/1/gestao/convidados', { nome: 'Zé (amigo do Nuno)' });
  await postForm(api, '/jogos/1', '/jogos/1/gestao/jogadores', { utilizadorId: String(id('sergio')) });
  await postForm(api, '/jogos/1', '/jogos/1/gestao/jogadores', { utilizadorId: String(id('luis')) });
  await postForm(api, '/jogos/1', '/jogos/1/gestao/inscricoes/5/remover', {});
  await postForm(api, '/admin/membros', `/admin/membros/${id('carlos')}/bloqueio`, { bloquear: 'true' });

  // ------------------------------------------------ browser
  const browser = await chromium.launch();
  const contexto = async () => {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: 'pt-PT' });
    await ctx.route('**/cdn.jsdelivr.net/**', r => r.fulfill({ status: 200, contentType: 'text/css', body: fs.readFileSync(BOOTSTRAP) }));
    await ctx.route('**/fonts.googleapis.com/**', r => r.fulfill({ status: 200, contentType: 'text/css', body: '' }));
    await ctx.route('**/unpkg.com/**', r => r.fulfill({ status: 200, contentType: 'application/javascript', body: '' }));
    return ctx.newPage();
  };
  const entrar = async (page, user, pass) => {
    await page.goto(BASE + '/login'); await page.fill('#username', user); await page.fill('#password', pass);
    await page.click('button[type=submit]'); await page.waitForURL(BASE + '/');
  };
  // Guarda o ecrã visível e, se a página for mais alta, a página inteira sem o rodapé fixo.
  const cap = async (page, url, nome, rects = {}) => {
    if (url) await page.goto(BASE + url);
    await page.waitForLoadState('networkidle');
    await page.setViewportSize({ width: 390, height: 844 });
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `${OUT}/${nome}-v.jpg`, type: 'jpeg', quality: 92 });
    const info = await page.evaluate(() => {
      const r = document.querySelector('.rodape');
      return { altura: document.documentElement.scrollHeight, rodape: r ? Math.round(r.getBoundingClientRect().height) : 0 };
    });
    info.rects = {};
    for (const [k, sel] of Object.entries(rects)) {
      const b = await page.locator(sel).first().boundingBox();
      if (b) info.rects[k] = b;
    }
    if (info.altura > 850) {
      await page.addStyleTag({ content: '.rodape{visibility:hidden}' });
      await page.setViewportSize({ width: 390, height: info.altura });
      await page.screenshot({ path: `${OUT}/${nome}-f.jpg`, type: 'jpeg', quality: 92 });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.reload(); await page.waitForLoadState('networkidle');
      info.full = true;
    }
    meta[nome] = info;
    console.log(nome, info.altura);
  };

  const adm = await contexto(); await entrar(adm, 'admin', 'admin123');
  const rui = await contexto(); await entrar(rui, 'rui', 'segredo123');
  const visitante = await contexto();

  // Passo 1: convite e conta
  await adm.goto(BASE + '/admin/convites'); await adm.click('button[type=submit]');
  await cap(adm, null, 'convite');
  await cap(visitante, `/registo?convite=${token}`, 'registo');
  await cap(rui, '/', 'inicio');
  // Passo 2: carta
  await cap(rui, `/jogadores/${id('rui')}/editar`, 'editar');
  await cap(rui, '/perfil', 'perfil');
  // Passo 3: convocatória
  await cap(adm, '/jogos/1', 'conv-org', { fechar: 'form[action$="/gestao/fechar"] button' });
  await cap(rui, '/jogos/1', 'conv-jogador');
  // Passo 4: equipas
  await adm.goto(BASE + '/jogos/1'); await adm.click('form[action$="/gestao/fechar"] button'); await adm.waitForLoadState('networkidle');
  await adm.goto(BASE + '/jogos/1/equipas'); await adm.click('form[action$="/gestao/equipas/gerar"] button'); await adm.waitForLoadState('networkidle');
  await cap(adm, null, 'equipas', { publicar: 'form[action$="/gestao/equipas/publicar"] button' });
  await adm.click('form[action$="/gestao/equipas/publicar"] button'); await adm.waitForLoadState('networkidle');
  await cap(rui, '/jogos/1', 'conv-equipas');
  // Passo 5: registo do jogo
  await cap(adm, '/jogos/1/registo', 'registo-inicio', { golo: 'a[href*="golo=COLETES"], button:has-text("+ Golo")' });
  await adm.goto(BASE + '/jogos/1/registo?golo=COLETES');
  await cap(adm, null, 'quem-marcou');
  const golo = async (equipa, marcador, assistencia) => {
    await adm.goto(`${BASE}/jogos/1/registo?golo=${equipa}`);
    if (assistencia !== undefined) await adm.selectOption('#assistencia', { index: assistencia });
    if (marcador === null) await adm.click('button[name=marcador][value=""]');
    else await adm.locator('button.escolha-marcador').nth(marcador).click();
    await adm.waitForLoadState('networkidle');
  };
  await golo('COLETES', 0, 2); await golo('SEM_COLETES', 1); await golo('COLETES', 0); await golo('COLETES', 3, 1); await golo('SEM_COLETES', null);
  await cap(adm, '/jogos/1/registo', 'registo-golos', { fechar: 'form[action$="/gestao/registo/fechar"] button' });
  await cap(rui, '/jogos/1', 'conv-resultado');
  // Passo 6: votação
  await adm.click('form[action$="/gestao/registo/fechar"] button').catch(async () => {
    await adm.goto(BASE + '/jogos/1/registo'); await adm.click('form[action$="/gestao/registo/fechar"] button'); });
  await adm.waitForLoadState('networkidle');
  await rui.goto(BASE + '/jogos/1/votar');
  await rui.locator('label.opcao-voto', { hasText: 'Didi' }).locator('input').check();
  await rui.locator('select.form-select').nth(0).selectOption({ label: 'Pedro Alves' });
  await rui.waitForTimeout(200);
  // a captura recarrega a página; tira-se à mão para manter as escolhas
  await rui.screenshot({ path: `${OUT}/votar-v.jpg`, type: 'jpeg', quality: 92 });
  { const altura = await rui.evaluate(() => document.documentElement.scrollHeight);
    const rodape = await rui.evaluate(() => Math.round(document.querySelector('.rodape').getBoundingClientRect().height));
    await rui.addStyleTag({ content: '.rodape{visibility:hidden}' });
    await rui.setViewportSize({ width: 390, height: altura });
    await rui.screenshot({ path: `${OUT}/votar-f.jpg`, type: 'jpeg', quality: 92 });
    await rui.setViewportSize({ width: 390, height: 844 });
    await rui.evaluate(() => document.querySelectorAll('style').forEach(s => { if (s.textContent.includes('.rodape{visibility')) s.remove(); }));
    meta['votar'] = { altura, rodape, rects: {}, full: true }; console.log('votar', altura); }
  await rui.click('button[type=submit]:has-text("Votar")'); await rui.waitForLoadState('networkidle');
  const votos = [
    ['pedro', 'Maestro', [[0, 'Nuno Silva'], [2, 'Tiago Rocha']]],
    ['andre', 'Maestro', [[0, 'Pedro Alves'], [5, 'Hugo Marques']]],
    ['tiago', 'Didi', [[0, 'Pedro Alves']]],
    ['diogo', 'Maestro', [[4, 'Hugo Marques']]],
    ['hugo', 'Maestro', [[0, 'Pedro Alves'], [5, 'André Santos']]],
  ];
  for (const [u, mvp, premios] of votos) {
    const p = await contexto(); await entrar(p, u, 'segredo123');
    await p.goto(BASE + '/jogos/1/votar');
    await p.locator('label.opcao-voto', { hasText: mvp }).locator('input').check();
    for (const [i, nome] of premios) await p.locator('select.form-select').nth(i).selectOption({ label: nome });
    await p.click('button[type=submit]:has-text("Votar"), button[type=submit]:has-text("Guardar")');
    await p.waitForLoadState('networkidle'); await p.context().close();
  }
  await cap(rui, '/jogos/1', 'votacao-aberta');
  await adm.goto(BASE + '/jogos/1'); await adm.click('form[action$="/gestao/votacao/fechar"] button'); await adm.waitForLoadState('networkidle');
  // Passo 7: resultados
  await cap(rui, '/jogos/1', 'resumo');
  await cap(rui, '/estatisticas', 'estatisticas');
  await cap(rui, `/jogadores/${id('rui')}`, 'carta-numeros');
  // Jogo seguinte: inscrição pelo próprio jogador
  await postForm(api, '/jogos/novo', '/jogos/novo', { dataHora: '2026-11-19T21:00', local: 'Pavilhão da Escola Básica', vagas: '12' });
  for (const u of ['nuno', 'pedro', 'andre', 'diogo', 'tiago', 'hugo', 'joao']) {
    await postForm(api, '/jogos/2', '/jogos/2/gestao/jogadores', { utilizadorId: String(id(u)) });
  }
  await cap(rui, '/jogos/2', 'conv2-antes', { inscrever: 'form[action$="/inscricao"] button' });
  await rui.click('form[action$="/inscricao"] button'); await rui.waitForLoadState('networkidle');
  await cap(rui, null, 'conv2-depois', { cancelar: 'form[action$="/inscricao/cancelar"] button' });
  await cap(rui, '/', 'inicio2');
  // Jogo cancelado
  await postForm(api, '/jogos/novo', '/jogos/novo', { dataHora: '2026-11-26T21:00', local: 'Pavilhão da Escola Básica', vagas: '12' });
  await adm.goto(BASE + '/jogos/3');
  await adm.locator('details summary:has-text("Cancelar jogo")').click();
  await adm.click('form[action$="/gestao/cancelar"] button'); await adm.waitForLoadState('networkidle');
  await cap(rui, '/jogos/3', 'cancelado');
  // Password esquecida
  await adm.goto(BASE + `/jogadores/${id('nuno')}`);
  await adm.click('form[action$="/link-password"] button'); await adm.waitForLoadState('networkidle');
  await cap(adm, null, 'link-password');
  const link = await adm.inputValue('#link');
  await cap(visitante, link.replace(BASE, ''), 'nova-password');

  fs.writeFileSync(`${OUT}/meta.json`, JSON.stringify(meta, null, 1));
  await browser.close(); console.log('ok');
})().catch(e => { console.error(e); process.exit(1); });
