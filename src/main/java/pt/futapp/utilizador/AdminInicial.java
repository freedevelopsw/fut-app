package pt.futapp.utilizador;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria a conta de administrador no arranque, se ainda não existir nenhum Admin.
 * Configuração: futapp.admin.username, futapp.admin.nome e futapp.admin.password
 * (variáveis FUTAPP_ADMIN_USERNAME, FUTAPP_ADMIN_NOME, FUTAPP_ADMIN_PASSWORD).
 * Sem password configurada, é gerada uma aleatória e escrita no log, uma única vez.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);
    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UtilizadorService utilizadores;
    private final String username;
    private final String nome;
    private final String password;

    public AdminInicial(UtilizadorService utilizadores,
                        @Value("${futapp.admin.username:admin}") String username,
                        @Value("${futapp.admin.nome:Administrador}") String nome,
                        @Value("${futapp.admin.password:}") String password) {
        this.utilizadores = utilizadores;
        this.username = username;
        this.nome = nome;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean gerada = password == null || password.isBlank();
        String usada = gerada ? gerarPassword() : password;
        if (utilizadores.garantirAdmin(username, nome, usada)) {
            if (gerada) {
                log.warn("Conta de administrador '{}' criada com a password gerada: {}  "
                    + "(define FUTAPP_ADMIN_PASSWORD para escolheres a password)", username, usada);
            } else {
                log.info("Conta de administrador '{}' criada com a password configurada.", username);
            }
        }
    }

    private static String gerarPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }
        return sb.toString();
    }
}
