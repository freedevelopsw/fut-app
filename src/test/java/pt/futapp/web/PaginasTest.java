package pt.futapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.conviteValido;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import pt.futapp.jogo.JogoService;
import pt.futapp.utilizador.UtilizadorService;

/** Abre cada ecrã de verdade (com os templates Thymeleaf) para apanhar erros nos templates e nas permissões. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaginasTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;

    private MockMvc mvc;
    private String organizador;
    private Long jogo;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        organizador = ADMIN;
        jogo = criarJogo(jogos, organizador);
    }

    private UserRequestPostProcessor comoOrganizador() {
        return user(organizador).roles("JOGADOR", "ORGANIZADOR", "ADMIN");
    }

    @Test
    void asPaginasPublicasAbremSemLogin() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/registo")).andExpect(status().isOk());
    }

    @Test
    void semLoginOsJogosRedirecionamParaOLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/jogos/" + jogo)).andExpect(status().is3xxRedirection());
    }

    @Test
    void todasAsPaginasAbremParaOAdmin() throws Exception {
        jogos.inscrever(jogo, organizador);
        jogos.adicionarConvidado(jogo, "Zé", organizador);

        for (String url : List.of("/", "/jogos/" + jogo, "/jogos/novo", "/perfil", "/estatisticas",
                                  "/admin/membros", "/admin/convites", "/admin/auditoria", "/perfil/password")) {
            mvc.perform(get(url).with(comoOrganizador())).andExpect(status().isOk());
        }
    }

    @Test
    void umJogadorVeAConvocatoriaMasNaoAsPaginasDeGestao() throws Exception {
        String j1 = registar(utilizadores, "j1");
        var jogador = user(j1).roles("JOGADOR");

        mvc.perform(get("/jogos/" + jogo).with(jogador)).andExpect(status().isOk());
        mvc.perform(get("/jogos/novo").with(jogador)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/membros").with(jogador)).andExpect(status().isForbidden());
        mvc.perform(post("/jogos/" + jogo + "/gestao/fechar").with(jogador).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void inscreverPeloEcraVoltaParaAConvocatoria() throws Exception {
        String j1 = registar(utilizadores, "j1");

        mvc.perform(post("/jogos/" + jogo + "/inscricao").with(user(j1).roles("JOGADOR")).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/jogos/" + jogo));

        assertThat(jogos.convocatoria(jogo, j1, "http://localhost").inscrito()).isTrue();
    }

    @Test
    void semConviteORegistoNaoMostraOFormulario() throws Exception {
        mvc.perform(get("/registo"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("precisas de um link de convite")))
            .andExpect(content().string(not(containsString("name=\"password\""))));
        mvc.perform(get("/registo").param("convite", "inventado"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("não é válido")));
    }

    @Test
    void comConviteValidoRegistaPeloEcra() throws Exception {
        String convite = conviteValido();

        mvc.perform(get("/registo").param("convite", convite))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"password\"")));
        mvc.perform(post("/registo").with(csrf())
                .param("convite", convite)
                .param("nome", "Rui")
                .param("username", "rui")
                .param("password", "segredo123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registado"));

        assertThat(utilizadores.vista("rui")).isPresent();
    }

    @Test
    void registarSemConvitePeloEcraNaoCriaConta() throws Exception {
        mvc.perform(post("/registo").with(csrf())
                .param("nome", "Intruso")
                .param("username", "intruso")
                .param("password", "segredo123"))
            .andExpect(status().isOk());

        assertThat(utilizadores.vista("intruso")).isEmpty();
    }

    @Test
    void oAdminGeraUmLinkDeConviteQueFunciona() throws Exception {
        String pagina = mvc.perform(post("/admin/convites").with(comoOrganizador()).with(csrf()).param("dias", "7"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/registo?convite=")))
            .andReturn().getResponse().getContentAsString();

        String token = pagina.replaceAll("(?s).*/registo\\?convite=([A-Za-z0-9_-]+).*", "$1");
        mvc.perform(get("/registo").param("convite", token))
            .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void oLoginNaoTemLinkParaRegisto() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/registo"))));
    }

    @Test
    void oAdminEntraComAPasswordConfigurada() throws Exception {
        mvc.perform(formLogin("/login").user(ADMIN).password("segredo-admin"))
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void umMembroBloqueadoNaoConsegueEntrarESeEstiverDentroESaido() throws Exception {
        String rui = registar(utilizadores, "rui");
        mvc.perform(formLogin("/login").user(rui).password("segredo123")).andExpect(redirectedUrl("/"));

        mvc.perform(post("/admin/membros/" + utilizadores.vista(rui).orElseThrow().id() + "/bloqueio")
                .with(comoOrganizador()).with(csrf()).param("bloquear", "true"))
            .andExpect(redirectedUrl("/admin/membros"));

        mvc.perform(formLogin("/login").user(rui).password("segredo123")).andExpect(redirectedUrl("/login?bloqueado"));
        mvc.perform(get("/").with(user(rui).roles("JOGADOR"))).andExpect(redirectedUrl("/login?bloqueado"));
        mvc.perform(get("/admin/membros").with(comoOrganizador()))
            .andExpect(content().string(containsString("Bloqueados (1)")));
    }

    @Test
    void mudarAPasswordPeloEcraObrigaAEntrarComANova() throws Exception {
        String rui = registar(utilizadores, "rui");

        mvc.perform(get("/perfil/password").with(user(rui).roles("JOGADOR"))).andExpect(status().isOk());
        mvc.perform(post("/perfil/password").with(user(rui).roles("JOGADOR")).with(csrf())
                .param("atual", "segredo123")
                .param("nova", "nova-password")
                .param("confirmacao", "nova-password"))
            .andExpect(redirectedUrl("/login?passwordAlterada"));

        mvc.perform(formLogin("/login").user(rui).password("segredo123")).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").user(rui).password("nova-password")).andExpect(redirectedUrl("/"));
    }

    @Test
    void mudarAPasswordComErrosMostraOFormularioENaoMuda() throws Exception {
        String rui = registar(utilizadores, "rui");

        mvc.perform(post("/perfil/password").with(user(rui).roles("JOGADOR")).with(csrf())
                .param("atual", "segredo123")
                .param("nova", "nova-password")
                .param("confirmacao", "outra-coisa"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("não são iguais")));
        mvc.perform(post("/perfil/password").with(user(rui).roles("JOGADOR")).with(csrf())
                .param("atual", "errada")
                .param("nova", "nova-password")
                .param("confirmacao", "nova-password"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("password atual não está certa")));

        mvc.perform(formLogin("/login").user(rui).password("segredo123")).andExpect(redirectedUrl("/"));
    }

    @Test
    void desbloquearDevolveOAcesso() throws Exception {
        String rui = registar(utilizadores, "rui");
        Long id = utilizadores.vista(rui).orElseThrow().id();
        utilizadores.bloquear(id, ADMIN);

        mvc.perform(post("/admin/membros/" + id + "/bloqueio").with(comoOrganizador()).with(csrf()).param("bloquear", "false"))
            .andExpect(redirectedUrl("/admin/membros"));

        mvc.perform(formLogin("/login").user(rui).password("segredo123")).andExpect(redirectedUrl("/"));
    }

    @Test
    void umJogadorNaoGeraConvites() throws Exception {
        String j1 = registar(utilizadores, "j1");

        mvc.perform(post("/admin/convites").with(user(j1).roles("JOGADOR")).with(csrf()).param("dias", "7"))
            .andExpect(status().isForbidden());
    }

    @Test
    void criarJogoComDadosInvalidosMostraOFormularioOutraVez() throws Exception {
        mvc.perform(post("/jogos/novo").with(comoOrganizador()).with(csrf())
                .param("local", "")
                .param("vagas", "12"))
            .andExpect(status().isOk());
    }
}
