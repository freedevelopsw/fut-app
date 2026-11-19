package pt.futapp.jogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.UtilizadorService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistoServiceTest {

    private static final String URL = "http://localhost:8080";

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;
    @Autowired private EquipasService equipas;
    @Autowired private RegistoService registo;

    private Long jogo;
    private RegistoView.Presenca c1;
    private RegistoView.Presenca c2;
    private RegistoView.Presenca s1;

    /** Jogo com 4 jogadores, equipas geradas e publicadas. */
    @BeforeEach
    void preparar() {
        jogo = criarJogo(jogos, ADMIN);
        for (String u : new String[] {"j1", "j2", "j3", "j4"}) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        equipas.gerar(jogo, ADMIN);
        jogos.fecharConvocatoria(jogo, ADMIN);
        equipas.publicar(jogo, ADMIN, URL);
        RegistoView v = vista();
        c1 = v.coletes().get(0);
        c2 = v.coletes().get(1);
        s1 = v.semColetes().get(0);
    }

    private RegistoView vista() {
        return registo.vista(jogo, ADMIN);
    }

    @Test
    void semEquipasPublicadasNaoSeRegista() {
        Long outro = criarJogo(jogos, ADMIN);

        assertThat(registo.vista(outro, ADMIN).podeRegistar()).isFalse();
        assertThatThrownBy(() -> registo.adicionarGolo(outro, Equipa.COLETES, null, null, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class)
            .hasMessageContaining("Publica primeiro");
    }

    @Test
    void oPrimeiroGoloPassaOJogoParaEmRegistoEOResultadoContaOsGolos() {
        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), c2.inscricaoId(), ADMIN);
        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), null, ADMIN);
        registo.adicionarGolo(jogo, Equipa.SEM_COLETES, null, null, ADMIN);

        RegistoView v = vista();
        assertThat(v.estado()).isEqualTo(EstadoJogo.EM_REGISTO);
        assertThat(v.golosColetes()).isEqualTo(2);
        assertThat(v.golosSemColetes()).isEqualTo(1);
        assertThat(v.marcadoresColetes()).extracting(RegistoView.Marcador::golos).containsExactly(2L);
        assertThat(v.golos().get(0).parcial()).isEqualTo("2–1");
        assertThat(v.golos().get(2).assistencia()).isEqualTo(c2.nome());
    }

    @Test
    void oMarcadorTemDeSerDaEquipaQueMarcou() {
        assertThatThrownBy(() -> registo.adicionarGolo(jogo, Equipa.COLETES, s1.inscricaoId(), null, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), c1.inscricaoId(), ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> registo.adicionarGolo(jogo, Equipa.COLETES, null, c1.inscricaoId(), ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void apagarUmGolo() {
        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), null, ADMIN);

        registo.apagarGolo(jogo, vista().golos().get(0).id(), ADMIN);

        assertThat(vista().golosColetes()).isZero();
    }

    @Test
    void soOResultadoCriaGolosSemMarcadorSemApagarOsQueTemMarcador() {
        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), null, ADMIN);

        registo.definirResultado(jogo, 3, 2, ADMIN);
        RegistoView v = vista();
        assertThat(v.golosColetes()).isEqualTo(3);
        assertThat(v.golosSemColetes()).isEqualTo(2);
        assertThat(v.marcadoresColetes()).hasSize(1);

        registo.definirResultado(jogo, 1, 0, ADMIN);
        assertThat(vista().golosColetes()).isEqualTo(1);
        assertThat(vista().golosSemColetes()).isZero();

        assertThatThrownBy(() -> registo.definirResultado(jogo, 0, 0, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class)
            .hasMessageContaining("com marcador");
    }

    @Test
    void marcarFaltasDeComparencia() {
        registo.marcarFalta(jogo, s1.inscricaoId(), true, ADMIN);

        RegistoView v = vista();
        assertThat(v.faltas()).isEqualTo(1);
        assertThat(v.presentes()).isEqualTo(3);
        assertThat(v.semColetes()).filteredOn(RegistoView.Presenca::faltou).hasSize(1);

        registo.marcarFalta(jogo, s1.inscricaoId(), false, ADMIN);
        assertThat(vista().faltas()).isZero();
    }

    @Test
    void fecharAbreAVotacaoEBloqueiaORegistoAteReabrir() {
        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), null, ADMIN);

        registo.fechar(jogo, ADMIN);
        assertThat(vista().estado()).isEqualTo(EstadoJogo.VOTACAO_ABERTA);
        assertThat(vista().podeRegistar()).isFalse();
        assertThat(vista().podeReabrir()).isTrue();
        assertThatThrownBy(() -> registo.adicionarGolo(jogo, Equipa.COLETES, null, null, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class);

        registo.reabrir(jogo, ADMIN);
        assertThat(vista().estado()).isEqualTo(EstadoJogo.EM_REGISTO);
        registo.adicionarGolo(jogo, Equipa.SEM_COLETES, null, null, ADMIN);
        assertThat(vista().golosSemColetes()).isEqualTo(1);
    }

    @Test
    void podeFecharSemGolosRegistados() {
        registo.fechar(jogo, ADMIN);

        assertThat(vista().estado()).isEqualTo(EstadoJogo.VOTACAO_ABERTA);
    }

    @Test
    void soOOrganizadorOuOAdminFecham_MarcamFaltasEDefinemOResultado() {
        assertThatThrownBy(() -> registo.fechar(jogo, "j1")).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> registo.definirResultado(jogo, 1, 0, "j1")).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> registo.marcarFalta(jogo, c1.inscricaoId(), true, "j1"))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(registo.vista(jogo, "j1").podeRegistar()).isFalse();
    }

    @Test
    void qualquerJogadorAdicionaEApagaGolos() {
        assertThat(registo.vista(jogo, "j1").podeMarcarGolos()).isTrue();

        registo.adicionarGolo(jogo, Equipa.COLETES, c1.inscricaoId(), null, "j1");
        registo.adicionarGolo(jogo, Equipa.SEM_COLETES, null, null, ADMIN);

        RegistoView v = vista();
        assertThat(v.estado()).isEqualTo(EstadoJogo.EM_REGISTO);
        assertThat(v.golosColetes()).isEqualTo(1);
        // um jogador também apaga golos registados por outros
        Long doAdmin = v.golos().stream().filter(g -> g.equipa() == Equipa.SEM_COLETES).findFirst().orElseThrow().id();
        registo.apagarGolo(jogo, doAdmin, "j2");
        assertThat(vista().golosSemColetes()).isZero();
    }

    @Test
    void comOJogoFechadoNinguemMarcaGolos() {
        registo.fechar(jogo, ADMIN);

        assertThat(registo.vista(jogo, "j1").podeMarcarGolos()).isFalse();
        assertThatThrownBy(() -> registo.adicionarGolo(jogo, Equipa.COLETES, null, null, "j1"))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void depoisDeComecarORegistoAsEquipasEAListaFicamFixas() {
        registo.adicionarGolo(jogo, Equipa.COLETES, null, null, ADMIN);

        assertThatThrownBy(() -> equipas.gerar(jogo, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.removerInscricao(jogo, c1.inscricaoId(), ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.adicionarConvidado(jogo, "Zé", ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(equipas.vista(jogo, "j1", URL).publicadas()).isTrue();
    }
}
