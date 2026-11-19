package pt.futapp.estatisticas;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.jogo.Convocatoria;
import pt.futapp.jogo.Equipa;
import pt.futapp.jogo.EquipasService;
import pt.futapp.jogo.JogoService;
import pt.futapp.jogo.RegistoService;
import pt.futapp.utilizador.UtilizadorService;
import pt.futapp.votacao.VotacaoService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EstatisticasServiceTest {

    private static final String URL = "http://localhost:8080";

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;
    @Autowired private EquipasService equipas;
    @Autowired private RegistoService registo;
    @Autowired private VotacaoService votacao;
    @Autowired private EstatisticasService estatisticas;
    @Autowired private ResumoService resumo;
    @Autowired private pt.futapp.utilizador.RemocaoService remocao;
    @Autowired private pt.futapp.utilizador.JogadorService jogadores;

    private Long jogo;
    private final Map<String, Long> ins = new HashMap<>();

    /**
     * Um jogo fechado: Coletes (j1, j2) 2 – 1 Sem coletes (j3, j4, Zé convidado).
     * j1 marca 2 (uma com assistência de j2), j3 marca 1, j4 falta. MVP: j1 (2 votos).
     */
    @BeforeEach
    void preparar() {
        jogo = criarJogo(jogos, ADMIN);
        for (String u : new String[] {"j1", "j2", "j3", "j4"}) {
            jogos.inscrever(jogo, registar(utilizadores, u));
        }
        jogos.adicionarConvidado(jogo, "Zé", ADMIN);
        for (Convocatoria.Linha l : jogos.convocatoria(jogo, ADMIN, URL).inscritos()) {
            ins.put(l.nome().replace("Nome ", ""), l.inscricaoId());
        }
        equipas.gerar(jogo, ADMIN);
        for (String u : new String[] {"j1", "j2"}) {
            equipas.mover(jogo, ins.get(u), Equipa.COLETES, ADMIN);
        }
        for (String u : new String[] {"j3", "j4", "Zé"}) {
            equipas.mover(jogo, ins.get(u), Equipa.SEM_COLETES, ADMIN);
        }
        jogos.fecharConvocatoria(jogo, ADMIN);
        equipas.publicar(jogo, ADMIN, URL);
        registo.adicionarGolo(jogo, Equipa.COLETES, ins.get("j1"), ins.get("j2"), ADMIN);
        registo.adicionarGolo(jogo, Equipa.COLETES, ins.get("j1"), null, ADMIN);
        registo.adicionarGolo(jogo, Equipa.SEM_COLETES, ins.get("j3"), null, ADMIN);
        registo.marcarFalta(jogo, ins.get("j4"), true, ADMIN);
        registo.fechar(jogo, ADMIN);
        votacao.votar(jogo, mvp(ins.get("j1")), "j2");
        votacao.votar(jogo, mvp(ins.get("j1")), "j3");
        votacao.votar(jogo, mvp(ins.get("j3")), "j1"); // todos votaram: fecha sozinha
    }

    private static Map<Long, Long> mvp(Long inscricao) {
        Map<Long, Long> m = new HashMap<>();
        m.put(null, inscricao);
        return m;
    }

    private EstatisticasView.Linha linha(String u) {
        return estatisticas.temporada(ADMIN).tabela().stream()
            .filter(l -> l.nome().equals("Nome " + u)).findFirst().orElseThrow();
    }

    @Test
    void contaJogosVitoriasGolosAssistenciasMvpsEFaltas() {
        EstatisticasView e = estatisticas.temporada(ADMIN);
        assertThat(e.jogosFechados()).isEqualTo(1);

        assertThat(linha("j1")).satisfies(l -> {
            assertThat(l.jogos()).isEqualTo(1);
            assertThat(l.vitorias()).isEqualTo(1);
            assertThat(l.golos()).isEqualTo(2);
            assertThat(l.mvps()).isEqualTo(1);
        });
        assertThat(linha("j2").assistencias()).isEqualTo(1);
        assertThat(linha("j3").derrotas()).isEqualTo(1);
        assertThat(linha("j3").golos()).isEqualTo(1);
        assertThat(linha("j4").faltas()).isEqualTo(1);
        assertThat(linha("j4").jogos()).isZero();
    }

    @Test
    void osConvidadosEntramNasEstatisticasPeloNome() {
        EstatisticasView.Linha ze = estatisticas.temporada(ADMIN).tabela().stream()
            .filter(l -> l.nome().equals("Zé")).findFirst().orElseThrow();

        assertThat(ze.convidado()).isTrue();
        assertThat(ze.jogadorId()).isNull();
        assertThat(ze.jogos()).isEqualTo(1);
        assertThat(ze.derrotas()).isEqualTo(1);
    }

    @Test
    void osRankingsComecamPeloLider() {
        EstatisticasView e = estatisticas.temporada("j1");

        EstatisticasView.Ranking marcadores = e.rankings().stream()
            .filter(r -> r.titulo().equals("Melhores marcadores")).findFirst().orElseThrow();
        assertThat(marcadores.lugares().get(0).nome()).isEqualTo("Nome j1");
        assertThat(marcadores.lugares().get(0).valor()).isEqualTo(2);
        assertThat(marcadores.lugares().get(0).eu()).isTrue();
        assertThat(e.rankings()).extracting(EstatisticasView.Ranking::titulo).contains("Mais MVPs", "Mais faltas");
    }

    @Test
    void soContamOsJogosFechados() {
        Long outro = criarJogo(jogos, ADMIN);
        jogos.inscrever(outro, "j1");
        jogos.inscrever(outro, "j2");
        equipas.gerar(outro, ADMIN);
        jogos.fecharConvocatoria(outro, ADMIN);
        equipas.publicar(outro, ADMIN, URL);
        registo.definirResultado(outro, 5, 0, ADMIN);

        assertThat(estatisticas.temporada(ADMIN).jogosFechados()).isEqualTo(1);
        assertThat(linha("j1").jogos()).isEqualTo(1);
    }

    @Test
    void osNumerosDeUmJogadorParaACarta() {
        Long idJ1 = utilizadores.vista("j1").orElseThrow().id();
        String novo = registar(utilizadores, "novo");

        assertThat(estatisticas.doJogador(idJ1, "j1").golos()).isEqualTo(2);
        EstatisticasView.Linha vazia = estatisticas.doJogador(utilizadores.vista(novo).orElseThrow().id(), ADMIN);
        assertThat(vazia.temJogos()).isFalse();
        assertThat(vazia.jogos()).isZero();
    }

    @Test
    void oResumoParaOWhatsAppTemResultadoMarcadoresMvpEPresencas() {
        ResumoService.ResumoView r = resumo.resumo(jogo, "j1", URL);

        assertThat(r.jogaram()).isEqualTo(4);
        assertThat(r.faltas()).isEqualTo(1);
        assertThat(r.votaram()).isEqualTo(3);
        String texto = URLDecoder.decode(r.whatsappUrl().substring("https://wa.me/?text=".length()), StandardCharsets.UTF_8);
        assertThat(texto)
            .contains("Coletes 2 – 1 Sem coletes")
            .contains("Golos: Nome j1 2 · Nome j3")
            .contains("MVP: Nome j1 (2 votos)")
            .contains("4 jogaram · 1 falta")
            .contains(URL + "/jogos/" + jogo);
    }

    @Test
    void quemERemovidoFicaNasEstatisticasComoConvidado() {
        Long idJ1 = utilizadores.vista("j1").orElseThrow().id();

        assertThat(remocao.remover(idJ1, ADMIN)).isEqualTo(pt.futapp.utilizador.RemocaoService.Resultado.REMOVIDO);

        EstatisticasView.Linha j1 = linha("j1");
        assertThat(j1.convidado()).isTrue();
        assertThat(j1.jogadorId()).isNull();
        assertThat(j1.golos()).isEqualTo(2);
        assertThat(j1.mvps()).isEqualTo(1);
        assertThat(j1.vitorias()).isEqualTo(1);
        // o voto que deu continua a contar
        assertThat(linha("j3").golos()).isEqualTo(1);
        assertThat(votacao.vista(jogo, ADMIN).mvpResultado().vencedores()).containsExactly("Nome j1");
        assertThat(jogos.convocatoria(jogo, ADMIN, URL).inscritos())
            .anySatisfy(l -> {
                assertThat(l.nome()).isEqualTo("Nome j1");
                assertThat(l.convidado()).isTrue();
                assertThat(l.jogadorId()).isNull();
            });
    }

    // review 5: um membro removido não se junta a um convidado com o mesmo nome
    @Test
    void umRemovidoComAMesmaAlcunhaDeUmConvidadoFicaNumaLinhaSo() {
        pt.futapp.utilizador.DadosForm f = new pt.futapp.utilizador.DadosForm();
        f.setNome("Nome j3");
        f.setAlcunha("Zé");
        jogadores.atualizar(utilizadores.vista("j3").orElseThrow().id(), f, "j3");

        remocao.remover(utilizadores.vista("j3").orElseThrow().id(), ADMIN);

        var zes = estatisticas.temporada(ADMIN).tabela().stream().filter(l -> l.nome().equals("Zé")).toList();
        assertThat(zes).hasSize(2).allSatisfy(l -> assertThat(l.convidado()).isTrue());
        assertThat(zes).extracting(EstatisticasView.Linha::golos).containsExactlyInAnyOrder(1, 0);
    }

    // ------------------------------------------------------------ ordenação (19/11/2026)

    /**
     * Segundo jogo fechado: Abel (convidado, sem coletes) marca e ganha 1–0 a j1 (coletes). Abel fica com V1 G1,
     * valores que o poriam no topo; tem de ficar no fundo da tabela e atrás dos membros empatados nos rankings.
     */
    private void jogoComConvidadoQueGanha() {
        Long outro = criarJogo(jogos, ADMIN);
        jogos.inscrever(outro, "j1");
        jogos.adicionarConvidado(outro, "Abel", ADMIN);
        Map<String, Long> i2 = new HashMap<>();
        for (Convocatoria.Linha l : jogos.convocatoria(outro, ADMIN, URL).inscritos()) {
            i2.put(l.nome().replace("Nome ", ""), l.inscricaoId());
        }
        equipas.gerar(outro, ADMIN);
        equipas.mover(outro, i2.get("j1"), Equipa.COLETES, ADMIN);
        equipas.mover(outro, i2.get("Abel"), Equipa.SEM_COLETES, ADMIN);
        jogos.fecharConvocatoria(outro, ADMIN);
        equipas.publicar(outro, ADMIN, URL);
        registo.adicionarGolo(outro, Equipa.SEM_COLETES, i2.get("Abel"), null, ADMIN);
        registo.fechar(outro, ADMIN);
        votacao.votar(outro, mvp(i2.get("Abel")), "j1"); // único votante: fecha sozinha
    }

    private java.util.List<String> nomesDaTabela(OrdemTabela ordem) {
        return estatisticas.temporada(ADMIN, ordem).tabela().stream().map(EstatisticasView.Linha::nome).toList();
    }

    @Test
    void aTabelaVemPorVitoriasEOsGolosDesempatam() {
        // j1 V1 G2 · j2 V1 G0 · j3 V0 G1 · j4 só faltou · Zé convidado
        assertThat(estatisticas.temporada(ADMIN).ordem()).isEqualTo(OrdemTabela.VITORIAS);
        assertThat(nomesDaTabela(OrdemTabela.VITORIAS)).containsExactly("Nome j1", "Nome j2", "Nome j3", "Nome j4", "Zé");
    }

    @Test
    void aTabelaPodeVirPorGolosEAsVitoriasDesempatam() {
        assertThat(nomesDaTabela(OrdemTabela.GOLOS)).containsExactly("Nome j1", "Nome j3", "Nome j2", "Nome j4", "Zé");
    }

    @Test
    void osConvidadosFicamNoFundoDaTabelaMesmoComMaisVitoriasOuGolos() {
        jogoComConvidadoQueGanha();

        // j1 agora V1 G2 em 2 jogos; Abel V1 G1 mas é convidado. Convidados entre si pela mesma regra.
        assertThat(nomesDaTabela(OrdemTabela.VITORIAS)).containsExactly("Nome j1", "Nome j2", "Nome j3", "Nome j4", "Abel", "Zé");
        assertThat(nomesDaTabela(OrdemTabela.GOLOS)).containsExactly("Nome j1", "Nome j3", "Nome j2", "Nome j4", "Abel", "Zé");
    }

    @Test
    void nosRankingsMandaOValorEEmEmpateOsConvidadosVemDepois() {
        jogoComConvidadoQueGanha();
        EstatisticasView e = estatisticas.temporada(ADMIN);

        EstatisticasView.Ranking vitorias = e.rankings().stream()
            .filter(r -> r.titulo().equals("Mais vitórias")).findFirst().orElseThrow();
        assertThat(vitorias.lugares()).extracting(EstatisticasView.Lugar::nome).containsExactly("Nome j1", "Nome j2", "Abel");
        assertThat(vitorias.lugares()).extracting(EstatisticasView.Lugar::posicao).containsExactly(1, 1, 1);

        EstatisticasView.Ranking marcadores = e.rankings().stream()
            .filter(r -> r.titulo().equals("Melhores marcadores")).findFirst().orElseThrow();
        assertThat(marcadores.lugares()).extracting(EstatisticasView.Lugar::nome).containsExactly("Nome j1", "Nome j3", "Abel");
        assertThat(marcadores.lugares()).extracting(EstatisticasView.Lugar::valor).containsExactly(2L, 1L, 1L);
    }

    @Test
    void aOrdemDaTabelaLeOParametroDoEndereco() {
        assertThat(OrdemTabela.de("golos")).isEqualTo(OrdemTabela.GOLOS);
        assertThat(OrdemTabela.de("GOLOS")).isEqualTo(OrdemTabela.GOLOS);
        assertThat(OrdemTabela.de("vitorias")).isEqualTo(OrdemTabela.VITORIAS);
        assertThat(OrdemTabela.de(null)).isEqualTo(OrdemTabela.VITORIAS);
        assertThat(OrdemTabela.de("faltas")).isEqualTo(OrdemTabela.VITORIAS);
    }
}
