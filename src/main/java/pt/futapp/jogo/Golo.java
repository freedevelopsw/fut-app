package pt.futapp.jogo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pt.futapp.comum.conversores.EquipaConverter;
import pt.futapp.utilizador.Utilizador;

/**
 * Um golo de um jogo (Fase 3). O marcador e a assistência são inscrições (assim os convidados também marcam).
 * Um golo sem marcador conta para o resultado mas não para ninguém.
 */
@Entity
@Table(name = "golo")
public class Golo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "jogo_id")
    private Jogo jogo;

    @Convert(converter = EquipaConverter.class)
    @Column(nullable = false, length = 20)
    private Equipa equipa;

    @ManyToOne
    @JoinColumn(name = "marcador_id")
    private Inscricao marcador;

    @ManyToOne
    @JoinColumn(name = "assistencia_id")
    private Inscricao assistencia;

    @Column(name = "registado_em", nullable = false)
    private LocalDateTime registadoEm;

    @ManyToOne
    @JoinColumn(name = "registado_por_id")
    private Utilizador registadoPor;

    protected Golo() {
    }

    public Golo(Jogo jogo, Equipa equipa, Inscricao marcador, Inscricao assistencia,
                LocalDateTime registadoEm, Utilizador registadoPor) {
        this.jogo = jogo;
        this.equipa = equipa;
        this.marcador = marcador;
        this.assistencia = assistencia;
        this.registadoEm = registadoEm;
        this.registadoPor = registadoPor;
    }

    public Long getId() { return id; }
    public Jogo getJogo() { return jogo; }
    public Equipa getEquipa() { return equipa; }
    public Inscricao getMarcador() { return marcador; }
    public Inscricao getAssistencia() { return assistencia; }
    public LocalDateTime getRegistadoEm() { return registadoEm; }
    public Utilizador getRegistadoPor() { return registadoPor; }
}
