package pt.futapp.jogo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final GoloRepository golos;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Formatos fmt;

    public JogoService(JogoRepository jogos, InscricaoRepository inscricoes, GoloRepository golos,
                       UtilizadorRepository utilizadores, ApplicationEventPublisher eventos, Clock clock, Formatos fmt) {
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.golos = golos;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.clock = clock;
        this.fmt = fmt;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<JogoResumo> proximosJogos() {
        return resumos(jogos.findByEstadoNotInOrderByDataHoraAsc(List.of(EstadoJogo.FECHADO, EstadoJogo.CANCELADO)));
    }

    /** Jogos fechados ou cancelados do mês atual (e cancelados com data futura). Os mais antigos estão no histórico. */
    @Transactional(readOnly = true)
    public List<JogoResumo> jogosAnteriores() {
        LocalDateTime inicioMes = agora().toLocalDate().withDayOfMonth(1).atStartOfDay();
        return resumos(jogos.findByEstadoInAndDataHoraGreaterThanEqualOrderByDataHoraDesc(
            List.of(EstadoJogo.FECHADO, EstadoJogo.CANCELADO), inicioMes));
    }

    /**
     * Histórico (Organizador/Admin): jogos fechados ou cancelados do ano corrente (de 1 de janeiro a 31 de dezembro;
     * um jogo cancelado do ano seguinte não entra), por mês, do mais recente para trás.
     * Três consultas no total, seja qual for o número de jogos.
     */
    @Transactional(readOnly = true)
    public List<MesHistorico> historico(String username) {
        exigirGestor(carregarUtilizador(username));
        LocalDateTime inicioAno = agora().toLocalDate().withDayOfYear(1).atStartOfDay();
        List<Jogo> doAno = jogos.findByEstadoInAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraDesc(
            List.of(EstadoJogo.FECHADO, EstadoJogo.CANCELADO), inicioAno, inicioAno.plusYears(1));
        List<JogoResumo> resumos = resumos(doAno);
        Map<String, List<JogoResumo>> porMes = new LinkedHashMap<>();
        for (int i = 0; i < doAno.size(); i++) {
            porMes.computeIfAbsent(fmt.mes(doAno.get(i).getDataHora()), m -> new ArrayList<>()).add(resumos.get(i));
        }
        return porMes.entrySet().stream().map(e -> new MesHistorico(e.getKey(), e.getValue())).toList();
    }

    /** Um mês do histórico, por exemplo "Novembro". */
    public record MesHistorico(String mes, List<JogoResumo> jogos) {
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
                ins.getConta() != null ? ins.getConta().getId() : null, i + 1, ins.getNomeVisivel(),
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
            eu.ehGestor(), eu.ehGestor() && jogo.permiteEquipas(), disponiveis, whatsapp,
            jogo.getMotivoCancelamento(),
            jogo.cancelado() ? fmt.dataHora(jogo.getCanceladoEm()) : null,
            jogo.getCanceladoPor() != null ? jogo.getCanceladoPor().getNomeVisivel() : null,
            jogo.cancelado() ? MensagemWhatsApp.url("O jogo de " + dia + ", " + hora
                + (jogo.temLocal() ? " (" + jogo.getLocal() + ")" : "") + " foi cancelado.\nMotivo: " + jogo.getMotivoCancelamento() + "\n\n" + link) : null);
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
        Jogo jogo = jogos.save(new Jogo(form.getDataHora(), form.getLocal(), form.getVagas(), ator, agora()));
        audit(ator, Acao.CRIACAO_JOGO, jogo, jogo.getId(),
            "Jogo criado para " + fmt.dia(jogo.getDataHora()) + ", " + fmt.hora(jogo.getDataHora())
                + (jogo.temLocal() ? " em " + jogo.getLocal() : "") + " (" + jogo.getVagas() + " vagas)");
        return jogo.getId();
    }

    /** Remove qualquer inscrição, a qualquer momento. Fica registada como desistência. */
    @Transactional
    public void removerInscricao(Long jogoId, Long inscricaoId, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        exigirListaEditavel(jogo);
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
        exigirListaEditavel(jogo);
        Utilizador jogador = utilizadores.findMembro(utilizadorId)
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
        exigirListaEditavel(jogo);
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

    /**
     * Retira um membro removido dos jogos que ainda não se jogaram (lista ainda editável: convocatória aberta ou
     * fechada, equipas publicadas), para não ficar a ocupar uma vaga. Cada saída fica registada como desistência e
     * quem está em espera sobe. Depois, as inscrições dele nesses jogos são apagadas: não são histórico.
     */
    @Transactional
    public void retirarDosJogosPorJogar(Utilizador jogador, Utilizador ator) {
        List<Inscricao> todas = inscricoes.findByJogador(jogador);
        for (Inscricao ins : todas) {
            if (ins.getJogo().permiteEquipas() && ins.getEstado() == EstadoInscricao.ATIVA) {
                cancelar(ins.getJogo(), ins, ator);
            }
        }
        inscricoes.deleteAll(todas.stream().filter(i -> i.getJogo().permiteEquipas()).toList());
        inscricoes.flush();
    }

    /** Motivo usado quando não é indicado nenhum. */
    public static final String MOTIVO_CANCELAMENTO_OMISSAO = "Falta de jogadores";

    /** Cancela o jogo. O motivo é sempre guardado (por omissão, falta de jogadores). */
    @Transactional
    public void cancelar(Long jogoId, String motivo, String username) {
        Utilizador ator = carregarUtilizador(username);
        exigirGestor(ator);
        Jogo jogo = carregarJogo(jogoId);
        String m = motivo == null || motivo.isBlank() ? MOTIVO_CANCELAMENTO_OMISSAO : motivo.trim();
        if (m.length() > 200) {
            throw new OperacaoInvalidaException("O motivo tem no máximo 200 caracteres.");
        }
        jogo.cancelar(m, ator, agora());
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.CANCELAMENTO_JOGO, jogo.getId(), "JOGO", jogo.getId(),
            "Jogo de " + fmt.dia(jogo.getDataHora()) + " cancelado. Motivo: " + m));
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

    /** Depois de o registo do jogo começar, a lista fica fixa (quem não veio marca-se como falta no registo). */
    private void exigirListaEditavel(Jogo jogo) {
        if (!jogo.permiteEquipas()) {
            throw new OperacaoInvalidaException("O jogo já está a ser registado: a lista já não muda. Marca as faltas no registo do jogo.");
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

    /** Resumos de vários jogos, pela mesma ordem, com as contagens em duas consultas (e não três por jogo). */
    private List<JogoResumo> resumos(List<Jogo> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> ativos = new HashMap<>();
        for (Object[] linha : inscricoes.contarPorJogo(lista, EstadoInscricao.ATIVA)) {
            ativos.put((Long) linha[0], (Long) linha[1]);
        }
        Map<String, Long> golosPorEquipa = new HashMap<>();
        for (Object[] linha : golos.contarPorJogoEEquipa(lista)) {
            String equipa = linha[1] instanceof Equipa e ? e.name() : String.valueOf(linha[1]);
            golosPorEquipa.put(linha[0] + ":" + equipa, (Long) linha[2]);
        }
        return lista.stream().map(jogo -> {
            long inscritos = ativos.getOrDefault(jogo.getId(), 0L);
            String resultado = jogo.temResultado()
                ? golosPorEquipa.getOrDefault(jogo.getId() + ":" + Equipa.COLETES.name(), 0L) + "–"
                    + golosPorEquipa.getOrDefault(jogo.getId() + ":" + Equipa.SEM_COLETES.name(), 0L)
                : null;
            return new JogoResumo(jogo.getId(), fmt.dia(jogo.getDataHora()), fmt.hora(jogo.getDataHora()),
                jogo.getLocal(), jogo.getEstado().getDescricao(), jogo.cancelado(), jogo.convocatoriaAberta(),
                Math.min(inscritos, jogo.getVagas()), jogo.getVagas(), resultado);
        }).toList();
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
