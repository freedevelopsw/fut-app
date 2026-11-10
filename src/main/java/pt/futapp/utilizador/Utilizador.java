package pt.futapp.utilizador;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

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

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utilizador_role", joinColumns = @JoinColumn(name = "utilizador_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /** Um utilizador bloqueado não consegue entrar, mas o histórico (inscrições, golos, votos) mantém-se. */
    @Column(name = "bloqueado", nullable = false, columnDefinition = "boolean default false")
    private boolean bloqueado;

    @Column(name = "bloqueado_em")
    private LocalDateTime bloqueadoEm;

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

    public void alterarPasswordHash(String novoHash) {
        this.passwordHash = novoHash;
    }

    public void desbloquear() {
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
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return roles; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public boolean isBloqueado() { return bloqueado; }
    public LocalDateTime getBloqueadoEm() { return bloqueadoEm; }
}
