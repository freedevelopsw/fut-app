// Guião do vídeo: cenas, legendas no ecrã e o que o telefone mostra.
const P = n => `<span>Passo ${n}</span><span class="n">de 7</span>`;
module.exports = [
  { id: '00-intro', tipo: 'intro', dur: 5.5, ecra: 'inicio2' },
  { id: '01-papeis', tipo: 'papeis', dur: 9, pill: 'Antes de começar', titulo: 'Quem faz o quê',
    legenda: 'Há três papéis. O Organizador e o Admin também jogam e votam como os outros.' },
  { id: '02-conta', dur: 12.5, pill: P(1), titulo: 'Criar conta', beats: [
    { t0: 0, dur: 4.2, quem: 'Admin', ecra: 'convite', texto: 'O Admin partilha no grupo um <b>link de convite</b>.' },
    { t0: 4.2, dur: 4.2, quem: 'Tu', ecra: 'registo', texto: 'Abres o link e crias a conta: nome, utilizador e password. <b>Só fazes isto uma vez.</b>' },
    { t0: 8.4, dur: 4.1, quem: 'Tu', ecra: 'inicio', texto: 'Depois o telemóvel lembra-se de ti. Dica: adiciona a FutApp ao <b>ecrã principal</b>.' } ] },
  { id: '03-carta', dur: 10.5, pill: P(2), titulo: 'A tua carta', beats: [
    { t0: 0, dur: 5.6, quem: 'Tu', ecra: 'editar', scroll: [0, 300], texto: 'Em <b>Perfil</b> preenches a tua carta: alcunha, posição, pé e atributos de 1 a 10.' },
    { t0: 5.6, dur: 4.9, quem: 'Todos', ecra: 'perfil', texto: 'Só o nome é obrigatório. As cartas ajudam a fazer <b>equipas equilibradas</b>.' } ] },
  { id: '04-convocatoria', dur: 19.5, pill: P(3), titulo: 'Convocatória', beats: [
    { t0: 0, dur: 4.6, quem: 'Organizador', ecra: 'conv-org', scroll: [0, 260], texto: 'O Organizador cria o jogo e partilha a <b>convocatória no WhatsApp</b>.' },
    { t0: 4.6, dur: 5, quem: 'Tu', ecra: 'conv2-antes', depois: 'conv2-depois', trocaEm: 2.9, toque: { rect: 'inscrever', t: 2.6 }, texto: 'Abres o link e carregas em <b>«Inscrever-me»</b>. Um toque e estás dentro.' },
    { t0: 9.6, dur: 5.2, quem: 'Todos', ecra: 'conv-jogador', scroll: [0, 483], texto: 'São <b>12 vagas</b>. A partir do 13.º ficas em lista de espera e sobes sozinho se alguém sair.' },
    { t0: 14.8, dur: 4.7, quem: 'Tu', ecra: 'conv2-depois', destaque: { rect: 'cancelar', t: 0.6 }, texto: 'Não podes ir? <b>«Cancelar inscrição»</b>. Fica registado como desistência.' } ] },
  { id: '05-equipas', dur: 12.5, pill: P(4), titulo: 'Equipas', beats: [
    { t0: 0, dur: 6.4, quem: 'Organizador', ecra: 'equipas', destaque: { rect: 'publicar', t: 4.2 }, texto: 'O Organizador fecha a convocatória e a app gera as equipas: <b>Coletes contra Sem coletes</b>.' },
    { t0: 6.4, dur: 6.1, quem: 'Todos', ecra: 'conv-equipas', scroll: [0, 340], texto: 'Depois de publicadas, as equipas aparecem na <b>convocatória de todos</b>.' } ] },
  { id: '06-jogo', dur: 13, pill: P(5), titulo: 'O jogo', beats: [
    { t0: 0, dur: 6.6, quem: 'Organizador', ecra: 'registo-inicio', depois: 'quem-marcou', trocaEm: 2.4, toque: { rect: 'golo', t: 2.1 }, texto: 'O Organizador regista os golos: <b>«+ Golo»</b>, quem marcou e, se quiser, a assistência.' },
    { t0: 6.6, dur: 6.4, quem: 'Todos', ecra: 'conv-resultado', scroll: [0, 300], texto: 'O <b>resultado e os marcadores</b> aparecem logo a todos.' } ] },
  { id: '07-votacao', dur: 17.5, pill: P(6), titulo: 'Votação', beats: [
    { t0: 0, dur: 6, quem: 'Tu', ecra: 'votar', scroll: [0, 640], texto: 'Quando o jogo fecha, abre a votação. Escolhes o <b>MVP</b> e, se quiseres, os prémios brincadeira.' },
    { t0: 6, dur: 5.4, quem: 'Tu', ecra: 'votar', scroll: [640, 1400], scrollT: [0.2, 4.8], texto: 'Votas em quem quiseres, <b>menos em ti</b>. Até a votação fechar, podes mudar o voto.' },
    { t0: 11.4, dur: 6.1, quem: 'Todos', ecra: 'votacao-aberta', scroll: [0, 280], texto: 'O voto é <b>secreto</b> (só o Admin vê). Fecha quando todos votarem ou quando o Organizador fechar.' } ] },
  { id: '08-resultados', dur: 16.5, pill: P(7), titulo: 'Resultados', beats: [
    { t0: 0, dur: 6, quem: 'Todos', ecra: 'resumo', scroll: [0, 330], scrollT: [1.6, 5.4], texto: 'No fim: <b>MVP, prémios e resumo do jogo</b>, prontos a partilhar no WhatsApp.' },
    { t0: 6, dur: 6, quem: 'Todos', ecra: 'estatisticas', scroll: [0, 1100], texto: 'As <b>estatísticas</b> vão somando: golos, MVPs, assistências, vitórias. Os convidados também contam.' },
    { t0: 12, dur: 4.5, quem: 'Tu', ecra: 'carta-numeros', texto: 'E a tua carta mostra <b>os teus números</b>.' } ] },
  { id: '09-bom-saber', dur: 11, pill: 'Bom saber', titulo: 'E se…', beats: [
    { t0: 0, dur: 5.5, quem: 'Admin', ecra: 'nova-password', texto: '<b>Esqueceste a password?</b> Pede ao Admin um link para escolheres uma nova.' },
    { t0: 5.5, dur: 5.5, quem: 'Todos', ecra: 'cancelado', texto: '<b>Jogo cancelado?</b> Aparece com o motivo e não conta para as estatísticas.' } ] },
  { id: '10-fim', tipo: 'fim', dur: 8 },
];
