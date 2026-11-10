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
    @Autowired private pt.futapp.jogo.EquipasService equipas;
    @Autowired private pt.futapp.jogo.RegistoService registoService;

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

        Long adminId = utilizadores.vista(ADMIN).orElseThrow().id();
        for (String url : List.of("/", "/jogos/" + jogo, "/jogos/novo", "/perfil", "/estatisticas",
                                  "/jogadores/" + adminId, "/jogadores/" + adminId + "/editar", "/jogos/" + jogo + "/equipas", "/jogos/" + jogo + "/registo",
                                  "/jogos/" + jogo + "/votar", "/jogos/" + jogo + "/votos", "/gestao/premios",
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
    void umJogadorVeACartaDosOutrosMasNaoAEdita() throws Exception {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");
        Long idRui = utilizadores.vista(rui).orElseThrow().id();
        var comoNuno = user(nuno).roles("JOGADOR");

        mvc.perform(get("/jogadores/" + idRui).with(comoNuno))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nome rui")))
            .andExpect(content().string(not(containsString("/editar"))));
        mvc.perform(get("/jogadores/" + idRui + "/editar").with(comoNuno)).andExpect(status().isBadRequest());
        mvc.perform(post("/jogadores/" + idRui + "/editar").with(comoNuno).with(csrf()).param("nome", "Hackeado"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Só podes alterar os teus próprios dados")));
        mvc.perform(get("/estatisticas").with(comoNuno))
            .andExpect(content().string(containsString("/jogadores/" + idRui)));
    }

    @Test
    void oJogadorPreencheACartaPeloEcra() throws Exception {
        String rui = registar(utilizadores, "rui");
        Long idRui = utilizadores.vista(rui).orElseThrow().id();
        var comoRui = user(rui).roles("JOGADOR");

        mvc.perform(get("/perfil/dados").with(comoRui)).andExpect(redirectedUrl("/jogadores/" + idRui + "/editar"));
        mvc.perform(get("/jogadores/" + idRui + "/editar").with(comoRui)).andExpect(status().isOk());
        mvc.perform(post("/jogadores/" + idRui + "/editar").with(comoRui).with(csrf())
                .param("nome", "Rui Costa")
                .param("alcunha", "Maestro")
                .param("posicao", "MEDIO")
                .param("posicaoSecundaria", "")
                .param("pe", "DIREITO")
                .param("velocidade", "7")
                .param("tecnica", "9")
                .param("remate", "")
                .param("passe", "8")
                .param("defesa", ""))
            .andExpect(redirectedUrl("/jogadores/" + idRui));

        mvc.perform(get("/jogadores/" + idRui).with(comoRui))
            .andExpect(content().string(containsString("Maestro")))
            .andExpect(content().string(containsString("MED")));
        mvc.perform(get("/perfil").with(comoRui)).andExpect(content().string(containsString("Maestro")));
    }

    @Test
    void semNomeOFormularioDeDadosVoltaComErro() throws Exception {
        String rui = registar(utilizadores, "rui");
        Long idRui = utilizadores.vista(rui).orElseThrow().id();

        mvc.perform(post("/jogadores/" + idRui + "/editar").with(user(rui).roles("JOGADOR")).with(csrf()).param("nome", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("O nome é obrigatório")));
    }

    @Test
    void osMembrosJaNaoTemBotoesDeRoles() throws Exception {
        registar(utilizadores, "rui");

        mvc.perform(get("/admin/membros").with(comoOrganizador()))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"nivel\""))));
    }

    @Test
    void oNivelMudaNumDropdownNaCartaConformeQuemEsta() throws Exception {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");
        Long idRui = utilizadores.vista(rui).orElseThrow().id();
        Long idNuno = utilizadores.vista(nuno).orElseThrow().id();
        Long idAdmin = utilizadores.vista(ADMIN).orElseThrow().id();

        // Jogador: não vê o nível de acesso
        mvc.perform(get("/jogadores/" + idNuno).with(user(rui).roles("JOGADOR")))
            .andExpect(content().string(not(containsString("name=\"nivel\""))));

        // Admin: dropdown com os três níveis; torna o Rui organizador
        mvc.perform(get("/jogadores/" + idRui).with(comoOrganizador()))
            .andExpect(content().string(containsString("name=\"nivel\"")))
            .andExpect(content().string(containsString("value=\"ADMIN\"")));
        mvc.perform(post("/jogadores/" + idRui + "/nivel").with(comoOrganizador()).with(csrf()).param("nivel", "ORGANIZADOR"))
            .andExpect(redirectedUrl("/jogadores/" + idRui));
        assertThat(utilizadores.vista(rui).orElseThrow().gestor()).isTrue();

        // Admin na própria carta: sem dropdown
        mvc.perform(get("/jogadores/" + idAdmin).with(comoOrganizador()))
            .andExpect(content().string(not(containsString("name=\"nivel\""))))
            .andExpect(content().string(containsString("Não podes mudar o teu próprio nível de Admin")));

        // Organizador: dropdown só com Jogador e Organizador; não consegue dar Admin
        var comoRui = user(rui).roles("JOGADOR", "ORGANIZADOR");
        mvc.perform(get("/jogadores/" + idNuno).with(comoRui))
            .andExpect(content().string(containsString("name=\"nivel\"")))
            .andExpect(content().string(not(containsString("value=\"ADMIN\""))));
        mvc.perform(post("/jogadores/" + idNuno + "/nivel").with(comoRui).with(csrf()).param("nivel", "ADMIN"))
            .andExpect(redirectedUrl("/jogadores/" + idNuno));
        assertThat(utilizadores.vista(nuno).orElseThrow().admin()).isFalse();
        mvc.perform(post("/jogadores/" + idNuno + "/nivel").with(comoRui).with(csrf()).param("nivel", "ORGANIZADOR"))
            .andExpect(redirectedUrl("/jogadores/" + idNuno));
        assertThat(utilizadores.vista(nuno).orElseThrow().gestor()).isTrue();

        // Organizador na carta de um Admin: vê o nível mas não o muda
        mvc.perform(get("/jogadores/" + idAdmin).with(comoRui))
            .andExpect(content().string(not(containsString("name=\"nivel\""))))
            .andExpect(content().string(containsString("Só o Admin pode dar ou retirar o nível de Admin")));
    }

    @Test
    void asEquipasFazemSeEPublicamSePeloEcra() throws Exception {
        for (String u : List.of("e1", "e2", "e3", "e4")) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        var jogador = user("e1").roles("JOGADOR");

        mvc.perform(get("/jogos/" + jogo + "/equipas").with(jogador)).andExpect(status().isForbidden());
        mvc.perform(post("/jogos/" + jogo + "/gestao/equipas/gerar").with(comoOrganizador()).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo + "/equipas"));
        mvc.perform(get("/jogos/" + jogo + "/equipas").with(comoOrganizador()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sem coletes")))
            .andExpect(content().string(containsString("Nome e1")));

        // Antes de publicar, o jogador não vê as equipas na convocatória
        mvc.perform(get("/jogos/" + jogo).with(jogador))
            .andExpect(content().string(not(containsString("equipa-cartao"))));

        jogos.fecharConvocatoria(jogo, organizador);
        mvc.perform(post("/jogos/" + jogo + "/gestao/equipas/publicar").with(comoOrganizador()).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo + "/equipas"));

        mvc.perform(get("/jogos/" + jogo).with(jogador))
            .andExpect(content().string(containsString("equipa-cartao")))
            .andExpect(content().string(containsString("Equipas publicadas")));
        mvc.perform(post("/jogos/" + jogo + "/gestao/equipas/gerar").with(jogador).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void oJogoRegistaSePeloEcraEOResultadoApareceATodos() throws Exception {
        for (String u : List.of("r1", "r2", "r3", "r4")) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        equipas.gerar(jogo, organizador);
        jogos.fecharConvocatoria(jogo, organizador);
        equipas.publicar(jogo, organizador, "http://localhost");
        var jogador = user("r1").roles("JOGADOR");
        var v = registoService.vista(jogo, organizador);
        Long marcador = v.coletes().get(0).inscricaoId();

        mvc.perform(get("/jogos/" + jogo + "/registo").with(jogador)).andExpect(status().isForbidden());
        mvc.perform(get("/jogos/" + jogo + "/registo").with(comoOrganizador()).param("golo", "COLETES"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Quem marcou?")));
        mvc.perform(post("/jogos/" + jogo + "/gestao/registo/golos").with(comoOrganizador()).with(csrf())
                .param("equipa", "COLETES").param("marcador", String.valueOf(marcador)).param("assistencia", ""))
            .andExpect(redirectedUrl("/jogos/" + jogo + "/registo"));
        mvc.perform(post("/jogos/" + jogo + "/gestao/registo/golos").with(comoOrganizador()).with(csrf())
                .param("equipa", "SEM_COLETES").param("marcador", ""))
            .andExpect(redirectedUrl("/jogos/" + jogo + "/registo"));
        mvc.perform(post("/jogos/" + jogo + "/gestao/registo/faltas").with(comoOrganizador()).with(csrf())
                .param("inscricaoId", String.valueOf(v.semColetes().get(0).inscricaoId())).param("faltou", "true"))
            .andExpect(redirectedUrl("/jogos/" + jogo + "/registo"));
        mvc.perform(get("/jogos/" + jogo + "/registo").with(comoOrganizador()))
            .andExpect(content().string(containsString("Golos (2)")))
            .andExpect(content().string(containsString("1 falta")));

        mvc.perform(get("/jogos/" + jogo).with(jogador))
            .andExpect(content().string(containsString("Resultado")))
            .andExpect(content().string(containsString(v.coletes().get(0).nome())));

        mvc.perform(post("/jogos/" + jogo + "/gestao/registo/fechar").with(comoOrganizador()).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo));
        mvc.perform(get("/jogos/" + jogo).with(jogador))
            .andExpect(content().string(containsString("Votação aberta")));
        mvc.perform(post("/jogos/" + jogo + "/gestao/registo/reabrir").with(jogador).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void votaSePeloEcraEOsResultadosAparecemNoFim() throws Exception {
        for (String u : List.of("v1", "v2", "v3", "v4")) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        equipas.gerar(jogo, organizador);
        jogos.fecharConvocatoria(jogo, organizador);
        equipas.publicar(jogo, organizador, "http://localhost");
        registoService.fechar(jogo, organizador);
        var v1 = user("v1").roles("JOGADOR");
        Long votadoV2 = registoService.vista(jogo, organizador).coletes().stream()
            .filter(p -> p.nome().equals("Nome v2")).findFirst()
            .or(() -> registoService.vista(jogo, organizador).semColetes().stream().filter(p -> p.nome().equals("Nome v2")).findFirst())
            .orElseThrow().inscricaoId();

        mvc.perform(get("/jogos/" + jogo).with(v1))
            .andExpect(content().string(containsString("Votar no MVP e nos prémios")));
        mvc.perform(get("/jogos/" + jogo + "/votar").with(v1))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"voto_MVP\"")))
            .andExpect(content().string(containsString("Frango da noite")));
        mvc.perform(post("/jogos/" + jogo + "/votar").with(v1).with(csrf()).param("voto_MVP", String.valueOf(votadoV2)))
            .andExpect(redirectedUrl("/jogos/" + jogo));
        mvc.perform(get("/jogos/" + jogo).with(v1))
            .andExpect(content().string(containsString("1 de 4")))
            .andExpect(content().string(containsString("Alterar o meu voto")));

        mvc.perform(get("/jogos/" + jogo + "/votos").with(v1)).andExpect(status().isForbidden());
        mvc.perform(get("/jogos/" + jogo + "/votos").with(comoOrganizador()))
            .andExpect(content().string(containsString("Nome v1 → Nome v2")));

        mvc.perform(post("/jogos/" + jogo + "/gestao/votacao/fechar").with(comoOrganizador()).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo));
        mvc.perform(get("/jogos/" + jogo).with(v1))
            .andExpect(content().string(containsString("MVP do jogo")))
            .andExpect(content().string(containsString("Nome v2")));
    }

    @Test
    void osPremiosGeremSeNoEcraDoOrganizador() throws Exception {
        mvc.perform(get("/gestao/premios").with(user("x").roles("JOGADOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/gestao/premios").with(comoOrganizador()).with(csrf()).param("nome", "Bota de ouro"))
            .andExpect(redirectedUrl("/gestao/premios"));
        mvc.perform(get("/gestao/premios").with(comoOrganizador()))
            .andExpect(content().string(containsString("Bota de ouro")))
            .andExpect(content().string(containsString("Ator de Hollywood")));
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
