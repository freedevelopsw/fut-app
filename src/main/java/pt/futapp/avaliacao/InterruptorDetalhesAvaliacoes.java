package pt.futapp.avaliacao;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.Configuracao;
import pt.futapp.comum.ConfiguracaoRepository;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Role;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;

/**
 * TEMPORÁRIO (pedido do dono, 23/11/2026). Um só interruptor do Admin, para todas as cartas: "Mostrar quem avaliou e
 * como". Desligado, os jogadores veem só a média dos colegas e quantos votaram; ligado, veem também quem deu cada nota.
 * O Admin vê sempre os detalhes.
 * <p>
 * É só para o início: depois de ligado, numa versão futura os detalhes ficam sempre visíveis e o interruptor sai.
 * Para o remover: apagar esta classe, o endpoint {@code /admin/avaliacoes/visibilidade} em {@link AvaliacaoController},
 * o bloco {@code id="interruptor-avaliacoes"} em {@code estatisticas.html} e, em
 * {@link AvaliacaoService#podeVerDetalhes}, devolver sempre true. A linha da tabela {@code configuracao} pode ficar.
 */
@Service
public class InterruptorDetalhesAvaliacoes {

    static final String CHAVE = "avaliacoes.detalhes-visiveis";

    private final ConfiguracaoRepository configuracoes;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;

    public InterruptorDetalhesAvaliacoes(ConfiguracaoRepository configuracoes, UtilizadorRepository utilizadores,
                                         ApplicationEventPublisher eventos) {
        this.configuracoes = configuracoes;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public boolean ligado() {
        return configuracoes.findById(CHAVE).map(c -> "true".equals(c.getValor())).orElse(false);
    }

    @Transactional
    public void definir(boolean ligado, String usernameAdmin) {
        Utilizador admin = utilizadores.findByUsernameIgnoreCase(usernameAdmin)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        if (!admin.temRole(Role.ADMIN) || admin.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Admin pode fazer isto.");
        }
        Configuracao c = configuracoes.findById(CHAVE).orElseGet(() -> new Configuracao(CHAVE, null));
        c.definir(Boolean.toString(ligado));
        configuracoes.save(c);
        eventos.publishEvent(new AuditEvent(admin.getUsername(), Acao.ALTERACAO_VISIBILIDADE_AVALIACOES, null,
            "CONFIGURACAO", null, ligado
                ? admin.getNome() + " mostrou a todos quem avaliou e como"
                : admin.getNome() + " escondeu quem avaliou e como"));
    }
}
