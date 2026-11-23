package pt.futapp.avaliacao;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

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
import pt.futapp.utilizador.JogadorService;
import pt.futapp.utilizador.UtilizadorService;

/** Abre os ecrãs da avaliação das cartas e do "Avisar no grupo" com os templates a sério. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvaliacaoPaginasTest {

    @Autowired private WebApplicationContext contexto;
    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogadorService jogadores;
    @Autowired private AvaliacaoService avaliacoes;
    @Autowired private JogoService jogos;

    private MockMvc mvc;
    private String rui;
    private String ana;
    private Long idAna;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        rui = registar(utilizadores, "rui");
        ana = registar(utilizadores, "ana");
        idAna = jogadores.idDe(ana);
    }

    private static UserRequestPostProcessor jogador(String username) {
        return user(username).roles("JOGADOR");
    }

    private static UserRequestPostProcessor admin() {
        return user(ADMIN).roles("JOGADOR", "ORGANIZADOR", "ADMIN");
    }

    @Test
    void aCartaTemOsSeparadoresEuEColegas() throws Exception {
        mvc.perform(get("/jogadores/" + idAna).with(jogador(rui)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"segmentos\"")))
            .andExpect(content().string(containsString("Os atributos são uma auto-avaliação")))
            .andExpect(content().string(containsString("Avaliar Nome ana")))
            .andExpect(content().string(not(containsString("vista=jogos"))));

        mvc.perform(get("/jogadores/" + idAna).param("vista", "colegas").with(jogador(rui)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Ainda nenhum colega avaliou esta carta.")))
            .andExpect(content().string(containsString("Quem avaliou e como")))
            .andExpect(content().string(containsString("Fica visível quando o Admin der permissão.")));

        // um separador desconhecido mostra a autoavaliação
        mvc.perform(get("/jogadores/" + idAna).param("vista", "jogos").with(jogador(rui)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Os atributos são uma auto-avaliação")));

        // na própria carta não há botão para se avaliar
        mvc.perform(get("/jogadores/" + idAna).with(jogador(ana)))
            .andExpect(content().string(not(containsString("Avaliar Nome ana"))));
    }

    @Test
    void avaliarUmColegaEVerAMediaNaCarta() throws Exception {
        mvc.perform(get("/jogadores/" + idAna + "/avaliar").with(jogador(rui)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Guardar avaliação")));

        mvc.perform(post("/jogadores/" + idAna + "/avaliar").with(jogador(rui)).with(csrf())
                .param("velocidade", "8").param("remate", "6"))
            .andExpect(redirectedUrl("/jogadores/" + idAna + "?vista=colegas"))
            .andExpect(flash().attribute("sucesso", "Avaliação guardada."));

        mvc.perform(get("/jogadores/" + idAna).param("vista", "colegas").with(jogador(rui)))
            .andExpect(content().string(containsString("7,0")))
            .andExpect(content().string(containsString("1 voto")))
            .andExpect(content().string(containsString("Já votou 1 colega")))
            .andExpect(content().string(containsString("Alterar a minha avaliação")));

        // uma nota fora de 1 a 10 volta ao formulário com o erro
        mvc.perform(post("/jogadores/" + idAna + "/avaliar").with(jogador(rui)).with(csrf()).param("velocidade", "11"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Entre 1 e 10.")));
    }

    @Test
    void quemAvaliouEComoSoParaOAdminEnquantoOInterruptorEstaDesligado() throws Exception {
        AvaliacaoForm f = new AvaliacaoForm();
        f.setVelocidade(9);
        avaliacoes.guardar(idAna, f, rui);

        mvc.perform(get("/jogadores/" + idAna + "/avaliacoes").with(jogador(ana)))
            .andExpect(redirectedUrl("/jogadores/" + idAna + "?vista=colegas"));
        mvc.perform(get("/jogadores/" + idAna + "/avaliacoes").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nome rui")))
            .andExpect(content().string(containsString("Média")));
        mvc.perform(get("/jogadores/" + idAna).param("vista", "colegas").with(admin()))
            .andExpect(content().string(containsString("Ver detalhes")));

        // o interruptor só aparece ao Admin, nas Cartas das Estatísticas
        mvc.perform(get("/estatisticas").with(jogador(rui)))
            .andExpect(content().string(not(containsString("Mostrar quem avaliou e como"))))
            .andExpect(content().string(containsString("hx-get=\"/estatisticas?cartas=colegas\"")));
        mvc.perform(get("/estatisticas").with(admin()))
            .andExpect(content().string(containsString("Mostrar quem avaliou e como")))
            .andExpect(content().string(containsString("Desligado: em todas as cartas")));

        mvc.perform(post("/admin/avaliacoes/visibilidade").param("ligado", "true").with(jogador(rui)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/avaliacoes/visibilidade").param("ligado", "true").param("cartas", "colegas")
                .with(admin()).with(csrf()))
            .andExpect(redirectedUrl("/estatisticas?cartas=colegas"));

        mvc.perform(get("/jogadores/" + idAna + "/avaliacoes").with(jogador(ana)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Nome rui")));
        mvc.perform(get("/estatisticas").param("cartas", "colegas").with(admin()))
            .andExpect(content().string(containsString("<details id=\"cartas\" class=\"colapsavel\" open=\"open\">")))
            .andExpect(content().string(containsString("title=\"Média dos colegas\" class=\"ordenavel ordenada\" aria-sort=\"descending\"")))
            .andExpect(content().string(containsString("Ligado: em todas as cartas")));
    }

    @Test
    void depoisDeInscreverOuCancelarApareceOAvisarNoGrupo() throws Exception {
        Long jogo = criarJogo(jogos, ADMIN);

        mvc.perform(post("/jogos/" + jogo + "/inscricao").with(jogador(rui)).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo))
            .andExpect(flash().attribute("listaMudou", true));
        mvc.perform(get("/jogos/" + jogo).flashAttr("listaMudou", true).with(jogador(rui)))
            .andExpect(content().string(containsString("Avisar no grupo")))
            .andExpect(content().string(containsString("https://wa.me/?text=")));

        mvc.perform(post("/jogos/" + jogo + "/inscricao/cancelar").with(jogador(rui)).with(csrf()))
            .andExpect(redirectedUrl("/jogos/" + jogo))
            .andExpect(flash().attribute("listaMudou", true));
    }
}
