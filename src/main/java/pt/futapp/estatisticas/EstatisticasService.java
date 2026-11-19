package pt.futapp.estatisticas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.jogo.Equipa;
import pt.futapp.jogo.EstadoInscricao;
import pt.futapp.jogo.EstadoJogo;
import pt.futapp.jogo.Golo;
import pt.futapp.jogo.GoloRepository;
import pt.futapp.jogo.Inscricao;
import pt.futapp.jogo.InscricaoRepository;
import pt.futapp.jogo.Jogo;
import pt.futapp.jogo.JogoRepository;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.votacao.PremiosService;
import pt.futapp.votacao.VotacaoService;
import pt.futapp.votacao.VotacaoView;
import pt.futapp.votacao.VotoRepository;

/**
 * Estatísticas calculadas a partir dos jogos fechados (estado FECHADO), sem nada guardado à parte:
 * jogos (presenças), vitórias/empates/derrotas, golos, assistências, MVPs, prémios brincadeira e faltas.
 * Os convidados também contam: como não têm conta, são agregados pelo nome. Os bloqueados continuam a contar.
 */
@Service
public class EstatisticasService {

    private static final int TOPO = 5;

    private final JogoRepository jogos;
    private final InscricaoRepository inscricoes;
    private final GoloRepository golos;
    private final VotoRepository votos;
    private final PremiosService premios;
    private final UtilizadorRepository utilizadores;

    public EstatisticasService(JogoRepository jogos, InscricaoRepository inscricoes, GoloRepository golos,
                               VotoRepository votos, PremiosService premios, UtilizadorRepository utilizadores) {
        this.utilizadores = utilizadores;
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.golos = golos;
        this.votos = votos;
        this.premios = premios;
    }

    @Transactional(readOnly = true)
    public EstatisticasView temporada(String usernameAtual) {
        List<Jogo> fechados = jogos.findByEstadoOrderByDataHoraAsc(EstadoJogo.FECHADO);
        Map<String, Acumulado> porJogador = calcular(fechados);

        List<EstatisticasView.Linha> tabela = porJogador.values().stream()
            .map(a -> a.linha(usernameAtual))
            .sorted(Comparator.comparingInt(EstatisticasView.Linha::jogos).reversed()
                .thenComparing(l -> l.nome().toLowerCase()))
            .toList();

        List<EstatisticasView.Ranking> rankings = new ArrayList<>();
        adicionar(rankings, "Melhores marcadores", "golo", "golos", tabela, EstatisticasView.Linha::golos);
        adicionar(rankings, "Mais MVPs", "MVP", "MVPs", tabela, EstatisticasView.Linha::mvps);
        adicionar(rankings, "Mais assistências", "assistência", "assistências", tabela, EstatisticasView.Linha::assistencias);
        adicionar(rankings, "Mais vitórias", "vitória", "vitórias", tabela, EstatisticasView.Linha::vitorias);
        adicionar(rankings, "Mais presenças", "jogo", "jogos", tabela, EstatisticasView.Linha::jogos);
        adicionar(rankings, "Mais faltas", "falta", "faltas", tabela, EstatisticasView.Linha::faltas);
        return new EstatisticasView(fechados.size(), rankings, tabela);
    }

    /** Números de um jogador (para a carta). */
    @Transactional(readOnly = true)
    public EstatisticasView.Linha doJogador(Long jogadorId, String usernameAtual) {
        Utilizador jogador = utilizadores.findMembro(jogadorId)
            .orElseThrow(() -> new pt.futapp.comum.OperacaoInvalidaException("Jogador não encontrado."));
        Acumulado a = calcular(jogos.findByEstadoOrderByDataHoraAsc(EstadoJogo.FECHADO)).get(chave(jogador.getId(), null));
        return a != null ? a.linha(usernameAtual)
            : EstatisticasView.Linha.vazia(jogador.getId(), jogador.getNomeVisivel(),
                jogador.getUsername().equalsIgnoreCase(usernameAtual));
    }

    // ---------------------------------------------------------------- cálculo

    /**
     * Chave de agregação: o utilizador (também quando a conta foi removida: cada pessoa fica na sua linha), ou o nome
     * do convidado sem conta (sem diferenças de maiúsculas e espaços).
     */
    private static String chave(Long jogadorId, String nomeConvidado) {
        return jogadorId != null ? "u:" + jogadorId : "c:" + nomeConvidado.trim().toLowerCase();
    }

    private static String chave(Inscricao ins) {
        return chave(ins.getJogador() != null ? ins.getJogador().getId() : null, ins.getNomeConvidado());
    }

    private Map<String, Acumulado> calcular(List<Jogo> fechados) {
        Map<String, Acumulado> porJogador = new HashMap<>();
        var ordemPremios = premios.todos();
        for (Jogo jogo : fechados) {
            List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
            List<Inscricao> jogaram = ativas.subList(0, Math.min(jogo.getVagas(), ativas.size())).stream()
                .filter(i -> i.getEquipa() != null).toList();
            List<Golo> golosJogo = golos.findByJogoOrderByIdAsc(jogo);
            long c = golosJogo.stream().filter(g -> g.getEquipa() == Equipa.COLETES).count();
            long s = golosJogo.size() - c;
            Equipa vencedora = c > s ? Equipa.COLETES : (s > c ? Equipa.SEM_COLETES : null);

            Map<Long, Inscricao> porInscricao = jogaram.stream().collect(Collectors.toMap(Inscricao::getId, i -> i));
            for (Inscricao ins : jogaram) {
                Acumulado a = porJogador.computeIfAbsent(chave(ins), k -> new Acumulado(ins));
                if (ins.isFaltou()) {
                    a.faltas++;
                    continue;
                }
                a.jogos++;
                if (vencedora == null) {
                    a.empates++;
                } else if (vencedora == ins.getEquipa()) {
                    a.vitorias++;
                } else {
                    a.derrotas++;
                }
            }
            for (Golo g : golosJogo) {
                somar(porJogador, g.getMarcador(), a -> a.golos++);
                somar(porJogador, g.getAssistencia(), a -> a.assistencias++);
            }

            Map<Long, Long> golosPorInscricao = golosJogo.stream().map(Golo::getMarcador).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Inscricao::getId, Collectors.counting()));
            for (VotacaoView.Resultado r : VotacaoService.resultados(votos.findByJogoAndAnuladoFalse(jogo), golosPorInscricao, ordemPremios)) {
                for (Long inscricaoId : r.inscricoes()) {
                    somar(porJogador, porInscricao.get(inscricaoId), r.mvp() ? a -> a.mvps++ : a -> a.premios++);
                }
            }
        }
        return porJogador;
    }

    private static void somar(Map<String, Acumulado> porJogador, Inscricao ins, java.util.function.Consumer<Acumulado> acao) {
        if (ins == null) {
            return;
        }
        acao.accept(porJogador.computeIfAbsent(chave(ins), k -> new Acumulado(ins)));
    }

    private static void adicionar(List<EstatisticasView.Ranking> rankings, String titulo, String singular, String plural,
                                  List<EstatisticasView.Linha> tabela, ToIntFunction<EstatisticasView.Linha> valor) {
        List<EstatisticasView.Linha> ordenada = tabela.stream().filter(l -> valor.applyAsInt(l) > 0)
            .sorted(Comparator.comparingInt(valor).reversed().thenComparing(l -> l.nome().toLowerCase()))
            .toList();
        if (ordenada.isEmpty()) {
            return;
        }
        List<EstatisticasView.Lugar> lugares = new ArrayList<>();
        int posicao = 0;
        int anterior = -1;
        for (int i = 0; i < ordenada.size(); i++) {
            EstatisticasView.Linha l = ordenada.get(i);
            int v = valor.applyAsInt(l);
            if (v != anterior) {
                posicao = i + 1;
                anterior = v;
            }
            if (posicao > TOPO || lugares.size() == TOPO) {
                break;
            }
            lugares.add(new EstatisticasView.Lugar(posicao, l.jogadorId(), l.nome(), v, l.eu(), l.convidado()));
        }
        long ultimo = lugares.get(lugares.size() - 1).valor();
        int maisEmpatados = (int) ordenada.stream().skip(lugares.size()).filter(l -> valor.applyAsInt(l) == ultimo).count();
        rankings.add(new EstatisticasView.Ranking(titulo, singular, plural, lugares, maisEmpatados));
    }

    private static final class Acumulado {
        private final Utilizador jogador;
        private final String nomeConvidado;
        int jogos, vitorias, empates, derrotas, golos, assistencias, mvps, premios, faltas;

        Acumulado(Inscricao ins) {
            this.jogador = ins.getJogador();
            this.nomeConvidado = ins.getNomeConvidado();
        }

        EstatisticasView.Linha linha(String usernameAtual) {
            // membro removido: mostrado como convidado (sem carta), mas numa linha só dele
            boolean convidado = jogador == null || jogador.isRemovido();
            return new EstatisticasView.Linha(convidado ? null : jogador.getId(),
                jogador == null ? nomeConvidado : jogador.getNomeVisivel(), jogos, vitorias, empates, derrotas,
                golos, assistencias, mvps, premios, faltas,
                !convidado && jogador.getUsername().equalsIgnoreCase(usernameAtual), convidado);
        }
    }
}
