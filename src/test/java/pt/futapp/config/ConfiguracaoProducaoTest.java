package pt.futapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Produção é sempre PostgreSQL: nada na configuração de produção tem ligação ao H2. */
class ConfiguracaoProducaoTest {

    @Test
    void producaoSemH2ESemBaseDeDadosDeReserva() throws Exception {
        String prod = new ClassPathResource("application-prod.properties").getContentAsString(StandardCharsets.UTF_8);
        String semComentarios = prod.replaceAll("(?m)^#.*$", "");

        assertThat(semComentarios.toLowerCase()).doesNotContain("h2").doesNotContain("futapp_dados");
        // DATABASE_URL obrigatória: sem valor por omissão (sem ela a app não arranca)
        assertThat(semComentarios).contains("spring.datasource.url=${DATABASE_URL}\n");
    }

    @Test
    void emProducaoAAppRecusaArrancarSemPostgreSQL() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BaseDadosProducao.verificar(null))
            .hasMessageContaining("DATABASE_URL");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BaseDadosProducao.verificar("${DATABASE_URL}"))
            .hasMessageContaining("DATABASE_URL");
        BaseDadosProducao.verificar("jdbc:postgresql://servidor/neondb?sslmode=require");
    }
}
