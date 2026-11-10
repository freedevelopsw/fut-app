package pt.futapp.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Corre no arranque, antes de tudo o resto: nas bases de dados H2 criadas por versões anteriores da app,
 * as colunas de enums foram criadas com o tipo ENUM nativo, que recusa valores novos (por exemplo, um jogo "CANCELADO").
 * Converte essas colunas para VARCHAR. Hoje os enums já são guardados como texto ({@code EnumTextoConverter}),
 * por isso isto só faz alguma coisa uma vez em cada base de dados antiga.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AjusteEsquema implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AjusteEsquema.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public AjusteEsquema(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        ajustar();
    }

    /** @return número de colunas convertidas */
    public int ajustar() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            if (!"H2".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())) {
                return 0;
            }
        }
        List<Map<String, Object>> colunas = jdbc.queryForList(
            "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = 'PUBLIC' AND DATA_TYPE = 'ENUM'");
        for (Map<String, Object> col : colunas) {
            String tabela = (String) col.get("TABLE_NAME");
            String coluna = (String) col.get("COLUMN_NAME");
            jdbc.execute("ALTER TABLE \"" + tabela + "\" ALTER COLUMN \"" + coluna + "\" SET DATA TYPE VARCHAR(40)");
            log.info("Esquema: coluna {}.{} convertida de ENUM para VARCHAR", tabela, coluna);
        }
        return colunas.size();
    }
}
