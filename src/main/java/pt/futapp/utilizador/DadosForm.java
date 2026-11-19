package pt.futapp.utilizador;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados do jogador. Só o nome é obrigatório. */
public class DadosForm {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 60, message = "O nome tem no máximo 60 caracteres.")
    private String nome;

    @Size(max = 30, message = "A alcunha tem no máximo 30 caracteres.")
    private String alcunha;

    private Posicao posicao;
    private Posicao posicaoSecundaria;
    private Pe pe;

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

    public static DadosForm de(Utilizador u) {
        DadosForm f = new DadosForm();
        Caracterizacao c = u.getCaracterizacao();
        f.nome = u.getNome();
        f.alcunha = u.getAlcunha();
        f.posicao = c.getPosicao();
        f.posicaoSecundaria = c.getPosicaoSecundaria();
        f.pe = c.getPe();
        f.velocidade = c.getVelocidade();
        f.tecnica = c.getTecnica();
        f.remate = c.getRemate();
        f.passe = c.getPasse();
        f.defesa = c.getDefesa();
        f.resistencia = c.getResistencia();
        return f;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getAlcunha() { return alcunha; }
    public void setAlcunha(String alcunha) { this.alcunha = alcunha; }
    public Posicao getPosicao() { return posicao; }
    public void setPosicao(Posicao posicao) { this.posicao = posicao; }
    public Posicao getPosicaoSecundaria() { return posicaoSecundaria; }
    public void setPosicaoSecundaria(Posicao posicaoSecundaria) { this.posicaoSecundaria = posicaoSecundaria; }
    public Pe getPe() { return pe; }
    public void setPe(Pe pe) { this.pe = pe; }
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
