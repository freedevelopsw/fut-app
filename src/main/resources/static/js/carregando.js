// Indicador de carregamento: uma bola a saltar ao centro do ecrã sempre que o utilizador está à espera de uma
// resposta do servidor.
// - Pedidos htmx (navegação e formulários com hx-boost): aparece ao fim de 250 ms (os pedidos rápidos não piscam)
//   e fica até à resposta, também em erro ou timeout.
// - O botão ou link carregado fica bloqueado até à resposta (evita registar um golo ou um voto duas vezes).
// - Formulários fora do htmx (hx-boost="false", por exemplo "Sair da conta") também o mostram.
(function () {
  var ATRASO = 250;
  var BOLA = '<svg class="carregando-bola" viewBox="0 0 48 48" width="56" height="56" aria-hidden="true"><defs><clipPath id="carregando-clip"><circle cx="24" cy="24" r="21"/></clipPath></defs><g clip-path="url(#carregando-clip)" stroke="#15191E" stroke-width="1.6" stroke-linecap="round"><circle cx="24" cy="24" r="21" fill="#fff"/><polygon points="24.00,17.00 30.66,21.84 28.11,29.66 19.89,29.66 17.34,21.84" fill="#15191E"/><polygon points="24.00,10.50 17.82,6.01 20.18,-1.26 27.82,-1.26 30.18,6.01" fill="#15191E"/><line x1="24.00" y1="17.00" x2="24.00" y2="10.50"/><polygon points="36.84,19.83 39.20,12.56 46.84,12.56 49.20,19.83 43.02,24.32" fill="#15191E"/><line x1="30.66" y1="21.84" x2="36.84" y2="19.83"/><polygon points="31.94,34.92 39.58,34.92 41.94,42.19 35.76,46.68 29.57,42.19" fill="#15191E"/><line x1="28.11" y1="29.66" x2="31.94" y2="34.92"/><polygon points="16.06,34.92 18.43,42.19 12.24,46.68 6.06,42.19 8.42,34.92" fill="#15191E"/><line x1="19.89" y1="29.66" x2="16.06" y2="34.92"/><polygon points="11.16,19.83 4.98,24.32 -1.20,19.83 1.16,12.56 8.80,12.56" fill="#15191E"/><line x1="17.34" y1="21.84" x2="11.16" y2="19.83"/></g><circle cx="24" cy="24" r="21" fill="none" stroke="#15191E" stroke-width="2"/></svg>';
  var pendentes = 0, temporizador = null;

  function elemento() {
    var e = document.getElementById('carregando');
    if (!e) {
      // o hx-boost troca o <body>, por isso o elemento é criado de novo quando falta
      e = document.createElement('div');
      e.id = 'carregando';
      e.setAttribute('role', 'status');
      e.setAttribute('aria-live', 'polite');
      e.innerHTML = '<div class="carregando-centro"><div class="carregando-salto">' + BOLA + '</div>'
        + '<div class="carregando-sombra"></div></div><span class="visually-hidden">A carregar…</span>';
      document.body.appendChild(e);
    }
    return e;
  }

  function mostrar() {
    pendentes++;
    if (pendentes > 1) return;
    clearTimeout(temporizador);
    temporizador = setTimeout(function () { elemento().classList.add('ativo'); }, ATRASO);
  }

  function esconder() {
    pendentes = Math.max(0, pendentes - 1);
    if (pendentes > 0) return;
    clearTimeout(temporizador);
    var e = document.getElementById('carregando');
    if (e) e.classList.remove('ativo');
  }

  function repor() {
    pendentes = 1;
    esconder();
  }

  document.addEventListener('htmx:beforeRequest', mostrar);
  document.addEventListener('htmx:afterRequest', esconder); // também em erro e timeout
  // depois de o hx-boost trocar a página, garante que nada fica preso
  document.addEventListener('htmx:afterSettle', function () { if (pendentes === 0) repor(); });

  // formulários que não passam pelo htmx: a página seguinte substitui esta
  document.addEventListener('submit', function (ev) {
    var form = ev.target;
    if (!ev.defaultPrevented && form.closest('[hx-boost="false"]') && form.getAttribute('target') !== '_blank') {
      mostrar();
    }
  });
  // voltar atrás com a cache do browser: a página volta sem pedido, não pode ficar o indicador
  window.addEventListener('pageshow', repor);
})();
