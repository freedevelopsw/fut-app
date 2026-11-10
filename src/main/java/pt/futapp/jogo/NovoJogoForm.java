package pt.futapp.jogo;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class NovoJogoForm {

    @NotNull(message = "Indica a data e a hora do jogo.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dataHora;

    @NotBlank(message = "Indica o local do jogo.")
    @Size(max = 120, message = "O local tem no máximo 120 caracteres.")
    private String local;

    @Min(value = 2, message = "Pelo menos 2 vagas.")
    @Max(value = 40, message = "No máximo 40 vagas.")
    private int vagas = 12;

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }
    public int getVagas() { return vagas; }
    public void setVagas(int vagas) { this.vagas = vagas; }
}
