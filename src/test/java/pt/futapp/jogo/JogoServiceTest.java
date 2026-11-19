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

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
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
    void cancelarGuardaOMotivoEPorOmissaoEFaltaDeJogadores() {
        inscreverJogadores(3);

        jogos.cancelar(jogo, "  ", organizador);

        Convocatoria c = convocatoria();
        assertThat(c.cancelado()).isTrue();
        assertThat(c.motivoCancelamento()).isEqualTo("Falta de jogadores");
        assertThat(c.canceladoPor()).isEqualTo(NOME_ADMIN);
        assertThat(c.inscritos()).hasSize(3);
        assertThat(c.whatsappCancelamentoUrl()).startsWith("https://wa.me/?text=");
    }

    @Test
    void umJogoCanceladoSaiDosProximosENaoAceitaInscricoes() {
        jogos.cancelar(jogo, "Chuva", organizador);

        assertThat(jogos.proximosJogos()).extracting(JogoResumo::id).doesNotContain(jogo);
        assertThat(jogos.jogosAnteriores()).filteredOn(j -> j.id().equals(jogo))
            .singleElement().satisfies(j -> assertThat(j.cancelado()).isTrue());
        assertThat(convocatoria().motivoCancelamento()).isEqualTo("Chuva");
        assertThatThrownBy(() -> jogos.inscrever(jogo, registar(utilizadores, "tarde"))).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogos.cancelar(jogo, "Outra vez", organizador)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void umJogoCanceladoNaoVoltaAAbrir() {
        jogos.fecharConvocatoria(jogo, organizador);
        jogos.cancelar(jogo, null, organizador);

        assertThat(java.util.Arrays.stream(JogoService.class.getMethods()).map(java.lang.reflect.Method::getName))
            .doesNotContain("reativar");
        assertThat(convocatoria().estado()).isEqualTo(EstadoJogo.CANCELADO);
        assertThatThrownBy(() -> jogos.fecharConvocatoria(jogo, organizador)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void umLocalSoComEspacosContaComoSemLocal() {
        NovoJogoForm form = new NovoJogoForm();
        form.setDataHora(java.time.LocalDateTime.now().plusDays(4).withSecond(0).withNano(0));
        form.setLocal("  Pavilhão  ");
        Long comLocal = jogos.criar(form, organizador);
        assertThat(jogos.convocatoria(comLocal, organizador, URL).local()).isEqualTo("Pavilhão");

        // linha antiga gravada só com espaços: nem as listas nem o WhatsApp mostram local
        jdbc.update("UPDATE jogo SET local_jogo = '   ' WHERE id = ?", comLocal);
        em.clear();
        Convocatoria c = jogos.convocatoria(comLocal, organizador, URL);
        assertThat(c.local()).isNull();
        assertThat(URLDecoder.decode(c.whatsappUrl(), StandardCharsets.UTF_8)).doesNotContain("Local:");
        assertThat(jogos.proximosJogos()).filteredOn(j -> j.id().equals(comLocal)).extracting(JogoResumo::local)
            .containsOnlyNulls();
    }

    @Test
    void soOOrganizadorOuOAdminCancelam() {
        String j1 = registar(utilizadores, "j1");

        assertThatThrownBy(() -> jogos.cancelar(jogo, "Não quero", j1)).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(convocatoria().cancelado()).isFalse();
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

    @Test
    void oLocalEOpcional() {
        NovoJogoForm form = new NovoJogoForm();
        form.setDataHora(java.time.LocalDateTime.now().plusDays(3).withSecond(0).withNano(0));
        form.setLocal("   ");
        Long semLocal = jogos.criar(form, organizador);

        Convocatoria c = jogos.convocatoria(semLocal, organizador, URL);
        assertThat(c.local()).isNull();
        String texto = URLDecoder.decode(c.whatsappUrl().substring("https://wa.me/?text=".length()), StandardCharsets.UTF_8);
        assertThat(texto).doesNotContain("Local:").contains("Inscritos (0/12)");
        assertThat(jogos.proximosJogos()).anySatisfy(j -> {
            assertThat(j.id()).isEqualTo(semLocal);
            assertThat(j.local()).isNull();
        });

        jogos.cancelar(semLocal, null, organizador);
        String aviso = URLDecoder.decode(jogos.convocatoria(semLocal, organizador, URL).whatsappCancelamentoUrl()
            .substring("https://wa.me/?text=".length()), StandardCharsets.UTF_8);
        assertThat(aviso).doesNotContain("null").contains("foi cancelado");
    }

    @Test
    void comLocalAMensagemIndicaO() {
        String texto = URLDecoder.decode(convocatoria().whatsappUrl().substring("https://wa.me/?text=".length()),
            StandardCharsets.UTF_8);
        assertThat(texto).contains("Local: Pavilhão");
    }

    private Long jogoCanceladoEm(java.time.LocalDateTime quando) {
        NovoJogoForm form = new NovoJogoForm();
        form.setDataHora(quando);
        Long id = jogos.criar(form, organizador);
        jogos.cancelar(id, null, organizador);
        return id;
    }

    @Test
    void osAnterioresSaoSoDoMesAtualEOHistoricoTemOAnoCorrente() {
        java.time.LocalDate hoje = java.time.LocalDate.now(pt.futapp.config.AppConfig.ZONA);
        java.time.LocalDateTime inicioMes = hoje.withDayOfMonth(1).atTime(21, 0);
        Long esteMes = jogoCanceladoEm(inicioMes);
        Long mesPassado = jogoCanceladoEm(inicioMes.minusMonths(1));
        Long anoPassado = jogoCanceladoEm(hoje.withDayOfYear(1).minusDays(1).atTime(21, 0));

        assertThat(jogos.jogosAnteriores()).extracting(JogoResumo::id)
            .contains(esteMes).doesNotContain(mesPassado, anoPassado);

        var noHistorico = jogos.historico(organizador).stream().flatMap(m -> m.jogos().stream()).map(JogoResumo::id).toList();
        assertThat(noHistorico).contains(esteMes).doesNotContain(anoPassado);
        if (inicioMes.minusMonths(1).getYear() == hoje.getYear()) {
            assertThat(noHistorico).contains(mesPassado);
        } else {
            assertThat(noHistorico).doesNotContain(mesPassado);
        }
        // o jogo continua a abrir pelo link
        assertThat(jogos.convocatoria(mesPassado, organizador, URL).cancelado()).isTrue();
    }

    @Test
    void soOOrganizadorEOAdminVeemOHistorico() {
        String j1 = registar(utilizadores, "j1");

        assertThatThrownBy(() -> jogos.historico(j1)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void osProximosAparecemSempreMesmoQueSejamDoMesSeguinte() {
        NovoJogoForm form = new NovoJogoForm();
        form.setDataHora(java.time.LocalDate.now(pt.futapp.config.AppConfig.ZONA).plusMonths(1).withDayOfMonth(2).atTime(21, 0));
        Long proximoMes = jogos.criar(form, organizador);

        assertThat(jogos.proximosJogos()).extracting(JogoResumo::id).contains(proximoMes);
    }

    // review 4: um jogo cancelado do ano seguinte não aparece no histórico deste ano
    @Test
    void oHistoricoNaoIncluiJogosDoAnoSeguinte() {
        java.time.LocalDate hoje = java.time.LocalDate.now(pt.futapp.config.AppConfig.ZONA);
        Long janeiroSeguinte = jogoCanceladoEm(hoje.plusYears(1).withDayOfYear(1).atTime(21, 0));
        Long janeiroDeste = jogoCanceladoEm(hoje.withDayOfYear(1).atTime(21, 0));

        var historico = jogos.historico(organizador);
        var ids = historico.stream().flatMap(m -> m.jogos().stream()).map(JogoResumo::id).toList();
        assertThat(ids).contains(janeiroDeste).doesNotContain(janeiroSeguinte);
        assertThat(historico.get(historico.size() - 1).mes()).isEqualTo("Janeiro");
    }

    // review 9: o histórico faz um número fixo de consultas, seja qual for o número de jogos
    @Test
    void oHistoricoNaoFazUmaConsultaPorJogo() {
        java.time.LocalDate hoje = java.time.LocalDate.now(pt.futapp.config.AppConfig.ZONA);
        for (int i = 0; i < 20; i++) {
            jogoCanceladoEm(hoje.withDayOfYear(1).atTime(21, 0).plusDays(i));
        }
        em.flush();
        em.clear();
        org.hibernate.stat.Statistics stats = em.getEntityManagerFactory()
            .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            assertThat(jogos.historico(organizador).stream().mapToInt(m -> m.jogos().size()).sum()).isGreaterThanOrEqualTo(20);
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }
}
