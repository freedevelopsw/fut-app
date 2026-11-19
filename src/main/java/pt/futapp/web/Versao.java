package pt.futapp.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/**
 * Versão da app, lida do {@code META-INF/build-info.properties} gerado pelo Maven (a versão do pom.xml).
 * Escrita no registo do arranque (para confirmar no Cloud Run que versão está publicada) e mostrada no Perfil.
 */
@Component
public class Versao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Versao.class);

    /** Sem build-info (por exemplo, a correr a partir do IDE sem passar pelo Maven). */
    static final String DESCONHECIDA = "dev";

    private final BuildProperties build;

    public Versao(ObjectProvider<BuildProperties> build) {
        this.build = build.getIfAvailable();
    }

    public String numero() {
        return build != null && build.getVersion() != null ? build.getVersion() : DESCONHECIDA;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("FutApp versão {}", numero());
    }
}
