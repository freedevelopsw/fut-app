package pt.futapp.votacao;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Prémio brincadeira votado no fim de cada jogo (o MVP não é um prémio: é fixo). */
@Entity
@Table(name = "premio")
public class Premio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nome;

    @Column(length = 120)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo;

    @Column(nullable = false)
    private int ordem;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Premio() {
    }

    public Premio(String nome, String descricao, boolean ativo, int ordem, LocalDateTime criadoEm) {
        this.nome = nome;
        this.descricao = descricao;
        this.ativo = ativo;
        this.ordem = ordem;
        this.criadoEm = criadoEm;
    }

    public void atualizar(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public boolean isAtivo() { return ativo; }
    public int getOrdem() { return ordem; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
