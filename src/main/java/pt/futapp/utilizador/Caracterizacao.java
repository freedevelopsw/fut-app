package pt.futapp.utilizador;

import java.util.Objects;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import pt.futapp.comum.conversores.PeConverter;
import pt.futapp.comum.conversores.PosicaoConverter;

/**
 * Caracterização do jogador, preenchida pelo próprio (ou corrigida pelo Admin). Tudo é opcional.
 * Os atributos são uma auto-avaliação de 1 a 10.
 */
@Embeddable
public class Caracterizacao {

    @Convert(converter = PosicaoConverter.class)
    @Column(name = "posicao", length = 20)
    private Posicao posicao;

    @Convert(converter = PosicaoConverter.class)
    @Column(name = "posicao_secundaria", length = 20)
    private Posicao posicaoSecundaria;

    @Convert(converter = PeConverter.class)
    @Column(name = "pe", length = 10)
    private Pe pe;

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

    public Caracterizacao() {
    }

    public Caracterizacao(Posicao posicao, Posicao posicaoSecundaria, Pe pe,
                          Integer velocidade, Integer tecnica, Integer remate, Integer passe, Integer defesa) {
        this.posicao = posicao;
        this.posicaoSecundaria = posicaoSecundaria;
        this.pe = pe;
        this.velocidade = velocidade;
        this.tecnica = tecnica;
        this.remate = remate;
        this.passe = passe;
        this.defesa = defesa;
    }

    /** Média dos atributos preenchidos, arredondada; null se nenhum estiver preenchido. */
    public Integer rating() {
        var valores = Stream.of(velocidade, tecnica, remate, passe, defesa).filter(Objects::nonNull).toList();
        if (valores.isEmpty()) {
            return null;
        }
        return (int) Math.round(valores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    public boolean vazia() {
        return posicao == null && posicaoSecundaria == null && pe == null && rating() == null;
    }

    public Posicao getPosicao() { return posicao; }
    public Posicao getPosicaoSecundaria() { return posicaoSecundaria; }
    public Pe getPe() { return pe; }
    public Integer getVelocidade() { return velocidade; }
    public Integer getTecnica() { return tecnica; }
    public Integer getRemate() { return remate; }
    public Integer getPasse() { return passe; }
    public Integer getDefesa() { return defesa; }
}
