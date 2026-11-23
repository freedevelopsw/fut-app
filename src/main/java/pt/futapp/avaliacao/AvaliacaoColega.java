package pt.futapp.avaliacao;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pt.futapp.utilizador.Utilizador;

/**
 * A avaliação que um jogador dá a um colega: as mesmas seis notas da carta (1 a 10, todas opcionais).
 * Uma por par avaliador/avaliado; voltar a avaliar substitui as notas.
 */
@Entity
@Table(name = "avaliacao_colega")
public class AvaliacaoColega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "avaliador_id")
    private Utilizador avaliador;

    @ManyToOne(optional = false)
    @JoinColumn(name = "avaliado_id")
    private Utilizador avaliado;

    @Column(name = "velocidade")
    private Integer velocidade;

    @Column(name = "tecnica")
    private Integer tecnica;

    @Column(name = "remate")
    private Integer remate;

    @Column(name = "passe")
    private Integer passe;

    @Column(name = "defesa")
    private Integer defesa;

    @Column(name = "resistencia")
    private Integer resistencia;

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm;

    protected AvaliacaoColega() {
    }

    public AvaliacaoColega(Utilizador avaliador, Utilizador avaliado) {
        this.avaliador = avaliador;
        this.avaliado = avaliado;
    }

    public void definir(Notas notas, LocalDateTime quando) {
        this.velocidade = notas.velocidade();
        this.tecnica = notas.tecnica();
        this.remate = notas.remate();
        this.passe = notas.passe();
        this.defesa = notas.defesa();
        this.resistencia = notas.resistencia();
        this.atualizadaEm = quando;
    }

    public Notas notas() {
        return new Notas(velocidade, tecnica, remate, passe, defesa, resistencia);
    }

    public Long getId() { return id; }
    public Utilizador getAvaliador() { return avaliador; }
    public Utilizador getAvaliado() { return avaliado; }
    public LocalDateTime getAtualizadaEm() { return atualizadaEm; }
}
