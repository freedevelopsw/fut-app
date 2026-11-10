package pt.futapp.jogo;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.web.Formatos;

/**
 * Fase 3 (Jogo e estatísticas): golos, resultado e faltas, registados pelo Organizador ou pelo Admin.
 * O registo pode ficar incompleto. Fechar o jogo abre a votação; enquanto a votação está aberta, o registo pode ser reaberto.
 */
@Service
public class RegistoService {

    private static final int MAX_GOLOS = 99;

    private final JogoRepository jogos;
    private final InscricaoRepository inscricoes;
    private final GoloRepository golos;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Formatos fmt;

    public RegistoService(JogoRepository jogos, InscricaoRepository inscricoes, GoloRepository golos,
                          UtilizadorRepository utilizadores, ApplicationEventPublisher eventos, Clock clock, Formatos fmt) {
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.golos = golos;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.clock = clock;
        this.fmt = fmt;
    }

    @Transactional(readOnly = true)
    public RegistoView vista(Long jogoId, String username) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);
        boolean gestor = eu.ehGestor() && !eu.isBloqueado();

        List<Golo> todos = golos.findByJogoOrderByIdAsc(jogo);
        List<RegistoView.GoloView> lista = new ArrayList<>();
        int c = 0;
        int s = 0;
        for (Golo g : todos) {
            if (g.getEquipa() == Equipa.COLETES) {
                c++;
            } else {
                s++;
            }
            lista.add(new RegistoView.GoloView(g.getId(), g.getEquipa(),
                g.getMarcador() != null ? g.getMarcador().getNomeVisivel() : null,
                g.getAssistencia() != null ? g.getAssistencia().getNomeVisivel() : null,
                c + "–" + s));
        }
        Collections.reverse(lista);

        List<RegistoView.Presenca> coletes = new ArrayList<>();
        List<RegistoView.Presenca> semColetes = new ArrayList<>();
        int faltas = 0;
        for (Inscricao ins : comEquipa(jogo)) {
            RegistoView.Presenca p = new RegistoView.Presenca(ins.getId(), ins.getNomeVisivel(), ins.getEquipa(),
                ins.isFaltou(), ins.isConvidado());
            (ins.getEquipa() == Equipa.COLETES ? coletes : semColetes).add(p);
            if (ins.isFaltou()) {
                faltas++;
            }
        }

        String motivo = null;
        if (!jogo.permiteRegisto()) {
            motivo = switch (jogo.getEstado()) {
                case VOTACAO_ABERTA, FECHADO -> "O jogo está fechado.";
                default -> "Publica primeiro as equipas para registares o jogo.";
            };
        }
        return new RegistoView(jogo.getId(), fmt.dia(jogo.getDataHora()), fmt.hora(jogo.getDataHora()), jogo.getEstado(),
            gestor, gestor && jogo.permiteRegisto(), gestor && jogo.getEstado() == EstadoJogo.VOTACAO_ABERTA,
            jogo.temResultado(), c, s, lista, marcadores(todos, Equipa.COLETES), marcadores(todos, Equipa.SEM_COLETES),
            coletes, semColetes, faltas, motivo);
    }

    /** Regista um golo. O marcador e a assistência, se existirem, têm de ser da equipa que marcou. */
    @Transactional
    public void adicionarGolo(Long jogoId, Equipa equipa, Long marcadorId, Long assistenciaId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.iniciarRegisto();
        if (equipa == null) {
            throw new OperacaoInvalidaException("Indica a equipa que marcou.");
        }
        Inscricao marcador = marcadorId != null ? daEquipa(jogo, marcadorId, equipa, "O marcador") : null;
        Inscricao assistencia = assistenciaId != null ? daEquipa(jogo, assistenciaId, equipa, "Quem assistiu") : null;
        if (marcador == null && assistencia != null) {
            throw new OperacaoInvalidaException("Um golo sem marcador não pode ter assistência.");
        }
        if (marcador != null && assistencia != null && marcador.getId().equals(assistencia.getId())) {
            throw new OperacaoInvalidaException("O marcador não pode ser também quem assistiu.");
        }
        Golo golo = golos.save(new Golo(jogo, equipa, marcador, assistencia, LocalDateTime.now(clock), ator));
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.REGISTO_GOLO, jogo.getId(), "GOLO", golo.getId(),
            "Golo de " + equipa.getDescricao() + ": " + (marcador != null ? marcador.getNomeVisivel() : "sem marcador")
                + (assistencia != null ? " (assistência de " + assistencia.getNomeVisivel() + ")" : "")));
    }

    @Transactional
    public void apagarGolo(Long jogoId, Long goloId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.iniciarRegisto();
        Golo golo = golos.findById(goloId).filter(g -> g.getJogo().getId().equals(jogo.getId()))
            .orElseThrow(() -> new OperacaoInvalidaException("Esse golo já não existe."));
        golos.delete(golo);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.ANULACAO_GOLO, jogo.getId(), "GOLO", goloId,
            "Golo apagado: " + golo.getEquipa().getDescricao() + ", "
                + (golo.getMarcador() != null ? golo.getMarcador().getNomeVisivel() : "sem marcador")));
    }

    /**
     * Define só o resultado, sem marcadores: acrescenta ou retira golos sem marcador até cada equipa ter o número pedido.
     * Os golos com marcador nunca são apagados aqui.
     */
    @Transactional
    public void definirResultado(Long jogoId, int coletes, int semColetes, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.iniciarRegisto();
        if (coletes < 0 || semColetes < 0 || coletes > MAX_GOLOS || semColetes > MAX_GOLOS) {
            throw new OperacaoInvalidaException("O resultado tem de ser entre 0 e " + MAX_GOLOS + ".");
        }
        ajustar(jogo, Equipa.COLETES, coletes, ator);
        ajustar(jogo, Equipa.SEM_COLETES, semColetes, ator);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.DEFINICAO_RESULTADO, jogo.getId(), "JOGO", jogo.getId(),
            "Resultado definido: Coletes " + coletes + " – " + semColetes + " Sem coletes"));
    }

    @Transactional
    public void marcarFalta(Long jogoId, Long inscricaoId, boolean faltou, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.iniciarRegisto();
        Inscricao ins = comEquipa(jogo).stream().filter(i -> i.getId().equals(inscricaoId)).findFirst()
            .orElseThrow(() -> new OperacaoInvalidaException("Esse jogador não está nas equipas."));
        if (ins.isFaltou() == faltou) {
            return;
        }
        ins.marcarFalta(faltou);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.FALTA_COMPARENCIA, jogo.getId(), "INSCRICAO", ins.getId(),
            faltou ? ins.getNomeVisivel() + " faltou" : ins.getNomeVisivel() + " afinal esteve presente"));
    }

    /** Fecha o jogo e abre a votação. Não é obrigatório ter golos registados. */
    @Transactional
    public void fechar(Long jogoId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.fecharJogo();
        long c = golos.countByJogoAndEquipa(jogo, Equipa.COLETES);
        long s = golos.countByJogoAndEquipa(jogo, Equipa.SEM_COLETES);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.FECHO_JOGO, jogo.getId(), "JOGO", jogo.getId(),
            "Jogo fechado: Coletes " + c + " – " + s + " Sem coletes. Votação aberta."));
    }

    @Transactional
    public void reabrir(Long jogoId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.reabrirRegisto();
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.REABERTURA_FASE, jogo.getId(), "JOGO", jogo.getId(),
            "Registo do jogo reaberto"));
    }

    // ---------------------------------------------------------------- apoio

    private void ajustar(Jogo jogo, Equipa equipa, int alvo, Utilizador ator) {
        long total = golos.countByJogoAndEquipa(jogo, equipa);
        long comMarcador = golos.countByJogoAndEquipaAndMarcadorIsNotNull(jogo, equipa);
        if (alvo < comMarcador) {
            throw new OperacaoInvalidaException(equipa.getDescricao() + " já tem " + comMarcador
                + (comMarcador == 1 ? " golo com marcador" : " golos com marcador") + ". Apaga-os primeiro na lista de golos.");
        }
        if (alvo > total) {
            for (long i = total; i < alvo; i++) {
                golos.save(new Golo(jogo, equipa, null, null, LocalDateTime.now(clock), ator));
            }
        } else if (alvo < total) {
            List<Golo> semMarcador = golos.findByJogoAndEquipaAndMarcadorIsNullOrderByIdDesc(jogo, equipa);
            golos.deleteAll(semMarcador.subList(0, (int) (total - alvo)));
        }
    }

    private static List<RegistoView.Marcador> marcadores(List<Golo> todos, Equipa equipa) {
        Map<String, Long> contagem = todos.stream()
            .filter(g -> g.getEquipa() == equipa && g.getMarcador() != null)
            .collect(Collectors.groupingBy(g -> g.getMarcador().getNomeVisivel(), LinkedHashMap::new, Collectors.counting()));
        return contagem.entrySet().stream()
            .map(e -> new RegistoView.Marcador(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(RegistoView.Marcador::golos).reversed())
            .toList();
    }

    /** Quem está numa equipa (as primeiras {@code vagas} inscrições ativas com equipa atribuída). */
    private List<Inscricao> comEquipa(Jogo jogo) {
        List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
        return ativas.subList(0, Math.min(jogo.getVagas(), ativas.size())).stream()
            .filter(i -> i.getEquipa() != null)
            .toList();
    }

    private Inscricao daEquipa(Jogo jogo, Long inscricaoId, Equipa equipa, String quem) {
        Inscricao ins = comEquipa(jogo).stream().filter(i -> i.getId().equals(inscricaoId)).findFirst()
            .orElseThrow(() -> new OperacaoInvalidaException(quem + " não está nas equipas deste jogo."));
        if (ins.getEquipa() != equipa) {
            throw new OperacaoInvalidaException(quem + " não é da equipa " + equipa.getDescricao() + ".");
        }
        return ins;
    }

    private Utilizador exigirGestor(String username) {
        Utilizador u = carregarUtilizador(username);
        if (!u.ehGestor() || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Organizador ou o Admin podem registar o jogo.");
        }
        return u;
    }

    private Jogo carregarJogo(Long id) {
        return jogos.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Jogo não encontrado."));
    }

    private Utilizador carregarUtilizador(String username) {
        return utilizadores.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
    }
}
