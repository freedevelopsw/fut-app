package pt.futapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

import liquibase.integration.spring.SpringLiquibase;
import pt.futapp.config.migracoes.EnumsComoTexto;

/** Versões da base de dados (Liquibase): base nova, base antiga criada pelo Hibernate e enums como texto. */
@SpringBootTest
@ActiveProfiles("test")
class MigracoesTest {

    private static final String CHANGELOG = "classpath:db/changelog/db.changelog-master.yaml";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @AfterEach
    void limpar() {
        jdbc.execute("DROP TABLE IF EXISTS teste_antigo");
    }

    @Test
    void numaBaseNovaOsChangeSetsCorremTodos() {
        List<String> ids = jdbc.queryForList("SELECT ID FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED", String.class);

        assertThat(ids).containsExactly("001-esquema-inicial", "002-enums-como-texto", "003-utilizador-resistencia",
            "003-utilizador-removido", "003-utilizador-removido-em", "003-jogo-local-opcional");
        assertThat(jdbc.queryForList("SELECT EXECTYPE FROM DATABASECHANGELOG", String.class)).containsOnly("EXECUTED");
        // o Hibernate valida as entidades contra estas tabelas (ddl-auto=validate): se a app arrancou, batem certo
        Integer checks = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE TABLE_SCHEMA = 'PUBLIC' AND CONSTRAINT_TYPE = 'CHECK'", Integer.class);
        assertThat(checks).isZero();
    }

    @Test
    void umaBaseAntigaCriadaPeloHibernateEMigradaSemPerderDados() throws Exception {
        // uma só ligação (no H2, uma restrição CHECK criada noutra sessão já fechada dá erro ao ser avaliada)
        SingleConnectionDataSource antiga = new SingleConnectionDataSource(
            "jdbc:h2:mem:antiga-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "", true);
        JdbcTemplate db = new JdbcTemplate(antiga);
        String esquema = new ClassPathResource("db/esquema-hibernate-2026-11-17-h2.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String sql : esquema.split(";")) {
            if (!sql.replaceAll("(?m)^--.*$", "").isBlank()) {
                db.execute(sql);
            }
        }
        db.update("INSERT INTO utilizador (username, nome, password_hash, criado_em) VALUES ('rui', 'Rui', 'x', NOW())");
        db.update("INSERT INTO jogo (vagas, criado_em, criado_por_id, data_hora, estado, local_jogo) "
            + "VALUES (12, NOW(), 1, NOW(), 'FECHADO', 'Campo 1')");

        migrar(antiga);

        assertThat(db.queryForMap("SELECT ID, EXECTYPE FROM DATABASECHANGELOG WHERE ID = '001-esquema-inicial'"))
            .containsEntry("EXECTYPE", "MARK_RAN");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class)).isEqualTo(6);
        // dados mantidos e colunas novas com os valores por omissão
        assertThat(db.queryForMap("SELECT nome, removido, resistencia FROM utilizador"))
            .containsEntry("NOME", "Rui").containsEntry("REMOVIDO", false).containsEntry("RESISTENCIA", null);
        assertThat(db.queryForObject("SELECT local_jogo FROM jogo", String.class)).isEqualTo("Campo 1");
        // o local passou a opcional e um valor novo de enum já é aceite (a restrição CHECK foi apagada)
        db.update("INSERT INTO jogo (vagas, criado_em, criado_por_id, data_hora, estado) VALUES (12, NOW(), 1, NOW(), 'X_NOVO')");
        db.update("INSERT INTO audit_evento (data_hora, acao) VALUES (NOW(), 'REMOCAO_UTILIZADOR')");

        migrar(antiga); // correr outra vez não faz nada
        assertThat(db.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class)).isEqualTo(6);
        antiga.destroy();
    }

    @Test
    void enumsComoTextoConverteColunasEnumEApagaRestricoesCheck() throws Exception {
        jdbc.execute("CREATE TABLE teste_antigo (id INT PRIMARY KEY, estado ENUM('ABERTO','FECHADO'), "
            + "fase VARCHAR(20) CHECK (fase IN ('A','B')))");

        try (Connection c = dataSource.getConnection()) {
            assertThat(EnumsComoTexto.aplicar(c)).isEqualTo(2);
            assertThat(EnumsComoTexto.aplicar(c)).isZero();
        }

        jdbc.update("INSERT INTO teste_antigo VALUES (1, 'CANCELADO', 'Z')");
    }

    private static void migrar(DataSource ds) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(ds);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setResourceLoader(new org.springframework.core.io.DefaultResourceLoader());
        liquibase.afterPropertiesSet();
    }
}
