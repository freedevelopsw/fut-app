package pt.futapp;

import java.time.Clock;
import java.time.LocalDateTime;

import pt.futapp.config.AppConfig;

import pt.futapp.jogo.JogoService;
import pt.futapp.jogo.NovoJogoForm;
import pt.futapp.utilizador.ConviteService;
import pt.futapp.utilizador.RegistoForm;
import pt.futapp.utilizador.UtilizadorService;

/** Ajudas comuns aos testes. */
public final class TesteApoio {

    /** Admin criado no arranque (application-test.properties). */
    public static final String ADMIN = "admin";
    public static final String NOME_ADMIN = "Administrador";

    /** A mesma chave de application-test.properties. */
    public static final String CHAVE_CONVITE = "chave-de-teste";

    /** Gera convites como se fosse num momento à escolha (para testar expirações). */
    public static ConviteService convites(Clock clock) {
        return new ConviteService(CHAVE_CONVITE, 7, clock);
    }

    private TesteApoio() {
    }

    public static RegistoForm form(String username) {
        RegistoForm form = new RegistoForm();
        form.setUsername(username);
        form.setNome("Nome " + username);
        form.setPassword("segredo123");
        return form;
    }

    public static String conviteValido() {
        return convites(Clock.system(AppConfig.ZONA)).gerar(1, "teste").token();
    }

    /**
     * Regista um jogador com o nome "Nome {username}", através de um link de convite válido, e devolve o username.
     */
    public static String registar(UtilizadorService utilizadores, String username) {
        return utilizadores.registar(form(username), conviteValido()).getUsername();
    }

    public static Long criarJogo(JogoService jogos, String organizador) {
        NovoJogoForm form = new NovoJogoForm();
        form.setDataHora(LocalDateTime.now().plusDays(2).withHour(21).withMinute(0).withSecond(0).withNano(0));
        form.setLocal("Pavilhão");
        form.setVagas(12);
        return jogos.criar(form, organizador);
    }
}
