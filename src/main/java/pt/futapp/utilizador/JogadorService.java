package pt.futapp.utilizador;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.Nomes;
import pt.futapp.comum.OperacaoInvalidaException;

/** Caracterização dos jogadores: a carta (visível a todos) e a edição (pelo próprio ou pelo Admin). */
@Service
public class JogadorService {

    private final UtilizadorRepository repo;
    private final ApplicationEventPublisher eventos;

    public JogadorService(UtilizadorRepository repo, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.eventos = eventos;
    }

    /** Jogadores ativos, por nome visível. */
    @Transactional(readOnly = true)
    public List<JogadorResumo> jogadores(String usernameAtual) {
        return repo.findByBloqueadoFalseOrderByNomeAsc().stream()
            .sorted(Comparator.comparing(u -> u.getNomeVisivel().toLowerCase()))
            .map(u -> {
                Caracterizacao c = u.getCaracterizacao();
                return new JogadorResumo(u.getId(), u.getNomeVisivel(), u.getNome(), Nomes.iniciais(u.getNomeVisivel()),
                    c.getPosicao() != null ? c.getPosicao().getSigla() : null, c.rating(),
                    u.getUsername().equalsIgnoreCase(usernameAtual));
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public CartaView carta(Long jogadorId, String usernameAtual) {
        Utilizador u = carregar(jogadorId);
        Utilizador atual = carregarPorUsername(usernameAtual);
        Caracterizacao c = u.getCaracterizacao();
        List<CartaView.Atributo> atributos = List.of(
            new CartaView.Atributo("Velocidade", c.getVelocidade()),
            new CartaView.Atributo("Técnica", c.getTecnica()),
            new CartaView.Atributo("Remate", c.getRemate()),
            new CartaView.Atributo("Passe", c.getPasse()),
            new CartaView.Atributo("Defesa", c.getDefesa()));
        boolean eu = u.getId().equals(atual.getId());
        List<NivelAcesso> opcoes = UtilizadorService.opcoesNivel(atual, u);
        return new CartaView(u.getId(), u.getNome(), u.getAlcunha(), u.getNomeVisivel(), Nomes.iniciais(u.getNomeVisivel()),
            c.getPosicao() != null ? c.getPosicao().getSigla() : null,
            c.getPosicao() != null ? c.getPosicao().getDescricao() : null,
            c.getPosicaoSecundaria() != null ? c.getPosicaoSecundaria().getDescricao() : null,
            c.getPe() != null ? c.getPe().getDescricao() : null,
            c.rating(), atributos, !c.vazia(), u.isBloqueado(), eu, podeEditar(atual, u),
            NivelAcesso.de(u), atual.ehGestor() && !atual.isBloqueado(), opcoes,
            opcoes.isEmpty() ? UtilizadorService.motivoSemPermissao(atual, u) : null);
    }

    /** Dados atuais para preencher o formulário. Só o próprio ou o Admin. */
    @Transactional(readOnly = true)
    public DadosForm formulario(Long jogadorId, String usernameAtual) {
        Utilizador u = carregar(jogadorId);
        exigirPodeEditar(carregarPorUsername(usernameAtual), u);
        return DadosForm.de(u);
    }

    @Transactional
    public void atualizar(Long jogadorId, DadosForm form, String usernameAtual) {
        Utilizador atual = carregarPorUsername(usernameAtual);
        Utilizador u = carregar(jogadorId);
        exigirPodeEditar(atual, u);

        String nome = form.getNome() == null ? "" : form.getNome().trim();
        if (nome.isEmpty() || nome.length() > 60) {
            throw new OperacaoInvalidaException("O nome é obrigatório (até 60 caracteres).");
        }
        String alcunha = form.getAlcunha() == null || form.getAlcunha().isBlank() ? null : form.getAlcunha().trim();
        if (alcunha != null && alcunha.length() > 30) {
            throw new OperacaoInvalidaException("A alcunha tem no máximo 30 caracteres.");
        }
        if (form.getPosicao() != null && form.getPosicao() == form.getPosicaoSecundaria()) {
            throw new OperacaoInvalidaException("A posição secundária tem de ser diferente da principal.");
        }
        for (Integer v : new Integer[] {form.getVelocidade(), form.getTecnica(), form.getRemate(), form.getPasse(), form.getDefesa()}) {
            if (v != null && (v < 1 || v > 10)) {
                throw new OperacaoInvalidaException("Os atributos são de 1 a 10.");
            }
        }
        Posicao secundaria = form.getPosicao() == null ? null : form.getPosicaoSecundaria();
        u.atualizarDados(nome, alcunha, new Caracterizacao(form.getPosicao(), secundaria, form.getPe(),
            form.getVelocidade(), form.getTecnica(), form.getRemate(), form.getPasse(), form.getDefesa()));

        String texto = Objects.equals(atual.getId(), u.getId())
            ? u.getNome() + " atualizou os seus dados"
            : atual.getNome() + " atualizou os dados de " + u.getNome();
        Integer rating = u.getCaracterizacao().rating();
        eventos.publishEvent(new AuditEvent(atual.getUsername(), Acao.ALTERACAO_DADOS, null, "UTILIZADOR", u.getId(),
            texto + (rating != null ? " (rating " + rating + ")" : "")));
    }

    @Transactional(readOnly = true)
    public Long idDe(String username) {
        return carregarPorUsername(username).getId();
    }

    private static boolean podeEditar(Utilizador atual, Utilizador alvo) {
        return atual.getId().equals(alvo.getId()) || (atual.temRole(Role.ADMIN) && !atual.isBloqueado());
    }

    private static void exigirPodeEditar(Utilizador atual, Utilizador alvo) {
        if (!podeEditar(atual, alvo)) {
            throw new OperacaoInvalidaException("Só podes alterar os teus próprios dados.");
        }
    }

    private Utilizador carregar(Long id) {
        return repo.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Jogador não encontrado."));
    }

    private Utilizador carregarPorUsername(String username) {
        return repo.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
    }
}
