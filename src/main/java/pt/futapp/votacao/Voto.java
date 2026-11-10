package pt.futapp.votacao;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pt.futapp.jogo.Inscricao;
import pt.futapp.jogo.Jogo;
import pt.futapp.utilizador.Utilizador;

/**
 * Um voto num jogo. {@code premio} null = voto no MVP.
 * O votado é uma inscrição (assim os convidados também recebem votos).
 * Um voto anulado pelo Admin fica guardado mas não conta.
 */
@Entity
@Table(name = "voto")
public class Voto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "jogo_id")
    private Jogo jogo;

    @ManyToOne
    @JoinColumn(name = "premio_id")
    private Premio premio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "eleitor_id")
    private Utilizador eleitor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "votado_id")
    private Inscricao votado;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "anulado", nullable = false, columnDefinition = "boolean default false")
    private boolean anulado;

    @ManyToOne
    @JoinColumn(name = "anulado_por_id")
    private Utilizador anuladoPor;

    protected Voto() {
    }

    public Voto(Jogo jogo, Premio premio, Utilizador eleitor, Inscricao votado, LocalDateTime criadoEm) {
        this.jogo = jogo;
        this.premio = premio;
        this.eleitor = eleitor;
        this.votado = votado;
        this.criadoEm = criadoEm;
    }

    public void anular(Utilizador admin) {
        this.anulado = true;
        this.anuladoPor = admin;
    }

    public boolean isMvp() {
        return premio == null;
    }

    public Long getId() { return id; }
    public Jogo getJogo() { return jogo; }
    public Premio getPremio() { return premio; }
    public Utilizador getEleitor() { return eleitor; }
    public Inscricao getVotado() { return votado; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public boolean isAnulado() { return anulado; }
    public Utilizador getAnuladoPor() { return anuladoPor; }
}
