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

    public boolean equipasPublicadas() {
        return estado == EstadoJogo.EQUIPAS_PUBLICADAS;
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

    public Long getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getLocal() { return local; }
    public int getVagas() { return vagas; }
    public EstadoJogo getEstado() { return estado; }
    public Utilizador getCriadoPor() { return criadoPor; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
