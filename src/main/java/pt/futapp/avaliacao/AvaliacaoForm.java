package pt.futapp.avaliacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** As notas que um jogador dá a um colega. Todas opcionais; tudo vazio apaga a avaliação. */
public class AvaliacaoForm {

    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer velocidade;
    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer tecnica;
    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer remate;
    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer passe;
    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer defesa;
    @Min(value = 1, message = "Entre 1 e 10.") @Max(value = 10, message = "Entre 1 e 10.")
    private Integer resistencia;

    public static AvaliacaoForm de(Notas n) {
        AvaliacaoForm f = new AvaliacaoForm();
        f.velocidade = n.velocidade();
        f.tecnica = n.tecnica();
        f.remate = n.remate();
        f.passe = n.passe();
        f.defesa = n.defesa();
        f.resistencia = n.resistencia();
        return f;
    }

    public Notas notas() {
        return new Notas(velocidade, tecnica, remate, passe, defesa, resistencia);
    }

    public Integer getVelocidade() { return velocidade; }
    public void setVelocidade(Integer velocidade) { this.velocidade = velocidade; }
    public Integer getTecnica() { return tecnica; }
    public void setTecnica(Integer tecnica) { this.tecnica = tecnica; }
    public Integer getRemate() { return remate; }
    public void setRemate(Integer remate) { this.remate = remate; }
    public Integer getPasse() { return passe; }
    public void setPasse(Integer passe) { this.passe = passe; }
    public Integer getDefesa() { return defesa; }
    public void setDefesa(Integer defesa) { this.defesa = defesa; }
    public Integer getResistencia() { return resistencia; }
    public void setResistencia(Integer resistencia) { this.resistencia = resistencia; }
}
