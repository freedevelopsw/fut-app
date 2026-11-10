package pt.futapp.votacao;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.Nomes;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.jogo.EstadoInscricao;
import pt.futapp.jogo.Golo;
import pt.futapp.jogo.GoloRepository;
import pt.futapp.jogo.Inscricao;
import pt.futapp.jogo.InscricaoRepository;
import pt.futapp.jogo.Jogo;
import pt.futapp.jogo.JogoRepository;
import pt.futapp.utilizador.Role;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.web.Formatos;

/**
 * Fase 4 (Votação). Abre quando o jogo fecha.
 * <ul>
 *   <li>Votam os jogadores com conta que estiveram presentes; ninguém vota em si próprio; os convidados recebem votos mas não votam.</li>
 *   <li>Boletim: MVP obrigatório, prémios opcionais. Pode ser alterado enquanto a votação está aberta.</li>
 *   <li>Voto secreto para os jogadores; o Admin vê quem votou em quem e pode anular votos.</li>
 *   <li>Fecha sozinha quando todos votaram, ou quando o Organizador ou o Admin a fecham.</li>
 *   <li>Empate no MVP: ganha quem marcou mais golos no jogo; se continuar, o prémio é partilhado.
 *       Nos outros prémios, empate = partilhado.</li>
 * </ul>
 */
@Service
public class VotacaoService {

    private final JogoRepository jogos;
    private final InscricaoRepository inscricoes;
    private final GoloRepository golos;
    private final VotoRepository votos;
    private final PremiosService premios;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Formatos fmt;

    public VotacaoService(JogoRepository jogos, InscricaoRepository inscricoes, GoloRepository golos, VotoRepository votos,
                          PremiosService premios, UtilizadorRepository utilizadores, ApplicationEventPublisher eventos,
                          Clock clock, Formatos fmt) {
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.golos = golos;
        this.votos = votos;
        this.premios = premios;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.clock = clock;
        this.fmt = fmt;
    }

    @Transactional(readOnly = true)
    public VotacaoView vista(Long jogoId, String username) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);
        List<Inscricao> presentes = presentes(jogo);
        List<Utilizador> eleitores = eleitores(presentes);
        List<Voto> validos = votos.findByJogoAndAnuladoFalse(jogo);
        Map<Long, Long> golosPorInscricao = golosPorInscricao(jogo);

        Inscricao minha = presentes.stream().filter(i -> i.ehDe(eu)).findFirst().orElse(null);
        String motivo = motivoNaoVotar(jogo, eu, minha);
        Map<Long, Long> meusVotos = new HashMap<>();
        validos.stream().filter(v -> v.getEleitor().getId().equals(eu.getId()))
            .forEach(v -> meusVotos.put(v.getPremio() == null ? null : v.getPremio().getId(), v.getVotado().getId()));
        boolean jaVotei = meusVotos.containsKey(null);

        Set<Long> votaramIds = validos.stream().filter(Voto::isMvp).map(v -> v.getEleitor().getId()).collect(Collectors.toSet());
        List<VotacaoView.Eleitor> votaram = eleitores.stream().filter(u -> votaramIds.contains(u.getId()))
            .map(u -> new VotacaoView.Eleitor(u.getNomeVisivel(), Nomes.iniciais(u.getNomeVisivel()))).toList();

        List<VotacaoView.Candidato> candidatos = presentes.stream()
            .filter(i -> minha == null || !i.getId().equals(minha.getId()))
            .map(i -> new VotacaoView.Candidato(i.getId(), i.getNomeVisivel(), i.getEquipa(),
                golosPorInscricao.getOrDefault(i.getId(), 0L), i.isConvidado()))
            .toList();

        List<VotacaoView.Categoria> categorias = new ArrayList<>();
        categorias.add(new VotacaoView.Categoria(null, "MVP", "O melhor do jogo.", meusVotos.get(null)));
        for (Premio p : premios.ativos()) {
            categorias.add(new VotacaoView.Categoria(p.getId(), p.getNome(), p.getDescricao(), meusVotos.get(p.getId())));
        }

        boolean fechada = jogo.getEstado() == pt.futapp.jogo.EstadoJogo.FECHADO;
        List<VotacaoView.Resultado> resultados = fechada ? resultados(jogo, validos, golosPorInscricao) : List.of();
        boolean gestor = eu.ehGestor() && !eu.isBloqueado();
        return new VotacaoView(jogo.getId(), fmt.dia(jogo.getDataHora()), fmt.hora(jogo.getDataHora()), jogo.getEstado(),
            jogo.votacaoAberta(), fechada, motivo == null, motivo, jaVotei, eleitores.size(), votaram, candidatos,
            categorias, resultados, gestor, gestor && eu.temRole(Role.ADMIN));
    }

    /**
     * Guarda o boletim de quem vota (substitui o anterior).
     *
     * @param escolhas premioId → inscrição votada; a chave null é o MVP (obrigatório)
     * @return true se, com este voto, todos votaram e a votação fechou
     */
    @Transactional
    public boolean votar(Long jogoId, Map<Long, Long> escolhas, String username) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);
        List<Inscricao> presentes = presentes(jogo);
        Inscricao minha = presentes.stream().filter(i -> i.ehDe(eu)).findFirst().orElse(null);
        String motivo = motivoNaoVotar(jogo, eu, minha);
        if (motivo != null) {
            throw new OperacaoInvalidaException(motivo);
        }
        if (escolhas.get(null) == null) {
            throw new OperacaoInvalidaException("Escolhe o MVP.");
        }
        Map<Long, Inscricao> candidatos = presentes.stream().filter(i -> !i.getId().equals(minha.getId()))
            .collect(Collectors.toMap(Inscricao::getId, i -> i));
        Map<Long, Premio> ativos = premios.ativos().stream().collect(Collectors.toMap(Premio::getId, p -> p));

        List<Voto> novos = new ArrayList<>();
        LocalDateTime agora = LocalDateTime.now(clock);
        for (Map.Entry<Long, Long> e : escolhas.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            Premio premio = null;
            if (e.getKey() != null) {
                premio = ativos.get(e.getKey());
                if (premio == null) {
                    throw new OperacaoInvalidaException("Esse prémio já não está ativo.");
                }
            }
            Inscricao votado = candidatos.get(e.getValue());
            if (votado == null) {
                throw new OperacaoInvalidaException(e.getValue().equals(minha.getId())
                    ? "Não podes votar em ti próprio." : "Só podes votar em quem jogou.");
            }
            novos.add(new Voto(jogo, premio, eu, votado, agora));
        }

        List<Voto> anteriores = votos.findByJogoAndEleitorAndAnuladoFalse(jogo, eu);
        boolean alteracao = !anteriores.isEmpty();
        votos.deleteAll(anteriores);
        votos.flush();
        votos.saveAll(novos);
        eventos.publishEvent(new AuditEvent(eu.getUsername(), alteracao ? Acao.ALTERACAO_VOTO : Acao.VOTO, jogo.getId(),
            "JOGO", jogo.getId(), eu.getNomeVisivel() + (alteracao ? " alterou os votos" : " votou")
                + " (" + novos.size() + (novos.size() == 1 ? " categoria)" : " categorias)")));

        if (todosVotaram(jogo, presentes)) {
            jogo.fecharVotacao();
            eventos.publishEvent(new AuditEvent(eu.getUsername(), Acao.FECHO_VOTACAO, jogo.getId(), "JOGO", jogo.getId(),
                "Votação fechada automaticamente: todos votaram"));
            return true;
        }
        return false;
    }

    @Transactional
    public void fechar(Long jogoId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.fecharVotacao();
        long votaram = votos.findByJogoAndAnuladoFalse(jogo).stream().filter(Voto::isMvp).count();
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.FECHO_VOTACAO, jogo.getId(), "JOGO", jogo.getId(),
            "Votação fechada por " + ator.getNomeVisivel() + " com " + votaram + (votaram == 1 ? " voto" : " votos")));
    }

    @Transactional
    public void reabrir(Long jogoId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        jogo.reabrirVotacao();
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.REABERTURA_FASE, jogo.getId(), "JOGO", jogo.getId(),
            "Votação reaberta"));
    }

    // ---------------------------------------------------------------- Admin: ver e anular votos

    public record VotoAdmin(Long id, String eleitor, String categoria, String votado, boolean anulado) {
    }

    @Transactional(readOnly = true)
    public List<VotoAdmin> votosDoJogo(Long jogoId, String username) {
        exigirAdmin(username);
        Jogo jogo = carregarJogo(jogoId);
        return votos.findByJogoOrderByIdAsc(jogo).stream()
            .sorted(Comparator.comparing((Voto v) -> v.getEleitor().getNomeVisivel().toLowerCase())
                .thenComparing(v -> v.getPremio() == null ? -1 : v.getPremio().getOrdem()))
            .map(v -> new VotoAdmin(v.getId(), v.getEleitor().getNomeVisivel(),
                v.getPremio() == null ? "MVP" : v.getPremio().getNome(), v.getVotado().getNomeVisivel(), v.isAnulado()))
            .toList();
    }

    @Transactional
    public void anular(Long jogoId, Long votoId, String username) {
        Utilizador admin = exigirAdmin(username);
        Jogo jogo = carregarJogo(jogoId);
        Voto voto = votos.findById(votoId).filter(v -> v.getJogo().getId().equals(jogo.getId()))
            .orElseThrow(() -> new OperacaoInvalidaException("Esse voto não existe."));
        if (voto.isAnulado()) {
            return;
        }
        voto.anular(admin);
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.ANULACAO_VOTO, jogo.getId(), "VOTO", voto.getId(),
            "Voto anulado: " + voto.getEleitor().getNomeVisivel() + " → " + voto.getVotado().getNomeVisivel()
                + " (" + (voto.getPremio() == null ? "MVP" : voto.getPremio().getNome()) + ")"));
    }

    // ---------------------------------------------------------------- regras

    /** Quem jogou e esteve presente (inscrições com equipa, sem falta). */
    private List<Inscricao> presentes(Jogo jogo) {
        List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
        return ativas.subList(0, Math.min(jogo.getVagas(), ativas.size())).stream()
            .filter(i -> i.getEquipa() != null && !i.isFaltou())
            .toList();
    }

    /** Quem pode votar: os presentes com conta e não bloqueados. */
    private static List<Utilizador> eleitores(List<Inscricao> presentes) {
        return presentes.stream().map(Inscricao::getJogador).filter(Objects::nonNull)
            .filter(u -> !u.isBloqueado()).toList();
    }

    private static String motivoNaoVotar(Jogo jogo, Utilizador eu, Inscricao minha) {
        if (!jogo.votacaoAberta()) {
            return "A votação não está aberta.";
        }
        if (eu.isBloqueado()) {
            return "A tua conta está bloqueada.";
        }
        if (minha == null) {
            return "Só votam os jogadores que estiveram presentes neste jogo.";
        }
        return null;
    }

    private boolean todosVotaram(Jogo jogo, List<Inscricao> presentes) {
        Set<Long> votaram = votos.findByJogoAndAnuladoFalse(jogo).stream().filter(Voto::isMvp)
            .map(v -> v.getEleitor().getId()).collect(Collectors.toSet());
        List<Utilizador> eleitores = eleitores(presentes);
        return !eleitores.isEmpty() && eleitores.stream().allMatch(u -> votaram.contains(u.getId()));
    }

    private Map<Long, Long> golosPorInscricao(Jogo jogo) {
        return golos.findByJogoOrderByIdAsc(jogo).stream().map(Golo::getMarcador).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Inscricao::getId, Collectors.counting()));
    }

    /** MVP primeiro, depois os prémios pela ordem; só categorias com votos. Usado também pelas estatísticas. */
    public static List<VotacaoView.Resultado> resultados(List<Voto> validos, Map<Long, Long> golosPorInscricao,
                                                  List<Premio> ordemPremios) {
        Map<Long, List<Voto>> porPremio = new LinkedHashMap<>();
        List<Voto> mvp = new ArrayList<>();
        for (Voto v : validos) {
            if (v.isMvp()) {
                mvp.add(v);
            } else {
                porPremio.computeIfAbsent(v.getPremio().getId(), k -> new ArrayList<>()).add(v);
            }
        }
        List<VotacaoView.Resultado> lista = new ArrayList<>();
        if (!mvp.isEmpty()) {
            lista.add(apurar("MVP", true, mvp, golosPorInscricao));
        }
        for (Premio p : ordemPremios) {
            List<Voto> vs = porPremio.get(p.getId());
            if (vs != null && !vs.isEmpty()) {
                lista.add(apurar(p.getNome(), false, vs, golosPorInscricao));
            }
        }
        return lista;
    }

    private List<VotacaoView.Resultado> resultados(Jogo jogo, List<Voto> validos, Map<Long, Long> golosPorInscricao) {
        return resultados(validos, golosPorInscricao, premios.todos());
    }

    private static VotacaoView.Resultado apurar(String categoria, boolean mvp, List<Voto> vs, Map<Long, Long> golos) {
        Map<Long, Long> contagem = vs.stream().collect(Collectors.groupingBy(v -> v.getVotado().getId(), Collectors.counting()));
        Map<Long, String> nomes = new HashMap<>();
        vs.forEach(v -> nomes.put(v.getVotado().getId(), v.getVotado().getNomeVisivel()));
        long max = contagem.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Long> empatados = contagem.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
        boolean desempate = false;
        if (mvp && empatados.size() > 1) {
            long maisGolos = empatados.stream().mapToLong(id -> golos.getOrDefault(id, 0L)).max().orElse(0);
            List<Long> porGolos = empatados.stream().filter(id -> golos.getOrDefault(id, 0L) == maisGolos).toList();
            desempate = porGolos.size() < empatados.size();
            empatados = porGolos;
        }
        List<Long> ids = empatados.stream().sorted(Comparator.comparing(nomes::get)).toList();
        List<String> vencedores = ids.stream().map(nomes::get).toList();
        return new VotacaoView.Resultado(categoria, mvp, vencedores, ids, max, desempate);
    }

    private Utilizador exigirGestor(String username) {
        Utilizador u = carregarUtilizador(username);
        if (!u.ehGestor() || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Organizador ou o Admin podem fazer isto.");
        }
        return u;
    }

    private Utilizador exigirAdmin(String username) {
        Utilizador u = carregarUtilizador(username);
        if (!u.temRole(Role.ADMIN) || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Admin pode ver e anular votos.");
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
