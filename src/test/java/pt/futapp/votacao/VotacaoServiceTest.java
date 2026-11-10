package pt.futapp.votacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.jogo.EquipasService;
import pt.futapp.jogo.EstadoJogo;
import pt.futapp.jogo.JogoService;
import pt.futapp.jogo.RegistoService;
import pt.futapp.jogo.RegistoView;
import pt.futapp.utilizador.UtilizadorService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VotacaoServiceTest {

    private static final String URL = "http://localhost:8080";

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;
    @Autowired private EquipasService equipas;
    @Autowired private RegistoService registo;
    @Autowired private VotacaoService votacao;
    @Autowired private PremiosService premios;

    private Long jogo;
    /** username → inscrição */
    private final Map<String, RegistoView.Presenca> ins = new HashMap<>();

    /** Jogo com j1..j4, equipas publicadas e o jogo fechado (votação aberta). */
    @BeforeEach
    void preparar() {
        jogo = criarJogo(jogos, ADMIN);
        for (String u : new String[] {"j1", "j2", "j3", "j4"}) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        equipas.gerar(jogo, ADMIN);
        jogos.fecharConvocatoria(jogo, ADMIN);
        equipas.publicar(jogo, ADMIN, URL);
        RegistoView r = registo.vista(jogo, ADMIN);
        r.coletes().forEach(p -> ins.put(p.nome().replace("Nome ", ""), p));
        r.semColetes().forEach(p -> ins.put(p.nome().replace("Nome ", ""), p));
    }

    private void fecharJogo() {
        registo.fechar(jogo, ADMIN);
    }

    private static Map<Long, Long> mvp(Long inscricao) {
        Map<Long, Long> m = new HashMap<>();
        m.put(null, inscricao);
        return m;
    }

    private Long id(String u) {
        return ins.get(u).inscricaoId();
    }

    @Test
    void soAbreDepoisDeFecharOJogo() {
        assertThat(votacao.vista(jogo, "j1").podeVotar()).isFalse();
        assertThatThrownBy(() -> votacao.votar(jogo, mvp(id("j2")), "j1")).isInstanceOf(OperacaoInvalidaException.class);

        fecharJogo();
        assertThat(votacao.vista(jogo, "j1").podeVotar()).isTrue();
        assertThat(votacao.vista(jogo, "j1").eleitores()).isEqualTo(4);
    }

    @Test
    void soVotaQuemJogouEEstevePresente() {
        registo.marcarFalta(jogo, id("j4"), true, ADMIN);
        fecharJogo();

        assertThat(votacao.vista(jogo, ADMIN).podeVotar()).isFalse();
        assertThat(votacao.vista(jogo, "j4").podeVotar()).isFalse();
        assertThat(votacao.vista(jogo, "j1").eleitores()).isEqualTo(3);
        assertThat(votacao.vista(jogo, "j1").candidatos()).extracting(VotacaoView.Candidato::inscricaoId).doesNotContain(id("j4"));
    }

    @Test
    void ninguemVotaEmSiProprioEOMvpEObrigatorio() {
        fecharJogo();

        assertThat(votacao.vista(jogo, "j1").candidatos()).extracting(VotacaoView.Candidato::inscricaoId).doesNotContain(id("j1"));
        assertThatThrownBy(() -> votacao.votar(jogo, mvp(id("j1")), "j1"))
            .isInstanceOf(OperacaoInvalidaException.class).hasMessageContaining("ti próprio");
        assertThatThrownBy(() -> votacao.votar(jogo, new HashMap<>(), "j1"))
            .isInstanceOf(OperacaoInvalidaException.class).hasMessageContaining("MVP");
    }

    @Test
    void oVotoESecretoMasMostraQuemJaVotou() {
        fecharJogo();
        votacao.votar(jogo, mvp(id("j2")), "j1");

        VotacaoView vistaJ2 = votacao.vista(jogo, "j2");
        assertThat(vistaJ2.votaram()).extracting(VotacaoView.Eleitor::nome).containsExactly("Nome j1");
        assertThat(vistaJ2.resultados()).isEmpty();
        assertThat(votacao.vista(jogo, "j1").jaVotei()).isTrue();
        assertThatThrownBy(() -> votacao.votosDoJogo(jogo, "j1")).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void alterarOVotoSubstituiOAnterior() {
        fecharJogo();
        votacao.votar(jogo, mvp(id("j2")), "j1");
        votacao.votar(jogo, mvp(id("j3")), "j1");

        assertThat(votacao.votosDoJogo(jogo, ADMIN)).singleElement()
            .satisfies(v -> assertThat(v.votado()).isEqualTo("Nome j3"));
        assertThat(votacao.vista(jogo, "j1").categorias().get(0).meuVoto()).isEqualTo(id("j3"));
    }

    @Test
    void fechaSozinhaQuandoTodosVotamEMostraOsResultados() {
        fecharJogo();
        votacao.votar(jogo, mvp(id("j2")), "j1");
        votacao.votar(jogo, mvp(id("j1")), "j2");
        votacao.votar(jogo, mvp(id("j2")), "j3");
        boolean fechou = votacao.votar(jogo, mvp(id("j2")), "j4");

        assertThat(fechou).isTrue();
        VotacaoView v = votacao.vista(jogo, "j1");
        assertThat(v.estado()).isEqualTo(EstadoJogo.FECHADO);
        assertThat(v.mvpResultado().vencedores()).containsExactly("Nome j2");
        assertThat(v.mvpResultado().votos()).isEqualTo(3);
    }

    @Test
    void empateNoMvpDesempataPorGolosESeContinuarEPartilhado() {
        // j3 marca um golo
        registo.adicionarGolo(jogo, ins.get("j3").equipa(), id("j3"), null, ADMIN);
        fecharJogo();
        votacao.votar(jogo, mvp(id("j3")), "j1");
        votacao.votar(jogo, mvp(id("j4")), "j2");
        votacao.votar(jogo, mvp(id("j4")), "j3");
        votacao.votar(jogo, mvp(id("j3")), "j4");

        VotacaoView.Resultado r = votacao.vista(jogo, "j1").mvpResultado();
        assertThat(r.vencedores()).containsExactly("Nome j3");
        assertThat(r.desempatePorGolos()).isTrue();

        votacao.reabrir(jogo, ADMIN);
        registo.reabrir(jogo, ADMIN);
        registo.adicionarGolo(jogo, ins.get("j4").equipa(), id("j4"), null, ADMIN);
        registo.fechar(jogo, ADMIN);
        votacao.fechar(jogo, ADMIN);

        VotacaoView.Resultado partilhado = votacao.vista(jogo, "j1").mvpResultado();
        assertThat(partilhado.partilhado()).isTrue();
        assertThat(partilhado.vencedores()).containsExactlyInAnyOrder("Nome j3", "Nome j4");
    }

    @Test
    void osPremiosAtivosEntramNoBoletimESaoOpcionais() {
        fecharJogo();
        Premio frango = premios.ativos().get(0);

        assertThat(votacao.vista(jogo, "j1").categorias()).hasSize(1 + premios.ativos().size());

        Map<Long, Long> boletim = mvp(id("j2"));
        boletim.put(frango.getId(), id("j3"));
        votacao.votar(jogo, boletim, "j1");
        votacao.votar(jogo, mvp(id("j1")), "j2");
        votacao.fechar(jogo, ADMIN);

        VotacaoView v = votacao.vista(jogo, "j4");
        assertThat(v.outrosPremios()).singleElement().satisfies(r -> {
            assertThat(r.categoria()).isEqualTo(frango.getNome());
            assertThat(r.vencedores()).containsExactly("Nome j3");
        });
    }

    @Test
    void oAdminAnulaUmVotoEQuemVotouPodeVotarOutraVez() {
        fecharJogo();
        votacao.votar(jogo, mvp(id("j2")), "j1");
        Long voto = votacao.votosDoJogo(jogo, ADMIN).get(0).id();

        votacao.anular(jogo, voto, ADMIN);

        assertThat(votacao.votosDoJogo(jogo, ADMIN).get(0).anulado()).isTrue();
        assertThat(votacao.vista(jogo, "j1").jaVotei()).isFalse();
        assertThat(votacao.vista(jogo, "j2").votaram()).isEmpty();
        votacao.votar(jogo, mvp(id("j3")), "j1");
        assertThat(votacao.vista(jogo, "j1").jaVotei()).isTrue();
    }

    @Test
    void oOrganizadorOuOAdminFechamEReabrem() {
        fecharJogo();
        votacao.votar(jogo, mvp(id("j2")), "j1");

        assertThatThrownBy(() -> votacao.fechar(jogo, "j1")).isInstanceOf(OperacaoInvalidaException.class);
        votacao.fechar(jogo, ADMIN);
        assertThat(votacao.vista(jogo, "j1").fechada()).isTrue();
        assertThat(votacao.vista(jogo, "j1").mvpResultado().vencedores()).containsExactly("Nome j2");

        votacao.reabrir(jogo, ADMIN);
        assertThat(votacao.vista(jogo, "j1").aberta()).isTrue();
        assertThat(votacao.vista(jogo, "j1").resultados()).isEmpty();
    }

    @Test
    void fecharSemVotosNaoDaErro() {
        fecharJogo();
        votacao.fechar(jogo, ADMIN);

        assertThat(votacao.vista(jogo, "j1").mvpResultado()).isNull();
    }
}
