package pt.futapp.config;

import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No arranque em produção, escreve nos registos onde estão os dados, e avisa quando a base de dados H2 está numa
 * pasta temporária (no Cloud Run, /tmp apaga-se quando a instância se desliga).
 */
@Component
@Profile("prod")
public class AvisoBaseDados implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AvisoBaseDados.class);

    private final DataSource dataSource;

    public AvisoBaseDados(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String url;
        try (Connection c = dataSource.getConnection()) {
            url = c.getMetaData().getURL();
        }
        log.info("Base de dados: {}", descrever(url));
        String aviso = aviso(url);
        if (aviso != null) {
            log.warn(aviso);
        }
    }

    /** Descrição sem credenciais: o URL JDBC até aos parâmetros. */
    static String descrever(String url) {
        if (url == null) {
            return "desconhecida";
        }
        int fim = url.indexOf(url.startsWith("jdbc:h2:") ? ';' : '?');
        return fim > 0 ? url.substring(0, fim) : url;
    }

    /** Aviso quando os dados estão numa pasta que não sobrevive ao contentor; null se estiver tudo bem. */
    static String aviso(String url) {
        if (url != null && url.startsWith("jdbc:h2:") && (url.contains(":/tmp/") || url.startsWith("jdbc:h2:mem:"))) {
            return "Os dados estão numa pasta temporária e perdem-se quando a app se desliga. "
                + "Define FUTAPP_DADOS com a pasta de um volume permanente (por exemplo /dados).";
        }
        return null;
    }
}
