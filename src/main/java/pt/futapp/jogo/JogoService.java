package pt.futapp.jogo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.Nomes;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.web.Formatos;

/**
 * Regras da Fase 1 (Convocatória): inscrição, lista de espera, cancelamentos e fecho.
 * Todas as permissões são verificadas aqui, além da configuração de segurança.
 */
@Service
public class JogoService {

    private final JogoRepository jogos;
    private final InscricaoRepository inscricoes;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Formatos fmt;

    public JogoService(JogoRepository jogos, InscricaoRepository inscricoes, UtilizadorRepository utilizadores,
                       ApplicationEventPublisher eventos, Clock clock, Formatos fmt) {
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.clock = clock;
        this.fmt = fmt;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<JogoResumo> proximosJogos() {
        return jogos.findByEstadoNotOrderByDataHoraAsc(EstadoJogo.FECHADO).stream().map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public List<JogoResumo> jogosAnteriores() {
        return jogos.findTop10ByEstadoOrderByDataHoraDesc(EstadoJogo.FECHADO).stream().map(this::resumo).toList();
    }

    @Transactional(readOnly = true)
    public Convocatoria convocatoria(Long jogoId, String username, String urlBase) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);

        List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
        List<Convocatoria.Linha> inscritos = new ArrayList<>();
        List<Convocatoria.Linha> espera = new ArrayList<>();
        Integer minhaPosicao = null;
        Set<Long> jaInscritos = new HashSet<>();
        for (int i = 0; i < ativas.size(); i++) {
            Inscricao ins = ativas.get(i);
            boolean minha = ins.ehDe(eu);
            if (minha) {
                minhaPosicao = i + 1;
            }
            if (ins.getJogador() != null) {
                jaInscritos.add(ins.getJogador().getId());
            }
            Convocatoria.Linha linha = new Convocatoria.Linha(ins.getId(),
                ins.getJogador() != null ? ins.getJogador().getId() : null, i + 1, ins.getNomeVisivel(),
                Nomes.iniciais(ins.getNomeVisivel()), ins.isConvidado(), fmt.curto(ins.getInscritoEm()), minha);
            (i < jogo.getVagas() ? inscritos : espera).add(linha);
        }

        List<Convocatoria.Desistencia> desistencias = inscricoes
            .findByJogoAndEstadoOrderByCanceladoEmDesc(jogo, EstadoInscricao.CANCELADA).stream()
            .map(d -> new Convocatoria.Desistencia(d.getNomeVisivel(), fmt.curto(d.getCanceladoEm()),
                d.getCanceladoPor() != null && !d.ehDe(d.getCanceladoPor()) ? d.getCanceladoPor().getNome() : null))
            .toList();

        List<Convocatoria.MembroOpcao> disponiveis = eu.ehGestor()
            ? utilizadores.findByBloqueadoFalseOrderByNomeAsc().stream()
                .filter(u -> !jaInscritos.contains(u.getId()))
                .map(u -> new Convocatoria.MembroOpcao(u.getId(), u.getNomeVisivel()))
                .toList()
            : List.of();

        String dia = fmt.dia(jogo.getDataHora());
        String hora = fmt.hora(jogo.getDataHora());
        String link = urlBase + "/jogos/" + jogo.getId();
        String whatsapp = MensagemWhatsApp.url(
            MensagemWhatsApp.texto(dia, hora, jogo.getLocal(), jogo.getVagas(), inscritos, espera, link));

        boolean aberta = jogo.convocatoriaAberta();
        return new Convocatoria(jogo.getId(), dia, hora, jogo.getLocal(), jogo.getVagas(), jogo.getEstado(),
            inscritos, espera, desistencias, minhaPosicao,
            aberta && minhaPosicao == null, aberta && minhaPosicao != null,
            eu.ehGestor(), disponiveis, whatsapp);
    }

    // ---------------------------------------------------------------- ações do jogador

    @Transactional
    public void inscrever(Long jogoId, String username) {
        Jogo jogo = carregarJogo(jogoId);
        exigirConvocatoriaAberta(jogo);
        Utilizador eu = carregarUtilizador(username);
        if (eu.isBloqueado()) {
            throw new OperacaoInvalidaException("A tua conta está bloqueada.");
        }
        if (inscricoes.existsByJogoAndJogadorAndEstado(jogo, eu, EstadoInscricao.ATIVA)) {
            throw new OperacaoInvalidaException("Já estás inscrito neste jogo.");
        }
        Inscricao ins = inscricoes.save(Inscricao.deJogador(jogo, eu, agora()));
        long posicao = inscricoes.countByJogoAndEstado(jogo, EstadoInscricao.ATIVA);
        audit(eu, Acao.INSCRICAO_JOGO, jogo, ins.getId(), eu.getNome() + " " + descricaoPosicao(jogo, posicao));
    }

    /** O jogador cancela a própria inscrição: só enquanto a convocatória está aberta. */
    @Transactional
    public void cancelarPropria(Long jogoId, String username) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);
        if (!jogo.convocatoriaAberta()) {
            throw new OperacaoInvalidaException(
                "A convocatória já fechou. Fala com o organizador para saíres da lista.");
        }
        Inscricao ins = inscricoes.findFirstByJogoAndJogadorAndEstado(jogo, eu, EstadoInscricao.ATIVA)
            .orElseThrow(() -> new OperacaoInvalidaException("Não estás inscrito neste jogo."));
        cancelar(jogo, ins, eu);
    }

    // ---------------------------------------------------------------- ações do Organizador / Admin

    @Transactional
    public Long criar(NovoJogoForm form, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = jogos.save(new Jogo(form.getDataHora(), form.getLocal().trim(), form.getVagas(), ator, agora()));
        audit(ator, Acao.CRIACAO_JOGO, jogo, jogo.getId(),
            "Jogo criado para " + fmt.dia(jogo.getDataHora()) + ", " + fmt.hora(jogo.getDataHora())
                + " em " + jogo.getLocal() + " (" + jogo.getVagas() + " vagas)");
        return jogo.getId();
    }

    /** Remove qualquer inscrição, a qualquer momento. Fica registada como desistência. */
    @Transactional
    public void removerInscricao(Long jogoId, Long inscricaoId, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        Inscricao ins = inscricoes.findById(inscricaoId)
            .filter(i -> i.getJogo().getId().equals(jogo.getId()))
            .filter(i -> i.getEstado() == EstadoInscricao.ATIVA)
            .orElseThrow(() -> new OperacaoInvalidaException("Essa inscrição já não existe."));
        cancelar(jogo, ins, ator);
    }

    @Transactional
    public void adicionarJogador(Long jogoId, Long utilizadorId, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        Utilizador jogador = utilizadores.findById(utilizadorId)
            .orElseThrow(() -> new OperacaoInvalidaException("Jogador não encontrado."));
        if (jogador.isBloqueado()) {
            throw new OperacaoInvalidaException(jogador.getNome() + " está bloqueado.");
        }
        if (inscricoes.existsByJogoAndJogadorAndEstado(jogo, jogador, EstadoInscricao.ATIVA)) {
            throw new OperacaoInvalidaException(jogador.getNome() + " já está inscrito.");
        }
        Inscricao ins = inscricoes.save(Inscricao.deJogador(jogo, jogador, agora()));
        long posicao = inscricoes.countByJogoAndEstado(jogo, EstadoInscricao.ATIVA);
        audit(ator, Acao.INSCRICAO_JOGO, jogo, ins.getId(),
            jogador.getNome() + " " + descricaoPosicao(jogo, posicao) + " (inscrito por " + ator.getNome() + ")");
    }

    @Transactional
    public void adicionarConvidado(Long jogoId, String nome, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        String limpo = nome == null ? "" : nome.trim();
        if (limpo.isEmpty() || limpo.length() > 60) {
            throw new OperacaoInvalidaException("Indica o nome do convidado (até 60 caracteres).");
        }
        Inscricao ins = inscricoes.save(Inscricao.deConvidado(jogo, limpo, agora()));
        long posicao = inscricoes.countByJogoAndEstado(jogo, EstadoInscricao.ATIVA);
        audit(ator, Acao.ADICAO_CONVIDADO, jogo, ins.getId(),
            "Convidado " + limpo + " " + descricaoPosicao(jogo, posicao));
    }

    @Transactional
    public void fecharConvocatoria(Long jogoId, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        jogo.fecharConvocatoria();
        long inscritos = Math.min(jogo.getVagas(), inscricoes.countByJogoAndEstado(jogo, EstadoInscricao.ATIVA));
        audit(ator, Acao.FECHO_CONVOCATORIA, jogo, jogo.getId(),
            "Convocatória fechada com " + inscritos + "/" + jogo.getVagas() + " inscritos");
    }

    /**
     * Retira um jogador de todas as convocatórias ainda abertas (usado quando o Admin o bloqueia).
     * Cada saída fica registada como desistência, e quem estava em espera sobe.
     *
     * @return número de convocatórias de onde saiu
     */
    @Transactional
    public int removerDeConvocatoriasAbertas(Utilizador jogador, Utilizador ator) {
        List<Inscricao> abertas = inscricoes.findByJogadorAndEstadoAndJogo_Estado(
            jogador, EstadoInscricao.ATIVA, EstadoJogo.CONVOCATORIA_ABERTA);
        for (Inscricao ins : abertas) {
            cancelar(ins.getJogo(), ins, ator);
        }
        return abertas.size();
    }

    // ---------------------------------------------------------------- regras internas

    /**
     * Cancela uma inscrição. Se a pessoa estava entre os inscritos e há lista de espera,
     * o primeiro da espera sobe automaticamente (a posição resulta da ordem de inscrição).
     */
    private void cancelar(Jogo jogo, Inscricao ins, Utilizador ator) {
        List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
        int indice = -1;
        for (int i = 0; i < ativas.size(); i++) {
            if (ativas.get(i).getId().equals(ins.getId())) {
                indice = i;
                break;
            }
        }
        Inscricao promovido = indice >= 0 && indice < jogo.getVagas() && ativas.size() > jogo.getVagas()
            ? ativas.get(jogo.getVagas())
            : null;

        ins.cancelar(ator, agora());
        String texto = "Desistência de " + ins.getNomeVisivel()
            + (ins.ehDe(ator) ? "" : " (registada por " + ator.getNome() + ")");
        audit(ator, Acao.CANCELAMENTO_INSCRICAO, jogo, ins.getId(), texto);
        if (promovido != null) {
            audit(ator, Acao.PROMOCAO_LISTA_ESPERA, jogo, promovido.getId(),
                promovido.getNomeVisivel() + " passou da lista de espera para os inscritos");
        }
    }

    private void exigirConvocatoriaAberta(Jogo jogo) {
        if (!jogo.convocatoriaAberta()) {
            throw new OperacaoInvalidaException("A convocatória está fechada.");
        }
    }

    private void exigirGestor(Utilizador ator) {
        if (!ator.ehGestor()) {
            throw new OperacaoInvalidaException("Só o Organizador ou o Admin podem fazer isto.");
        }
    }

    private Jogo carregarJogo(Long id) {
        return jogos.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Jogo não encontrado."));
    }

    private Utilizador carregarUtilizador(String username) {
        return utilizadores.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
    }

    private JogoResumo resumo(Jogo jogo) {
        long ativos = inscricoes.countByJogoAndEstado(jogo, EstadoInscricao.ATIVA);
        return new JogoResumo(jogo.getId(), fmt.dia(jogo.getDataHora()), fmt.hora(jogo.getDataHora()),
            jogo.getLocal(), jogo.getEstado().getDescricao(), jogo.convocatoriaAberta(),
            Math.min(ativos, jogo.getVagas()), jogo.getVagas());
    }

    private static String descricaoPosicao(Jogo jogo, long posicao) {
        return posicao <= jogo.getVagas()
            ? "inscrito em " + posicao + "º lugar"
            : "em lista de espera (" + (posicao - jogo.getVagas()) + "º)";
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    private void audit(Utilizador ator, Acao acao, Jogo jogo, Long entidadeId, String detalhes) {
        String tipo = acao == Acao.CRIACAO_JOGO || acao == Acao.FECHO_CONVOCATORIA ? "JOGO" : "INSCRICAO";
        eventos.publishEvent(new AuditEvent(ator.getUsername(), acao, jogo.getId(), tipo, entidadeId, detalhes));
    }
}
