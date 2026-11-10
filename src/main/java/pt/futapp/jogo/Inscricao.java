package pt.futapp.jogo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pt.futapp.utilizador.Utilizador;

/**
 * Inscrição num jogo. Um convidado é uma inscrição sem conta: tem {@code nomeConvidado} e não tem jogador.
 * A posição (inscrito ou em espera) resulta da ordem de inscrição entre as inscrições ativas.
 */
@Entity
@Table(name = "inscricao")
public class Inscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "jogo_id")
    private Jogo jogo;

    @ManyToOne
    @JoinColumn(name = "jogador_id")
    private Utilizador jogador;

    @Column(name = "nome_convidado", length = 60)
    private String nomeConvidado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoInscricao estado;

    @Column(name = "inscrito_em", nullable = false)
    private LocalDateTime inscritoEm;

    @Column(name = "cancelado_em")
    private LocalDateTime canceladoEm;

    @ManyToOne
    @JoinColumn(name = "cancelado_por_id")
    private Utilizador canceladoPor;

    protected Inscricao() {
    }

    private Inscricao(Jogo jogo, Utilizador jogador, String nomeConvidado, LocalDateTime inscritoEm) {
        this.jogo = jogo;
        this.jogador = jogador;
        this.nomeConvidado = nomeConvidado;
        this.inscritoEm = inscritoEm;
        this.estado = EstadoInscricao.ATIVA;
    }

    public static Inscricao deJogador(Jogo jogo, Utilizador jogador, LocalDateTime quando) {
        return new Inscricao(jogo, jogador, null, quando);
    }

    public static Inscricao deConvidado(Jogo jogo, String nome, LocalDateTime quando) {
        return new Inscricao(jogo, null, nome, quando);
    }

    public void cancelar(Utilizador por, LocalDateTime quando) {
        this.estado = EstadoInscricao.CANCELADA;
        this.canceladoPor = por;
        this.canceladoEm = quando;
    }

    public boolean isConvidado() {
        return jogador == null;
    }

    public boolean ehDe(Utilizador utilizador) {
        return jogador != null && jogador.getId().equals(utilizador.getId());
    }

    public String getNomeVisivel() {
        return jogador != null ? jogador.getNome() : nomeConvidado;
    }

    public Long getId() { return id; }
    public Jogo getJogo() { return jogo; }
    public Utilizador getJogador() { return jogador; }
    public String getNomeConvidado() { return nomeConvidado; }
    public EstadoInscricao getEstado() { return estado; }
    public LocalDateTime getInscritoEm() { return inscritoEm; }
    public LocalDateTime getCanceladoEm() { return canceladoEm; }
    public Utilizador getCanceladoPor() { return canceladoPor; }
}
