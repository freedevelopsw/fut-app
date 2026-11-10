package pt.futapp.jogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.NOME_ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
class JogoServiceTest {

    private static final String URL = "http://localhost:8080";

    @Autowired
    private UtilizadorService utilizadores;

    @Autowired
    private JogoService jogos;

    private String organizador;
    private Long jogo;

    @BeforeEach
    void preparar() {
        organizador = ADMIN; // criado no arranque, com as roles de Admin e Organizador
        jogo = criarJogo(jogos, organizador);
    }

    private void inscreverJogadores(int quantos) {
        for (int i = 1; i <= quantos; i++) {
            jogos.inscrever(jogo, registar(utilizadores, "j" + i));
        }
    }

    private Convocatoria convocatoria() {
        return jogos.convocatoria(jogo, organizador, URL);
    }

    @Test
    void osPrimeiros12FicamInscritosEO13FicaEmEspera() {
        inscreverJogadores(13);

        Convocatoria c = convocatoria();
        assertThat(c.inscritos()).hasSize(12);
        assertThat(c.espera()).extracting(Convocatoria.Linha::nome).containsExactly("Nome j13");
        assertThat(c.espera().get(0).posicao()).isEqualTo(13);
        assertThat(c.vagasLivres()).isZero();
    }

    @Test
    void oJogadorEmEsperaSabeAPosicao() {
        inscreverJogadores(13);

        Convocatoria c = jogos.convocatoria(jogo, "j13", URL);
        assertThat(c.emEspera()).isTrue();
        assertThat(c.posicaoNaEspera()).isEqualTo(1);
        assertThat(c.podeCancelar()).isTrue();
        assertThat(c.podeInscrever()).isFalse();
    }

    @Test
    void cancelarUmInscritoPromoveOPrimeiroDaEsperaEFicaRegistado() {
        inscreverJogadores(13);

        jogos.cancelarPropria(jogo, "j1");

        Convocatoria c = convocatoria();
        assertThat(c.inscritos()).hasSize(12);
        assertThat(c.inscritos()).extracting(Convocatoria.Linha::nome).contains("Nome j13").doesNotContain("Nome j1");
        assertThat(c.espera()).isEmpty();
        assertThat(c.desistencias()).extracting(Convocatoria.Desistencia::nome).containsExactly("Nome j1");
        assertThat(c.desistencias().get(0).registadaPor()).isNull();
    }

    @Test
    void naoSePodeInscreverDuasVezes() {
        String j1 = registar(utilizadores, "j1");
        jogos.inscrever(jogo, j1);

        assertThatThrownBy(() -> jogos.inscrever(jogo, j1))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void depoisDeFecharOJogadorNaoCancelaMasOOrganizadorPodeRemover() {
        String j1 = registar(utilizadores, "j1");
        jogos.inscrever(jogo, j1);
        jogos.fecharConvocatoria(jogo, organizador);

        assertThatThrownBy(() -> jogos.cancelarPropria(jogo, j1))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.inscrever(jogo, registar(utilizadores, "j2")))
            .isInstanceOf(OperacaoInvalidaException.class);

        Long inscricao = convocatoria().inscritos().get(0).inscricaoId();
        jogos.removerInscricao(jogo, inscricao, organizador);

        Convocatoria c = convocatoria();
        assertThat(c.inscritos()).isEmpty();
        assertThat(c.desistencias()).hasSize(1);
        assertThat(c.desistencias().get(0).registadaPor()).isEqualTo(NOME_ADMIN);
    }

    @Test
    void umJogadorSemRoleNaoGereOJogo() {
        String j1 = registar(utilizadores, "j1");

        assertThatThrownBy(() -> jogos.fecharConvocatoria(jogo, j1)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.adicionarConvidado(jogo, "Zé", j1)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> criarJogo(jogos, j1)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void oOrganizadorAdicionaConvidadosEJogadores() {
        String j1 = registar(utilizadores, "j1");

        jogos.adicionarConvidado(jogo, "Zé", organizador);
        jogos.adicionarJogador(jogo, utilizadores.vista(j1).orElseThrow().id(), organizador);

        Convocatoria c = convocatoria();
        assertThat(c.inscritos()).extracting(Convocatoria.Linha::nome).containsExactly("Zé", "Nome j1");
        assertThat(c.inscritos().get(0).convidado()).isTrue();
        assertThat(c.membrosDisponiveis()).extracting(Convocatoria.MembroOpcao::nome).containsExactly(NOME_ADMIN);
    }

    @Test
    void bloquearUmJogadorTiraODasConvocatoriasAbertasEPromoveAEspera() {
        inscreverJogadores(13);
        Long j1 = utilizadores.vista("j1").orElseThrow().id();

        utilizadores.bloquear(j1, ADMIN);

        Convocatoria c = convocatoria();
        assertThat(c.inscritos()).extracting(Convocatoria.Linha::nome).contains("Nome j13").doesNotContain("Nome j1");
        assertThat(c.desistencias()).extracting(Convocatoria.Desistencia::nome).containsExactly("Nome j1");
        assertThat(c.membrosDisponiveis()).extracting(Convocatoria.MembroOpcao::nome).doesNotContain("Nome j1");
        assertThatThrownBy(() -> jogos.adicionarJogador(jogo, j1, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.inscrever(jogo, "j1")).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void bloquearNaoMexeEmConvocatoriasJaFechadas() {
        inscreverJogadores(2);
        jogos.fecharConvocatoria(jogo, organizador);

        utilizadores.bloquear(utilizadores.vista("j1").orElseThrow().id(), ADMIN);

        assertThat(convocatoria().inscritos()).extracting(Convocatoria.Linha::nome).contains("Nome j1");
    }

    @Test
    void oLinkDoWhatsAppTemAListaEOLinkDoJogo() {
        inscreverJogadores(2);

        String url = convocatoria().whatsappUrl();
        assertThat(url).startsWith("https://wa.me/?text=");
        String texto = URLDecoder.decode(url.substring("https://wa.me/?text=".length()), StandardCharsets.UTF_8);
        assertThat(texto)
            .contains("Inscritos (2/12)")
            .contains("1. Nome j1")
            .contains("Faltam 10 jogadores!")
            .contains(URL + "/jogos/" + jogo);
    }
}
