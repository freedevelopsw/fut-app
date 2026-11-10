package pt.futapp.votacao;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** No primeiro arranque, cria os prémios brincadeira de origem. */
@Component
public class PremiosIniciais implements ApplicationRunner {

    private final PremiosService premios;

    public PremiosIniciais(PremiosService premios) {
        this.premios = premios;
    }

    @Override
    public void run(ApplicationArguments args) {
        premios.criarIniciaisSeVazio();
    }
}
