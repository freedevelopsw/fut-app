package pt.futapp.utilizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.jogo.Convocatoria;
import pt.futapp.jogo.JogoService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RemocaoServiceTest {

    @Autowired private UtilizadorService utilizadores;
    @Autowired private UtilizadorRepository repo;
    @Autowired private JogadorService jogadores;
    @Autowired private JogoService jogos;
    @Autowired private RemocaoService remocao;
    @Autowired private pt.futapp.jogo.InscricaoRepository inscricoes;

    private Long id(String username) {
        return utilizadores.vista(username).orElseThrow().id();
    }

    @Test
    void quemNuncaJogouEApagadoEOUsernameFicaLivre() {
        String rui = registar(utilizadores, "rui");
        Long id = id(rui);

        assertThat(remocao.remover(id, ADMIN)).isEqualTo(RemocaoService.Resultado.APAGADO);

        assertThat(repo.findById(id)).isEmpty();
        assertThat(utilizadores.membros(ADMIN)).extracting(MembroView::username).doesNotContain("rui");
        assertThat(utilizadores.estaBloqueado("rui")).isTrue(); // uma sessão antiga termina
        assertThat(registar(utilizadores, "rui")).isEqualTo("rui");
    }

    @Test
    void quemSoEstavaNumaConvocatoriaAbertaEApagadoEQuemEstavaEmEsperaSobe() {
        Long jogo = criarJogo(jogos, ADMIN);
        for (int i = 1; i <= 12; i++) {
            jogos.inscrever(jogo, registar(utilizadores, "j" + i));
        }
        jogos.inscrever(jogo, registar(utilizadores, "espera"));

        assertThat(remocao.remover(id("j1"), ADMIN)).isEqualTo(RemocaoService.Resultado.APAGADO);

        Convocatoria c = jogos.convocatoria(jogo, ADMIN, "http://localhost");
        assertThat(c.inscritos()).hasSize(12).extracting(Convocatoria.Linha::nome)
            .doesNotContain("Nome j1").contains("Nome espera");
        assertThat(c.espera()).isEmpty();
        assertThat(c.desistencias()).isEmpty();
    }

    /** Um jogo cancelado onde a pessoa estava inscrita: é histórico (um jogo cancelado não volta a abrir). */
    private Long jogoCanceladoCom(String... usernames) {
        Long jogo = criarJogo(jogos, ADMIN);
        for (String u : usernames) {
            jogos.inscrever(jogo, u);
        }
        jogos.cancelar(jogo, null, ADMIN);
        return jogo;
    }

    @Test
    void comHistoricoFicaComoConvidadoESaiDeTodasAsListas() {
        String rui = registar(utilizadores, "rui");
        Long id = id(rui);
        Long jogo = jogoCanceladoCom(rui);

        assertThat(remocao.remover(id, ADMIN)).isEqualTo(RemocaoService.Resultado.REMOVIDO);

        Utilizador removido = repo.findById(id).orElseThrow();
        assertThat(removido.isRemovido()).isTrue();
        assertThat(removido.isBloqueado()).isTrue();
        assertThat(removido.getUsername()).isEqualTo("removido-" + id);
        assertThat(utilizadores.estaBloqueado("rui")).isTrue();
        assertThat(utilizadores.membros(ADMIN)).extracting(MembroView::id).doesNotContain(id);
        assertThat(jogadores.jogadores(ADMIN)).extracting(JogadorResumo::id).doesNotContain(id);
        assertThat(repo.findMembro(id)).isEmpty();
        assertThatThrownBy(() -> jogadores.carta(id, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);

        Convocatoria.Linha linha = jogos.convocatoria(jogo, ADMIN, "http://localhost").inscritos().get(0);
        assertThat(linha.nome()).isEqualTo("Nome rui");
        assertThat(linha.convidado()).isTrue();
        assertThat(linha.jogadorId()).isNull();
        // a inscrição continua ligada à conta (as estatísticas não se misturam com convidados do mesmo nome)
        assertThat(inscricoes.findByJogador(removido)).hasSize(1);

        assertThat(registar(utilizadores, "rui")).isEqualTo("rui");
        assertThatThrownBy(() -> remocao.remover(id, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
    }

    // review 1 e 8: uma conta removida não volta, por nenhum caminho
    @Test
    void umaContaRemovidaNaoPodeSerDesbloqueadaNemAlterada() {
        String rui = registar(utilizadores, "rui");
        Long id = id(rui);
        jogoCanceladoCom(rui);
        remocao.remover(id, ADMIN);

        assertThatThrownBy(() -> utilizadores.desbloquear(id, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.definirNivel(id, NivelAcesso.ADMIN, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.gerarLinkPassword(id, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.bloquear(id, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(utilizadores.loadUserByUsername("removido-" + id).isEnabled()).isFalse();

        // mesmo por um caminho que não passe pelos serviços, a própria conta recusa
        Utilizador removido = repo.findById(id).orElseThrow();
        assertThatThrownBy(removido::desbloquear).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> removido.definirRoles(java.util.Set.of(Role.ADMIN)))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> removido.alterarPasswordHash("x")).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(repo.findById(id).orElseThrow().isBloqueado()).isTrue();
    }

    // review 2
    @Test
    void ninguemSeRegistaComUmUsernameDeContaRemovidaEARemocaoNuncaChoca() {
        assertThatThrownBy(() -> registar(utilizadores, "removido-99")).isInstanceOf(OperacaoInvalidaException.class);

        String rui = registar(utilizadores, "rui");
        Long id = id(rui);
        jogoCanceladoCom(rui);
        // uma conta antiga que já tinha esse nome (de antes de a regra existir)
        repo.save(new Utilizador("removido-" + id, "Antigo", "x", java.util.EnumSet.of(Role.JOGADOR),
            java.time.LocalDateTime.now()));

        assertThat(remocao.remover(id, ADMIN)).isEqualTo(RemocaoService.Resultado.REMOVIDO);
        assertThat(repo.findById(id).orElseThrow().getUsername()).isEqualTo("removido-" + id + "-2");
    }

    // review 3: num jogo ainda por jogar (convocatória fechada, equipas publicadas) não fica um "jogador fantasma"
    @Test
    void numJogoAindaPorJogarSaiDaListaEQuemEstavaEmEsperaSobe() {
        Long antigo = jogoCanceladoCom(registar(utilizadores, "j1"));
        Long jogo = criarJogo(jogos, ADMIN);
        jogos.inscrever(jogo, "j1");
        for (int i = 2; i <= 12; i++) {
            jogos.inscrever(jogo, registar(utilizadores, "j" + i));
        }
        jogos.inscrever(jogo, registar(utilizadores, "espera"));
        jogos.fecharConvocatoria(jogo, ADMIN);

        assertThat(remocao.remover(id("j1"), ADMIN)).isEqualTo(RemocaoService.Resultado.REMOVIDO);

        Convocatoria c = jogos.convocatoria(jogo, ADMIN, "http://localhost");
        assertThat(c.inscritos()).hasSize(12).extracting(Convocatoria.Linha::nome)
            .doesNotContain("Nome j1").contains("Nome espera");
        assertThat(c.desistencias()).isEmpty();
        // no jogo cancelado (histórico) continua lá, como convidado
        assertThat(jogos.convocatoria(antigo, ADMIN, "http://localhost").inscritos())
            .singleElement().satisfies(l -> assertThat(l.convidado()).isTrue());
    }

    // review 6: uma desistência que a pessoa fez sozinha continua a ser dela
    @Test
    void aDesistenciaQueAPessoaFezSozinhaNaoPassaAParecerFeitaPorOutro() {
        String rui = registar(utilizadores, "rui");
        Long jogo = criarJogo(jogos, ADMIN);
        jogos.inscrever(jogo, rui);
        jogos.cancelarPropria(jogo, rui);
        jogos.cancelar(jogo, null, ADMIN);

        remocao.remover(id(rui), ADMIN);

        Convocatoria.Desistencia d = jogos.convocatoria(jogo, ADMIN, "http://localhost").desistencias().get(0);
        assertThat(d.nome()).isEqualTo("Nome rui");
        assertThat(d.registadaPor()).isNull();
    }

    @Test
    void soOAdminRemoveENinguemSeRemoveASiProprio() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");

        assertThatThrownBy(() -> remocao.remover(id(ana), rui)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> remocao.remover(id(ADMIN), ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(repo.findById(id(ana))).isPresent();
    }
}
