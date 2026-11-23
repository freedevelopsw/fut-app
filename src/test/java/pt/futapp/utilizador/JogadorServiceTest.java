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
class JogadorServiceTest {

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogadorService jogadores;
    @Autowired private JogoService jogos;

    private static DadosForm dados(String nome) {
        DadosForm f = new DadosForm();
        f.setNome(nome);
        return f;
    }

    @Test
    void umaCartaNovaEstaVaziaESemRating() {
        String rui = registar(utilizadores, "rui");

        CartaView carta = jogadores.carta(jogadores.idDe(rui), rui);

        assertThat(carta.preenchida()).isFalse();
        assertThat(carta.rating()).isNull();
        assertThat(carta.ratingTexto()).isEqualTo("–");
        assertThat(carta.nomeVisivel()).isEqualTo("Nome rui");
        assertThat(carta.eu()).isTrue();
        assertThat(carta.podeEditar()).isTrue();
    }

    @Test
    void soONomeEObrigatorio() {
        String rui = registar(utilizadores, "rui");
        Long id = jogadores.idDe(rui);

        jogadores.atualizar(id, dados("Rui Costa"), rui);

        assertThat(jogadores.carta(id, rui).nome()).isEqualTo("Rui Costa");
        assertThatThrownBy(() -> jogadores.atualizar(id, dados("  "), rui)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void oRatingEAMediaDosAtributosPreenchidos() {
        String rui = registar(utilizadores, "rui");
        Long id = jogadores.idDe(rui);
        DadosForm f = dados("Rui");
        f.setPosicao(Posicao.MEDIO);
        f.setPe(Pe.ESQUERDO);
        f.setVelocidade(8);
        f.setPasse(9);
        f.setRemate(6);

        jogadores.atualizar(id, f, rui);

        CartaView carta = jogadores.carta(id, rui);
        assertThat(carta.rating()).isEqualTo(8); // (8 + 9 + 6) / 3 = 7,67
        // a carta mostra a média com uma casa decimal (pedido do dono, 23/11/2026)
        assertThat(carta.ratingTexto()).isEqualTo("7,7");
        assertThat(carta.posicaoSigla()).isEqualTo("MED");
        assertThat(carta.pe()).isEqualTo("Esquerdo");
        assertThat(carta.atributos()).extracting(CartaView.Atributo::valor).containsExactly(8, null, 6, 9, null, null);
    }

    @Test
    void aResistenciaEntraNaCartaENoRating() {
        String rui = registar(utilizadores, "rui");
        Long id = jogadores.idDe(rui);
        DadosForm f = dados("Rui");
        f.setVelocidade(6);
        f.setResistencia(10);

        jogadores.atualizar(id, f, rui);

        CartaView carta = jogadores.carta(id, rui);
        assertThat(carta.rating()).isEqualTo(8); // (6 + 10) / 2
        assertThat(carta.atributos()).extracting(CartaView.Atributo::nome).last().isEqualTo("Resistência");
        assertThat(carta.atributos()).extracting(CartaView.Atributo::valor).last().isEqualTo(10);
        assertThat(jogadores.formulario(id, rui).getResistencia()).isEqualTo(10);

        DadosForm invalido = dados("Rui");
        invalido.setResistencia(11);
        assertThatThrownBy(() -> jogadores.atualizar(id, invalido, rui)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void aAlcunhaApareceNasListas() {
        String rui = registar(utilizadores, "rui");
        DadosForm f = dados("Rui Costa");
        f.setAlcunha("Maestro");
        jogadores.atualizar(jogadores.idDe(rui), f, rui);

        Long jogo = criarJogo(jogos, ADMIN);
        jogos.inscrever(jogo, rui);

        Convocatoria c = jogos.convocatoria(jogo, ADMIN, "http://localhost");
        assertThat(c.inscritos()).extracting(Convocatoria.Linha::nome).containsExactly("Maestro");
        assertThat(c.inscritos().get(0).jogadorId()).isEqualTo(jogadores.idDe(rui));
        assertThat(jogadores.jogadores(ADMIN)).extracting(JogadorResumo::nomeVisivel).contains("Maestro");
    }

    @Test
    void aPosicaoSecundariaTemDeSerDiferenteDaPrincipal() {
        String rui = registar(utilizadores, "rui");
        DadosForm f = dados("Rui");
        f.setPosicao(Posicao.DEFESA);
        f.setPosicaoSecundaria(Posicao.DEFESA);

        assertThatThrownBy(() -> jogadores.atualizar(jogadores.idDe(rui), f, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void osAtributosSaoDe1a10() {
        String rui = registar(utilizadores, "rui");
        DadosForm f = dados("Rui");
        f.setDefesa(11);

        assertThatThrownBy(() -> jogadores.atualizar(jogadores.idDe(rui), f, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void todosVeemACartaDosOutrosMasSoOProprioEOAdminEditam() {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");
        Long idRui = jogadores.idDe(rui);

        CartaView vistaPeloNuno = jogadores.carta(idRui, nuno);
        assertThat(vistaPeloNuno.podeEditar()).isFalse();
        assertThat(vistaPeloNuno.eu()).isFalse();
        assertThatThrownBy(() -> jogadores.atualizar(idRui, dados("Outro"), nuno)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> jogadores.formulario(idRui, nuno)).isInstanceOf(OperacaoInvalidaException.class);

        assertThat(jogadores.carta(idRui, ADMIN).podeEditar()).isTrue();
        jogadores.atualizar(idRui, dados("Rui corrigido"), ADMIN);
        assertThat(jogadores.carta(idRui, nuno).nome()).isEqualTo("Rui corrigido");
    }

    @Test
    void osBloqueadosNaoAparecemNaListaDeJogadores() {
        String rui = registar(utilizadores, "rui");
        utilizadores.bloquear(jogadores.idDe(rui), ADMIN);

        assertThat(jogadores.jogadores(ADMIN)).extracting(JogadorResumo::nome).doesNotContain("Nome rui");
        assertThat(jogadores.carta(jogadores.idDe(rui), ADMIN).bloqueado()).isTrue();
    }

    // 19/11/2026: cartas do ecrã Estatísticas do rating maior para o menor, sem rating no fim
    @Test
    void asCartasVemDoRatingMaiorParaOMenorESemRatingNoFim() {
        String ana = registar(utilizadores, "ana");
        String bia = registar(utilizadores, "bia");
        String rui = registar(utilizadores, "rui");
        String zeca = registar(utilizadores, "zeca");
        DadosForm fraco = dados("Ana");
        fraco.setVelocidade(4);
        jogadores.atualizar(jogadores.idDe(ana), fraco, ana);
        DadosForm forte = dados("Zeca");
        forte.setVelocidade(9);
        forte.setTecnica(9);
        jogadores.atualizar(jogadores.idDe(zeca), forte, zeca);
        DadosForm medio = dados("Rui");
        medio.setRemate(7);
        jogadores.atualizar(jogadores.idDe(rui), medio, rui);
        DadosForm medio2 = dados("Bia");
        medio2.setPasse(7);
        jogadores.atualizar(jogadores.idDe(bia), medio2, bia);

        assertThat(jogadores.jogadores(ADMIN))
            .filteredOn(j -> j.rating() != null)
            .extracting(JogadorResumo::nomeVisivel).containsExactly("Zeca", "Bia", "Rui", "Ana");
        var todos = jogadores.jogadores(ADMIN);
        assertThat(todos.subList(0, 4)).extracting(JogadorResumo::nomeVisivel).containsExactly("Zeca", "Bia", "Rui", "Ana");
        assertThat(todos.subList(4, todos.size())).allSatisfy(j -> assertThat(j.rating()).isNull());
    }
}
