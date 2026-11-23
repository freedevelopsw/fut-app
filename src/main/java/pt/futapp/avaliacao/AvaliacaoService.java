package pt.futapp.avaliacao;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Caracterizacao;
import pt.futapp.utilizador.JogadorResumo;
import pt.futapp.utilizador.JogadorService;
import pt.futapp.utilizador.Role;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;

/**
 * Avaliação das cartas pelos colegas (proposta de 22/11/2026 na especificação).
 * <ul>
 *   <li>Cada membro avalia os colegas nos mesmos seis atributos da carta (1 a 10, todos opcionais); uma avaliação por
 *       colega, voltar a avaliar substitui, tudo em branco apaga. Ninguém se avalia a si próprio (isso é a
 *       autoavaliação, nos dados do jogador) nem avalia um jogador bloqueado.</li>
 *   <li>A média dos colegas aparece logo a partir do primeiro voto, sempre com o número de votos.</li>
 *   <li>Quem deu cada nota: o Admin vê sempre; os outros só com o interruptor do Admin ligado
 *       ({@link InterruptorDetalhesAvaliacoes}, temporário).</li>
 *   <li>A autoavaliação continua a ser a da carta e é a que se vê e ordena por omissão. As equipas continuam a ser
 *       geradas com a autoavaliação.</li>
 * </ul>
 */
@Service
public class AvaliacaoService {

    private final AvaliacaoColegaRepository avaliacoes;
    private final UtilizadorRepository utilizadores;
    private final JogadorService jogadores;
    private final InterruptorDetalhesAvaliacoes interruptor;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public AvaliacaoService(AvaliacaoColegaRepository avaliacoes, UtilizadorRepository utilizadores,
                            JogadorService jogadores, InterruptorDetalhesAvaliacoes interruptor,
                            ApplicationEventPublisher eventos, Clock clock) {
        this.avaliacoes = avaliacoes;
        this.utilizadores = utilizadores;
        this.jogadores = jogadores;
        this.interruptor = interruptor;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- avaliar

    /** As notas que já dei a este colega (vazio se ainda não o avaliei). */
    @Transactional(readOnly = true)
    public AvaliacaoForm formulario(Long avaliadoId, String username) {
        Utilizador eu = carregarPorUsername(username);
        Utilizador alvo = carregarAvaliavel(eu, avaliadoId);
        return avaliacoes.findByAvaliadorAndAvaliado(eu, alvo).map(a -> AvaliacaoForm.de(a.notas()))
            .orElseGet(AvaliacaoForm::new);
    }

    /** Guarda (ou substitui) a minha avaliação de um colega; tudo em branco apaga-a. */
    @Transactional
    public void guardar(Long avaliadoId, AvaliacaoForm form, String username) {
        Utilizador eu = carregarPorUsername(username);
        Utilizador alvo = carregarAvaliavel(eu, avaliadoId);
        Notas notas = form.notas();
        if (!notas.validas()) {
            throw new OperacaoInvalidaException("As notas são de 1 a 10.");
        }
        Optional<AvaliacaoColega> existente = avaliacoes.findByAvaliadorAndAvaliado(eu, alvo);
        if (notas.vazia()) {
            if (existente.isPresent()) {
                avaliacoes.delete(existente.get());
                eventos.publishEvent(new AuditEvent(eu.getUsername(), Acao.AVALIACAO_COLEGA, null, "UTILIZADOR",
                    alvo.getId(), eu.getNome() + " apagou a avaliação de " + alvo.getNome()));
            }
            return;
        }
        AvaliacaoColega a = existente.orElseGet(() -> new AvaliacaoColega(eu, alvo));
        a.definir(notas, LocalDateTime.now(clock));
        avaliacoes.save(a);
        eventos.publishEvent(new AuditEvent(eu.getUsername(), Acao.AVALIACAO_COLEGA, null, "UTILIZADOR", alvo.getId(),
            eu.getNome() + (existente.isPresent() ? " alterou a avaliação de " : " avaliou ") + alvo.getNome()));
    }

    // ---------------------------------------------------------------- ver

    /** Média dos colegas de cada jogador avaliado (por id do jogador). */
    @Transactional(readOnly = true)
    public Map<Long, MediaColegas> medias() {
        return avaliacoes.findAll().stream()
            .filter(a -> !a.getAvaliador().isRemovido() && !a.getAvaliado().isRemovido())
            .collect(Collectors.groupingBy(a -> a.getAvaliado().getId(),
                Collectors.collectingAndThen(Collectors.mapping(AvaliacaoColega::notas, Collectors.toList()),
                    MediaColegas::de)));
    }

    /**
     * Cartas dos jogadores ativos com a autoavaliação e a média dos colegas, ordenadas por uma delas (a outra
     * desempata, depois o nome; sem nota no fim).
     */
    @Transactional(readOnly = true)
    public List<CartaResumo> cartas(String usernameAtual, OrdemCartas ordem) {
        Map<Long, MediaColegas> medias = medias();
        Comparator<CartaResumo> porAuto = Comparator.comparing(CartaResumo::auto,
            Comparator.nullsLast(Comparator.<Integer>reverseOrder()));
        Comparator<CartaResumo> porColegas = Comparator.comparing(CartaResumo::colegas,
            Comparator.nullsLast(Comparator.<Double>reverseOrder()));
        Comparator<CartaResumo> porNome = Comparator.comparing((CartaResumo c) -> c.nomeVisivel().toLowerCase());
        Comparator<CartaResumo> comparador = ordem == OrdemCartas.COLEGAS
            ? porColegas.thenComparing(porAuto).thenComparing(porNome)
            : porAuto.thenComparing(porColegas).thenComparing(porNome);
        List<CartaResumo> cartas = new ArrayList<>();
        for (JogadorResumo j : jogadores.jogadores(usernameAtual)) {
            MediaColegas m = medias.getOrDefault(j.id(), MediaColegas.SEM_VOTOS);
            cartas.add(new CartaResumo(j.id(), j.nomeVisivel(), j.iniciais(), j.posicaoSigla(), j.rating(), m.rating(),
                m.votos(), j.eu()));
        }
        cartas.sort(comparador);
        return cartas;
    }

    /** A parte da carta com a autoavaliação e a média dos colegas, atributo a atributo. */
    @Transactional(readOnly = true)
    public CartaAvaliacoesView daCarta(Long jogadorId, String usernameAtual) {
        Utilizador eu = carregarPorUsername(usernameAtual);
        Utilizador jogador = carregarMembro(jogadorId);
        MediaColegas media = MediaColegas.de(recebidas(jogador).stream().map(AvaliacaoColega::notas).toList());
        Caracterizacao c = jogador.getCaracterizacao();
        List<Integer> auto = java.util.Arrays.asList(c.getVelocidade(), c.getTecnica(), c.getRemate(), c.getPasse(),
            c.getDefesa(), c.getResistencia());
        List<CartaAvaliacoesView.Linha> linhas = new ArrayList<>();
        for (int i = 0; i < Notas.NOMES.size(); i++) {
            linhas.add(new CartaAvaliacoesView.Linha(Notas.NOMES.get(i), auto.get(i), media.atributos().get(i)));
        }
        boolean jaAvaliei = !eu.getId().equals(jogador.getId())
            && avaliacoes.findByAvaliadorAndAvaliado(eu, jogador).isPresent();
        return new CartaAvaliacoesView(jogador.getId(), jogador.getNomeVisivel(), media, linhas,
            podeAvaliar(eu, jogador), jaAvaliei, podeVerDetalhes(eu));
    }

    /** Quem avaliou este jogador e com que notas. O Admin vê sempre; os outros só com o interruptor ligado. */
    @Transactional(readOnly = true)
    public DetalhesAvaliacoesView detalhes(Long jogadorId, String usernameAtual) {
        Utilizador eu = carregarPorUsername(usernameAtual);
        Utilizador jogador = carregarMembro(jogadorId);
        if (!podeVerDetalhes(eu)) {
            throw new OperacaoInvalidaException("Quem avaliou e como fica visível quando o Admin der permissão.");
        }
        List<AvaliacaoColega> recebidas = recebidas(jogador).stream()
            .sorted(Comparator.comparing((AvaliacaoColega a) -> a.getAvaliador().getNomeVisivel().toLowerCase()))
            .toList();
        List<DetalhesAvaliacoesView.Linha> linhas = recebidas.stream()
            .map(a -> new DetalhesAvaliacoesView.Linha(a.getAvaliador().getNomeVisivel(),
                a.notas().valores().stream().map(v -> v == null ? "–" : v.toString()).toList()))
            .toList();
        MediaColegas media = MediaColegas.de(recebidas.stream().map(AvaliacaoColega::notas).toList());
        return new DetalhesAvaliacoesView(jogador.getId(), jogador.getNomeVisivel(), linhas,
            media.atributos().stream().map(MediaColegas::decimal).toList(), media.votosTexto());
    }

    /** O Admin vê sempre quem avaliou e como; os outros só com o interruptor (temporário) ligado. */
    boolean podeVerDetalhes(Utilizador eu) {
        return ehAdmin(eu) || interruptor.ligado();
    }

    // ---------------------------------------------------------------- apoio

    private List<AvaliacaoColega> recebidas(Utilizador jogador) {
        return avaliacoes.findByAvaliado(jogador).stream()
            .filter(a -> !a.getAvaliador().isRemovido() && !a.notas().vazia())
            .toList();
    }

    private static boolean ehAdmin(Utilizador u) {
        return u.temRole(Role.ADMIN) && !u.isBloqueado();
    }

    private static boolean podeAvaliar(Utilizador eu, Utilizador alvo) {
        return !eu.getId().equals(alvo.getId()) && !alvo.isBloqueado() && !eu.isBloqueado();
    }

    private Utilizador carregarAvaliavel(Utilizador eu, Long avaliadoId) {
        Utilizador alvo = carregarMembro(avaliadoId);
        if (eu.getId().equals(alvo.getId())) {
            throw new OperacaoInvalidaException("Não te podes avaliar a ti próprio: a tua autoavaliação está nos teus dados.");
        }
        if (alvo.isBloqueado()) {
            throw new OperacaoInvalidaException("Não é possível avaliar um jogador bloqueado.");
        }
        if (eu.isBloqueado()) {
            throw new OperacaoInvalidaException("A tua conta está bloqueada.");
        }
        return alvo;
    }

    private Utilizador carregarMembro(Long id) {
        return utilizadores.findMembro(id)
            .orElseThrow(() -> new OperacaoInvalidaException("Jogador não encontrado."));
    }

    private Utilizador carregarPorUsername(String username) {
        return utilizadores.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
    }
}
