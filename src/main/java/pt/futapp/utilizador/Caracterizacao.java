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

    @Column(name = "resistencia")
    private Integer resistencia;

    public Caracterizacao() {
    }

    public Caracterizacao(Posicao posicao, Posicao posicaoSecundaria, Pe pe,
                          Integer velocidade, Integer tecnica, Integer remate, Integer passe, Integer defesa,
                          Integer resistencia) {
        this.posicao = posicao;
        this.posicaoSecundaria = posicaoSecundaria;
        this.pe = pe;
        this.velocidade = velocidade;
        this.tecnica = tecnica;
        this.remate = remate;
        this.passe = passe;
        this.defesa = defesa;
        this.resistencia = resistencia;
    }

    /**
     * Média dos atributos preenchidos, sem arredondar; null se nenhum estiver preenchido. É o rating que a carta e as
     * Cartas mostram, com uma casa decimal (8,2).
     */
    public Double media() {
        var valores = Stream.of(velocidade, tecnica, remate, passe, defesa, resistencia).filter(Objects::nonNull).toList();
        if (valores.isEmpty()) {
            return null;
        }
        return valores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** A média arredondada às unidades; null se nenhum atributo estiver preenchido. Usada a gerar as equipas. */
    public Integer rating() {
        Double media = media();
        return media == null ? null : (int) Math.round(media);
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
    public Integer getResistencia() { return resistencia; }
}
