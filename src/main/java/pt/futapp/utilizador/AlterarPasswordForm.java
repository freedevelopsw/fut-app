package pt.futapp.utilizador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AlterarPasswordForm {

    @NotBlank(message = "Escreve a tua password atual.")
    private String atual;

    @NotBlank(message = "Escolhe a nova password.")
    @Size(min = 6, max = 100, message = "A nova password deve ter pelo menos 6 caracteres.")
    private String nova;

    @NotBlank(message = "Repete a nova password.")
    private String confirmacao;

    public String getAtual() { return atual; }
    public void setAtual(String atual) { this.atual = atual; }
    public String getNova() { return nova; }
    public void setNova(String nova) { this.nova = nova; }
    public String getConfirmacao() { return confirmacao; }
    public void setConfirmacao(String confirmacao) { this.confirmacao = confirmacao; }
}
