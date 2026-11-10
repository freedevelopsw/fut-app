package pt.futapp.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import pt.futapp.comum.conversores.AcaoConverter;

@Entity
@Table(name = "audit_evento", indexes = {
    @Index(name = "idx_audit_data_hora", columnList = "data_hora"),
    @Index(name = "idx_audit_jogo", columnList = "jogo_id"),
    @Index(name = "idx_audit_ator", columnList = "ator")
})
public class AuditEvento {

    private static final int MAX_DETALHES = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(name = "ator", length = 40)
    private String ator;

    @Convert(converter = AcaoConverter.class)
    @Column(name = "acao", nullable = false, length = 40)
    private Acao acao;

    @Column(name = "jogo_id")
    private Long jogoId;

    @Column(name = "entidade_tipo", length = 30)
    private String entidadeTipo;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(name = "detalhes", length = MAX_DETALHES)
    private String detalhes;

    protected AuditEvento() {
    }

    public AuditEvento(LocalDateTime dataHora, AuditEvent evento) {
        this.dataHora = dataHora;
        this.ator = evento.ator();
        this.acao = evento.acao();
        this.jogoId = evento.jogoId();
        this.entidadeTipo = evento.entidadeTipo();
        this.entidadeId = evento.entidadeId();
        String texto = evento.detalhes();
        this.detalhes = texto != null && texto.length() > MAX_DETALHES ? texto.substring(0, MAX_DETALHES) : texto;
    }

    public Long getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getAtor() { return ator; }
    public Acao getAcao() { return acao; }
    public Long getJogoId() { return jogoId; }
    public String getEntidadeTipo() { return entidadeTipo; }
    public Long getEntidadeId() { return entidadeId; }
    public String getDetalhes() { return detalhes; }
}
