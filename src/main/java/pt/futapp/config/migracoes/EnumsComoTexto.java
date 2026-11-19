package pt.futapp.config.migracoes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Changeset 002 do Liquibase: os enums ficam como texto livre.
 * <ul>
 *   <li>Apaga as restrições CHECK das tabelas da app (o Hibernate criava-as com os valores de cada enum nesse dia).</li>
 *   <li>Em H2, converte para VARCHAR as colunas do tipo ENUM (criadas por versões antigas da app).</li>
 * </ul>
 * Funciona em H2 e em PostgreSQL. As restrições NOT NULL, chaves e índices não são tocados.
 */
public class EnumsComoTexto implements CustomTaskChange {

    private int alteracoes;

    @Override
    public void execute(Database database) throws CustomChangeException {
        try {
            alteracoes = aplicar(((JdbcConnection) database.getConnection()).getUnderlyingConnection());
        } catch (SQLException e) {
            throw new CustomChangeException("Não foi possível converter os enums para texto", e);
        }
    }

    /** @return número de restrições apagadas e colunas convertidas */
    public static int aplicar(Connection c) throws SQLException {
        String produto = c.getMetaData().getDatabaseProductName();
        List<String> comandos = new ArrayList<>();
        if ("H2".equalsIgnoreCase(produto)) {
            consultar(c, "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = SCHEMA() AND DATA_TYPE = 'ENUM'", comandos,
                "ALTER TABLE \"%s\" ALTER COLUMN \"%s\" SET DATA TYPE VARCHAR(40)");
            consultar(c, "SELECT TABLE_NAME, CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = SCHEMA() AND CONSTRAINT_TYPE = 'CHECK' "
                + "AND TABLE_NAME NOT LIKE 'DATABASECHANGELOG%'", comandos,
                "ALTER TABLE \"%s\" DROP CONSTRAINT \"%s\"");
        } else if ("PostgreSQL".equalsIgnoreCase(produto)) {
            consultar(c, "SELECT t.relname, r.conname FROM pg_constraint r "
                + "JOIN pg_class t ON t.oid = r.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE r.contype = 'c' AND n.nspname = current_schema() "
                + "AND t.relname NOT LIKE 'databasechangelog%'", comandos,
                "ALTER TABLE \"%s\" DROP CONSTRAINT \"%s\"");
        }
        try (Statement st = c.createStatement()) {
            for (String sql : comandos) {
                st.execute(sql);
            }
        }
        return comandos.size();
    }

    private static void consultar(Connection c, String sql, List<String> comandos, String modelo) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                comandos.add(String.format(modelo, rs.getString(1), rs.getString(2)));
            }
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Enums como texto: " + alteracoes + " restrições/colunas alteradas";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
