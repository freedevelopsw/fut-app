package pt.futapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Simula uma base de dados antiga com uma coluna ENUM e confirma que o arranque a converte para texto. */
@SpringBootTest
@ActiveProfiles("test")
class AjusteEsquemaTest {

    @Autowired private AjusteEsquema ajuste;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.execute("DROP TABLE IF EXISTS teste_antigo");
    }

    @Test
    void asColunasEnumAntigasPassamATextoEAceitamValoresNovos() throws Exception {
        jdbc.execute("CREATE TABLE teste_antigo (id INT PRIMARY KEY, estado ENUM('ABERTO','FECHADO'))");

        int convertidas = ajuste.ajustar();

        assertThat(convertidas).isEqualTo(1);
        String tipo = jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'TESTE_ANTIGO' AND COLUMN_NAME = 'ESTADO'", String.class);
        assertThat(tipo).isEqualTo("CHARACTER VARYING");
        jdbc.update("INSERT INTO teste_antigo VALUES (1, 'CANCELADO')");
    }

    @Test
    void asTabelasDaAppJaNaoTemColunasEnum() {
        Integer enums = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND DATA_TYPE = 'ENUM'", Integer.class);

        assertThat(enums).isZero();
    }
}
