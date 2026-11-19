package pt.futapp.comum;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomesTest {

    @Test
    void nomeComAlcunhaQuandoCabe() {
        assertThat(Nomes.nomeComAlcunha("João Silva", "Jota")).isEqualTo("João Silva (Jota)");
        assertThat(Nomes.nomeComAlcunha("João Silva", null)).isEqualTo("João Silva");
        assertThat(Nomes.nomeComAlcunha("João Silva", "  ")).isEqualTo("João Silva");
        assertThat(Nomes.nomeComAlcunha("Rui", "rui")).isEqualTo("Rui");
    }

    @Test
    void soONomeQuandoNaoCabe() {
        String nome = "Bartolomeu Fernandes";
        assertThat(Nomes.nomeComAlcunha(nome, "Grande")).isEqualTo("Bartolomeu Fernandes (Grande)"); // 29 caracteres
        assertThat(Nomes.nomeComAlcunha(nome, "O Grande")).isEqualTo(nome); // 31 caracteres
    }
}
