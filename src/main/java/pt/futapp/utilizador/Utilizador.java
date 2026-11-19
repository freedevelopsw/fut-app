package pt.futapp.utilizador;

import pt.futapp.comum.OperacaoInvalidaException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import pt.futapp.comum.conversores.RoleConverter;

@Entity
@Table(name = "utilizador")
public class Utilizador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, length = 60)
    private String nome;

    /** Opcional: quando existe, é o nome que aparece nas listas. */
    @Column(length = 30)
    private String alcunha;

    @Embedded
    private Caracterizacao caracterizacao;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utilizador_role", joinColumns = @JoinColumn(name = "utilizador_id"))
    @Convert(converter = RoleConverter.class)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Um utilizador bloqueado não consegue entrar, mas o histórico (inscrições, golos, votos) mantém-se. */
    @Column(name = "bloqueado", nullable = false, columnDefinition = "boolean default false")
    private boolean bloqueado;

    @Column(name = "bloqueado_em")
    private LocalDateTime bloqueadoEm;

    /**
     * Removido pelo Admin (soft delete). A linha fica para não partir o histórico (votos que deu, golos que registou,
     * jogos que criou), mas a pessoa desaparece da app: não entra, não aparece em listas e nos jogos antigos passa a
     * convidado. O username é libertado.
     */
    @Column(name = "removido", nullable = false, columnDefinition = "boolean default false")
    private boolean removido;

    @Column(name = "removido_em")
    private LocalDateTime removidoEm;

    protected Utilizador() {
    }

    public Utilizador(String username, String nome, String passwordHash, Set<Role> roles, LocalDateTime criadoEm) {
        this.username = username;
        this.nome = nome;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
        this.criadoEm = criadoEm;
    }

    public void bloquear(LocalDateTime quando) {
        this.bloqueado = true;
        this.bloqueadoEm = quando;
    }

    /** Nome que aparece nas listas: a alcunha, se existir, senão o nome. */
    public String getNomeVisivel() {
        return alcunha != null && !alcunha.isBlank() ? alcunha : nome;
    }

    public void atualizarDados(String nome, String alcunha, Caracterizacao caracterizacao) {
        exigirNaoRemovido();
        this.nome = nome;
        this.alcunha = alcunha;
        this.caracterizacao = caracterizacao;
    }

    public void alterarPasswordHash(String novoHash) {
        exigirNaoRemovido();
        this.passwordHash = novoHash;
    }

    /** Prefixo dos usernames das contas removidas; ninguém se pode registar com ele. */
    public static final String PREFIXO_REMOVIDO = "removido-";

    public static boolean usernameReservado(String username) {
        return username != null && username.trim().toLowerCase(java.util.Locale.ROOT).startsWith(PREFIXO_REMOVIDO);
    }

    /**
     * Soft delete, sem volta: fica bloqueado para sempre e o username passa a {@code novoUsername}
     * ("removido-{id}"), o que liberta o antigo para outra conta.
     */
    public void marcarRemovido(LocalDateTime quando, String novoUsername) {
        exigirNaoRemovido();
        if (!usernameReservado(novoUsername)) {
            throw new IllegalArgumentException("O username de uma conta removida começa por " + PREFIXO_REMOVIDO);
        }
        this.removido = true;
        this.removidoEm = quando;
        if (!bloqueado) {
            bloquear(quando);
        }
        this.username = novoUsername;
    }

    /**
     * Garantia única de que uma conta removida não volta: desbloquear, mudar roles, dados ou password recusam.
     * Os serviços também a escondem (UtilizadorRepository.findMembro), mas esta regra vale mesmo que não.
     */
    private void exigirNaoRemovido() {
        if (removido) {
            throw new OperacaoInvalidaException("Este membro foi removido e não pode ser alterado.");
        }
    }

    /** Substitui as roles (nível de acesso). */
    public void definirRoles(java.util.Collection<Role> novas) {
        exigirNaoRemovido();
        roles.clear();
        roles.addAll(novas);
    }

    public void desbloquear() {
        exigirNaoRemovido();
        this.bloqueado = false;
        this.bloqueadoEm = null;
    }

    public boolean temRole(Role role) {
        return roles.contains(role);
    }

    /** Organizador ou Admin: pode gerir jogos. */
    public boolean ehGestor() {
        return temRole(Role.ORGANIZADOR) || temRole(Role.ADMIN);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getNome() { return nome; }
    public String getAlcunha() { return alcunha; }

    /** Nunca null (o Hibernate devolve null quando todas as colunas estão vazias). */
    public Caracterizacao getCaracterizacao() {
        return caracterizacao != null ? caracterizacao : new Caracterizacao();
    }
    public String getPasswordHash() { return passwordHash; }
    /** Só leitura: para mudar, {@link #definirRoles}. */
    public Set<Role> getRoles() { return java.util.Collections.unmodifiableSet(roles); }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public boolean isBloqueado() { return bloqueado; }
    public LocalDateTime getBloqueadoEm() { return bloqueadoEm; }
    public boolean isRemovido() { return removido; }
    public LocalDateTime getRemovidoEm() { return removidoEm; }
}
