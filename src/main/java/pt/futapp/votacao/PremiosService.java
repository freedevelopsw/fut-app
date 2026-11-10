package pt.futapp.votacao;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;

/**
 * Prémios brincadeira: o Organizador e o Admin criam, editam, ativam e desativam.
 * Não se apagam (desativar mantém o histórico de quem os ganhou). Nas votações só aparecem os ativos.
 */
@Service
public class PremiosService {

    /** Prémios criados no primeiro arranque, todos ativos. */
    static final String[][] INICIAIS = {
        {"Frango da noite", "O erro mais caricato de quem esteve à baliza."},
        {"Pior falhanço", "O golo que era impossível falhar."},
        {"Muralha", "O melhor a defender."},
        {"Assistência de ouro", "O passe da noite."},
        {"Golo da noite", "O golo mais bonito."},
        {"Mais queixinhas", "Reclamou com o árbitro, com os colegas e com a bola."},
        {"Fominha", "Nunca passou a bola."},
        {"Pulmão", "Quem mais correu."},
        {"Fantasma", "Esteve em campo, mas ninguém deu por ele."},
        {"Ator de Hollywood", "A melhor simulação de falta."},
    };

    private final PremioRepository premios;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public PremiosService(PremioRepository premios, UtilizadorRepository utilizadores,
                          ApplicationEventPublisher eventos, Clock clock) {
        this.premios = premios;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.clock = clock;
    }

    /** Cria os prémios iniciais se ainda não houver nenhum. */
    @Transactional
    public void criarIniciaisSeVazio() {
        if (premios.count() > 0) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        for (int i = 0; i < INICIAIS.length; i++) {
            premios.save(new Premio(INICIAIS[i][0], INICIAIS[i][1], true, i + 1, agora));
        }
    }

    @Transactional(readOnly = true)
    public List<Premio> todos() {
        return premios.findAllByOrderByOrdemAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Premio> ativos() {
        return premios.findByAtivoTrueOrderByOrdemAscIdAsc();
    }

    @Transactional
    public Premio criar(String nome, String descricao, String username) {
        Utilizador ator = exigirGestor(username);
        String n = validarNome(nome);
        if (premios.existsByNomeIgnoreCase(n)) {
            throw new OperacaoInvalidaException("Já existe um prémio com esse nome.");
        }
        int ordem = premios.findAllByOrderByOrdemAscIdAsc().stream().mapToInt(Premio::getOrdem).max().orElse(0) + 1;
        Premio p = premios.save(new Premio(n, limparDescricao(descricao), true, ordem, LocalDateTime.now(clock)));
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.CRIACAO_PREMIO, null, "PREMIO", p.getId(),
            "Prémio criado: " + p.getNome()));
        return p;
    }

    @Transactional
    public void atualizar(Long id, String nome, String descricao, String username) {
        Utilizador ator = exigirGestor(username);
        Premio p = carregar(id);
        String n = validarNome(nome);
        if (premios.existsByNomeIgnoreCaseAndIdNot(n, id)) {
            throw new OperacaoInvalidaException("Já existe um prémio com esse nome.");
        }
        String antes = p.getNome();
        p.atualizar(n, limparDescricao(descricao));
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.ALTERACAO_PREMIO, null, "PREMIO", p.getId(),
            antes.equals(n) ? "Prémio editado: " + n : "Prémio renomeado: " + antes + " → " + n));
    }

    @Transactional
    public void definirAtivo(Long id, boolean ativo, String username) {
        Utilizador ator = exigirGestor(username);
        Premio p = carregar(id);
        if (p.isAtivo() == ativo) {
            return;
        }
        p.definirAtivo(ativo);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.ALTERACAO_PREMIO, null, "PREMIO", p.getId(),
            "Prémio " + (ativo ? "ativado" : "desativado") + ": " + p.getNome()));
    }

    private static String validarNome(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty() || n.length() > 40) {
            throw new OperacaoInvalidaException("O nome do prémio é obrigatório (até 40 caracteres).");
        }
        return n;
    }

    private static String limparDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        String d = descricao.trim();
        if (d.length() > 120) {
            throw new OperacaoInvalidaException("A descrição tem no máximo 120 caracteres.");
        }
        return d;
    }

    private Premio carregar(Long id) {
        return premios.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Prémio não encontrado."));
    }

    private Utilizador exigirGestor(String username) {
        Utilizador u = utilizadores.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
        if (!u.ehGestor() || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Organizador ou o Admin podem gerir os prémios.");
        }
        return u;
    }
}
