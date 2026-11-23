package pt.futapp.utilizador;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.avaliacao.AvaliacaoColegaRepository;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.jogo.GoloRepository;
import pt.futapp.jogo.InscricaoRepository;
import pt.futapp.jogo.JogoRepository;
import pt.futapp.jogo.JogoService;
import pt.futapp.votacao.VotacaoService;
import pt.futapp.votacao.VotoRepository;

/**
 * O Admin remove um membro. Não há volta atrás.
 * <ul>
 *   <li>Primeiro sai dos jogos que ainda não se jogaram (fica registado e quem está em espera sobe) e essas
 *       inscrições são apagadas.</li>
 *   <li>Se não sobra nada ligado à pessoa (nunca jogou, não votou, não registou golos, não criou jogos), a conta é
 *       apagada de vez.</li>
 *   <li>Senão, soft delete ({@link Utilizador#marcarRemovido}): deixa de entrar, desaparece das listas, o username
 *       fica livre e nos jogos antigos é mostrada como convidado ({@code Inscricao.isConvidado}). As inscrições
 *       continuam ligadas à conta, por isso as estatísticas, os resultados e os votos antigos não mudam.</li>
 *   <li>Nas votações abertas dos jogos em que jogou, se todos os outros já votaram, a votação fecha.</li>
 *   <li>As avaliações de colegas que deu e recebeu são apagadas (as médias deixam de contar com elas).</li>
 * </ul>
 */
@Service
public class RemocaoService {

    private final UtilizadorRepository utilizadores;
    private final InscricaoRepository inscricoes;
    private final GoloRepository golos;
    private final JogoRepository jogos;
    private final VotoRepository votos;
    private final AvaliacaoColegaRepository avaliacoes;
    private final JogoService jogoService;
    private final VotacaoService votacao;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public RemocaoService(UtilizadorRepository utilizadores, InscricaoRepository inscricoes, GoloRepository golos,
                          JogoRepository jogos, VotoRepository votos, AvaliacaoColegaRepository avaliacoes,
                          JogoService jogoService, VotacaoService votacao, ApplicationEventPublisher eventos, Clock clock) {
        this.utilizadores = utilizadores;
        this.inscricoes = inscricoes;
        this.golos = golos;
        this.jogos = jogos;
        this.votos = votos;
        this.avaliacoes = avaliacoes;
        this.jogoService = jogoService;
        this.votacao = votacao;
        this.eventos = eventos;
        this.clock = clock;
    }

    public enum Resultado {
        /** Não tinha histórico: a conta foi apagada. */
        APAGADO,
        /** Tinha histórico: soft delete, mostrado como convidado nos jogos antigos. */
        REMOVIDO
    }

    @Transactional
    public Resultado remover(Long membroId, String usernameAdmin) {
        Utilizador admin = utilizadores.findByUsernameIgnoreCase(usernameAdmin)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        if (!admin.temRole(Role.ADMIN) || admin.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Admin pode fazer isto.");
        }
        Utilizador membro = utilizadores.findMembro(membroId)
            .orElseThrow(() -> new OperacaoInvalidaException("Membro não encontrado."));
        if (membro.getId().equals(admin.getId())) {
            throw new OperacaoInvalidaException("Não te podes remover a ti próprio.");
        }
        String nome = membro.getNome();

        jogoService.retirarDosJogosPorJogar(membro, admin);
        // as avaliações de colegas (dadas e recebidas) saem com o membro e não contam como histórico
        avaliacoes.deleteByAvaliadorOrAvaliado(membro, membro);

        boolean temHistorico = !inscricoes.findByJogador(membro).isEmpty()
            || inscricoes.existsByCanceladoPor(membro)
            || votos.existsByEleitorOrAnuladoPor(membro, membro)
            || golos.existsByRegistadoPor(membro)
            || jogos.existsByCriadoPorOrCanceladoPor(membro, membro);

        if (!temHistorico) {
            utilizadores.delete(membro);
            eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.REMOCAO_UTILIZADOR, null, "UTILIZADOR",
                membroId, nome + " removido (não tinha jogos: conta apagada)"));
            return Resultado.APAGADO;
        }

        membro.marcarRemovido(LocalDateTime.now(clock), usernameLivre(membro));
        utilizadores.flush();
        votacao.fecharSeTodosVotaram(membro, admin);
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.REMOCAO_UTILIZADOR, null, "UTILIZADOR",
            membroId, nome + " removido; nos jogos antigos passa a aparecer como convidado"));
        return Resultado.REMOVIDO;
    }

    /** "removido-{id}"; se, por acaso, já existir, acrescenta um número. */
    private String usernameLivre(Utilizador membro) {
        String base = Utilizador.PREFIXO_REMOVIDO + membro.getId();
        String candidato = base;
        for (int n = 2; utilizadores.existsByUsernameIgnoreCase(candidato); n++) {
            candidato = base + "-" + n;
        }
        return candidato;
    }
}
