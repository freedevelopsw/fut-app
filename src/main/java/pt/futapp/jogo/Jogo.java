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
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Utilizador;

@Entity
@Table(name = "jogo")
public class Jogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "local_jogo", nullable = false, length = 120)
    private String local;

    @Column(nullable = false)
    private int vagas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoJogo estado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criado_por_id")
    private Utilizador criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Jogo() {
    }

    public Jogo(LocalDateTime dataHora, String local, int vagas, Utilizador criadoPor, LocalDateTime criadoEm) {
        this.dataHora = dataHora;
        this.local = local;
        this.vagas = vagas;
        this.criadoPor = criadoPor;
        this.criadoEm = criadoEm;
        this.estado = EstadoJogo.CONVOCATORIA_ABERTA;
    }

    public boolean convocatoriaAberta() {
        return estado == EstadoJogo.CONVOCATORIA_ABERTA;
    }

    public void fecharConvocatoria() {
        if (!convocatoriaAberta()) {
            throw new OperacaoInvalidaException("A convocatória já está fechada.");
        }
        estado = EstadoJogo.CONVOCATORIA_FECHADA;
    }

    /** As equipas podem ser preparadas até serem publicadas e ajustadas depois, mas não após o início do registo. */
    public boolean permiteEquipas() {
        return estado == EstadoJogo.CONVOCATORIA_ABERTA || estado == EstadoJogo.CONVOCATORIA_FECHADA
            || estado == EstadoJogo.EQUIPAS_PUBLICADAS;
    }

    /** As equipas já foram publicadas (e continuam visíveis durante o registo, a votação e depois de fechado). */
    public boolean equipasPublicadas() {
        return estado == EstadoJogo.EQUIPAS_PUBLICADAS || estado == EstadoJogo.EM_REGISTO
            || estado == EstadoJogo.VOTACAO_ABERTA || estado == EstadoJogo.FECHADO;
    }

    public void publicarEquipas() {
        if (estado == EstadoJogo.CONVOCATORIA_ABERTA) {
            throw new OperacaoInvalidaException("Fecha primeiro a convocatória para publicares as equipas.");
        }
        if (estado != EstadoJogo.CONVOCATORIA_FECHADA && estado != EstadoJogo.EQUIPAS_PUBLICADAS) {
            throw new OperacaoInvalidaException("Já não é possível mudar as equipas deste jogo.");
        }
        estado = EstadoJogo.EQUIPAS_PUBLICADAS;
    }

    /** O registo do jogo (golos, resultado, faltas) é possível depois de publicar as equipas e até fechar o jogo. */
    public boolean permiteRegisto() {
        return estado == EstadoJogo.EQUIPAS_PUBLICADAS || estado == EstadoJogo.EM_REGISTO;
    }

    /** A primeira ação de registo passa o jogo para "Em registo"; a partir daí as equipas e a lista ficam fixas. */
    public void iniciarRegisto() {
        if (!permiteRegisto()) {
            throw new OperacaoInvalidaException(estado == EstadoJogo.VOTACAO_ABERTA || estado == EstadoJogo.FECHADO
                ? "O jogo já foi fechado. Reabre o registo para fazer alterações."
                : "Publica primeiro as equipas para registares o jogo.");
        }
        estado = EstadoJogo.EM_REGISTO;
    }

    /** Fecha o registo do jogo e abre a votação. */
    public void fecharJogo() {
        if (!permiteRegisto()) {
            throw new OperacaoInvalidaException("Só se fecha um jogo com as equipas publicadas e ainda por fechar.");
        }
        estado = EstadoJogo.VOTACAO_ABERTA;
    }

    public void reabrirRegisto() {
        if (estado != EstadoJogo.VOTACAO_ABERTA) {
            throw new OperacaoInvalidaException("Só se reabre o registo enquanto a votação está aberta.");
        }
        estado = EstadoJogo.EM_REGISTO;
    }

    public boolean votacaoAberta() {
        return estado == EstadoJogo.VOTACAO_ABERTA;
    }

    /** Fecha a votação: o jogo fica fechado e os resultados são revelados. */
    public void fecharVotacao() {
        if (estado != EstadoJogo.VOTACAO_ABERTA) {
            throw new OperacaoInvalidaException("A votação deste jogo não está aberta.");
        }
        estado = EstadoJogo.FECHADO;
    }

    public void reabrirVotacao() {
        if (estado != EstadoJogo.FECHADO) {
            throw new OperacaoInvalidaException("Só se reabre a votação de um jogo fechado.");
        }
        estado = EstadoJogo.VOTACAO_ABERTA;
    }

    /** Já houve registo do jogo (há resultado para mostrar). */
    public boolean temResultado() {
        return estado == EstadoJogo.EM_REGISTO || estado == EstadoJogo.VOTACAO_ABERTA || estado == EstadoJogo.FECHADO;
    }

    public Long getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getLocal() { return local; }
    public int getVagas() { return vagas; }
    public EstadoJogo getEstado() { return estado; }
    public Utilizador getCriadoPor() { return criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
