package pt.futapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class UrlBaseDadosTest {

    @Test
    void converteOLinkDoNeonParaJdbc() {
        Map<String, String> p = UrlBaseDados.propriedades(
            "postgresql://neondb_owner:abc%40123@ep-cool-rain-a1b2c3.eu-central-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

        assertThat(p).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-cool-rain-a1b2c3.eu-central-1.aws.neon.tech/neondb?sslmode=require")
            .containsEntry("spring.datasource.username", "neondb_owner")
            .containsEntry("spring.datasource.password", "abc@123");
    }

    @Test
    void mantemAPortaEAceitaPostgresCurto() {
        Map<String, String> p = UrlBaseDados.propriedades("postgres://u:p@servidor:6543/base");

        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://servidor:6543/base")
            .containsEntry("spring.datasource.username", "u")
            .containsEntry("spring.datasource.password", "p");
    }

    @Test
    void umUrlJdbcOuVazioFicaComoEsta() {
        assertThat(UrlBaseDados.propriedades("jdbc:postgresql://servidor/base")).isEmpty();
        assertThat(UrlBaseDados.propriedades("")).isEmpty();
        assertThat(UrlBaseDados.propriedades(null)).isEmpty();
    }
}
