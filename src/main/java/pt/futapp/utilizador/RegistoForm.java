package pt.futapp.utilizador;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistoForm {

    @NotBlank(message = "Escolhe um nome de utilizador.")
    @Size(min = 3, max = 40, message = "O utilizador deve ter entre 3 e 40 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9._-]*", message = "Usa só letras, números, ponto, hífen ou underscore.")
    private String username;

    @NotBlank(message = "Diz-nos o teu nome.")
    @Size(max = 60, message = "O nome tem no máximo 60 caracteres.")
    private String nome;

    @NotBlank(message = "Escolhe uma password.")
    @Size(min = 6, max = 100, message = "A password deve ter pelo menos 6 caracteres.")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
