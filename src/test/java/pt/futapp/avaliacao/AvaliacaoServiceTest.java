package pt.futapp.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.registar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.DadosForm;
import pt.futapp.utilizador.JogadorService;
import pt.futapp.utilizador.RemocaoService;
import pt.futapp.utilizador.UtilizadorService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AvaliacaoServiceTest {

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogadorService jogadores;
    @Autowired private AvaliacaoService avaliacoes;
    @Autowired private InterruptorDetalhesAvaliacoes interruptor;
    @Autowired private RemocaoService remocao;

    private static AvaliacaoForm notas(Integer velocidade, Integer tecnica, Integer remate, Integer passe, Integer defesa,
                                       Integer resistencia) {
        AvaliacaoForm f = new AvaliacaoForm();
        f.setVelocidade(velocidade);
        f.setTecnica(tecnica);
        f.setRemate(remate);
        f.setPasse(passe);
        f.setDefesa(defesa);
        f.setResistencia(resistencia);
        return f;
    }

    private Long id(String username) {
        return jogadores.idDe(username);
    }

    private void autoavaliar(String username, int valor) {
        DadosForm f = new DadosForm();
        f.setNome("Nome " + username);
        f.setVelocidade(valor);
        jogadores.atualizar(id(username), f, username);
    }

    @Test
    void aMediaDosColegasApareceLogoAoPrimeiroVotoComONumeroDeVotos() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");

        assertThat(avaliacoes.daCarta(id(ana), rui).temVotos()).isFalse();
        assertThat(avaliacoes.daCarta(id(ana), rui).jaVotaramTexto()).isEqualTo("Ainda nenhum colega votou");

        avaliacoes.guardar(id(ana), notas(8, 6, null, null, null, null), rui);

        CartaAvaliacoesView carta = avaliacoes.daCarta(id(ana), rui);
        assertThat(carta.votos()).isEqualTo(1);
        assertThat(carta.votosTexto()).isEqualTo("1 voto");
        assertThat(carta.jaVotaramTexto()).isEqualTo("Já votou 1 colega");
        assertThat(carta.ratingColegasTexto()).isEqualTo("7,0");
        assertThat(carta.atributos().get(0).colegasTexto()).isEqualTo("8,0");
        assertThat(carta.atributos().get(2).colegas()).isNull();
        assertThat(carta.jaAvaliei()).isTrue();
    }

    @Test
    void cadaAtributoEAMediaDosColegasQueLheDeramNota() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");
        String ze = registar(utilizadores, "ze");

        avaliacoes.guardar(id(ana), notas(8, 7, null, null, null, null), rui);
        avaliacoes.guardar(id(ana), notas(7, null, 9, null, null, null), ze);

        MediaColegas m = avaliacoes.medias().get(id(ana));
        assertThat(m.votos()).isEqualTo(2);
        assertThat(m.atributos().get(0)).isEqualTo(7.5);
        assertThat(m.atributos().get(1)).isEqualTo(7.0);
        assertThat(m.atributos().get(2)).isEqualTo(9.0);
        assertThat(m.atributos().get(3)).isNull();
        // rating = média dos atributos com nota: (7,5 + 7 + 9) / 3
        assertThat(m.ratingTexto()).isEqualTo("7,8");
        assertThat(m.votosTexto()).isEqualTo("2 votos");
    }

    @Test
    void voltarAAvaliarSubstituiETudoEmBrancoApaga() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");

        avaliacoes.guardar(id(ana), notas(4, null, null, null, null, null), rui);
        avaliacoes.guardar(id(ana), notas(9, null, null, null, null, null), rui);

        assertThat(avaliacoes.daCarta(id(ana), rui).votos()).isEqualTo(1);
        assertThat(avaliacoes.daCarta(id(ana), rui).ratingColegasTexto()).isEqualTo("9,0");
        assertThat(avaliacoes.formulario(id(ana), rui).getVelocidade()).isEqualTo(9);

        avaliacoes.guardar(id(ana), notas(null, null, null, null, null, null), rui);

        assertThat(avaliacoes.daCarta(id(ana), rui).temVotos()).isFalse();
        assertThat(avaliacoes.formulario(id(ana), rui).getVelocidade()).isNull();
    }

    @Test
    void ninguemSeAvaliaASiProprioNemAvaliaUmBloqueado() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");

        assertThatThrownBy(() -> avaliacoes.guardar(id(rui), notas(9, 9, 9, 9, 9, 9), rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(avaliacoes.daCarta(id(rui), rui).podeAvaliar()).isFalse();
        assertThat(avaliacoes.daCarta(id(ana), rui).podeAvaliar()).isTrue();

        utilizadores.bloquear(id(ana), ADMIN);

        assertThatThrownBy(() -> avaliacoes.guardar(id(ana), notas(5, null, null, null, null, null), rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(avaliacoes.daCarta(id(ana), rui).podeAvaliar()).isFalse();
    }

    @Test
    void asNotasSaoDe1A10() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");

        assertThatThrownBy(() -> avaliacoes.guardar(id(ana), notas(11, null, null, null, null, null), rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> avaliacoes.guardar(id(ana), notas(null, 0, null, null, null, null), rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(avaliacoes.daCarta(id(ana), rui).temVotos()).isFalse();
    }

    @Test
    void quemAvaliouEComoOAdminVeSempreOsOutrosSoComOInterruptorLigado() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");
        avaliacoes.guardar(id(ana), notas(8, null, null, null, null, 6), rui);

        assertThat(interruptor.ligado()).isFalse();
        assertThat(avaliacoes.daCarta(id(ana), ana).podeVerDetalhes()).isFalse();
        assertThatThrownBy(() -> avaliacoes.detalhes(id(ana), ana)).isInstanceOf(OperacaoInvalidaException.class);

        DetalhesAvaliacoesView paraAdmin = avaliacoes.detalhes(id(ana), ADMIN);
        assertThat(paraAdmin.linhas()).hasSize(1);
        assertThat(paraAdmin.linhas().get(0).avaliador()).isEqualTo("Nome rui");
        assertThat(paraAdmin.linhas().get(0).notas()).containsExactly("8", "–", "–", "–", "–", "6");
        assertThat(paraAdmin.medias()).containsExactly("8,0", "–", "–", "–", "–", "6,0");

        interruptor.definir(true, ADMIN);

        assertThat(interruptor.ligado()).isTrue();
        assertThat(avaliacoes.daCarta(id(ana), ana).podeVerDetalhes()).isTrue();
        assertThat(avaliacoes.detalhes(id(ana), ana).linhas()).hasSize(1);

        interruptor.definir(false, ADMIN);
        assertThat(avaliacoes.daCarta(id(ana), ana).podeVerDetalhes()).isFalse();
    }

    @Test
    void soOAdminMexeNoInterruptor() {
        String rui = registar(utilizadores, "rui");

        assertThatThrownBy(() -> interruptor.definir(true, rui)).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(interruptor.ligado()).isFalse();
    }

    @Test
    void asCartasOrdenamPelaAutoavaliacaoOuPelaMediaDosColegas() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");
        String bia = registar(utilizadores, "bia");
        autoavaliar(rui, 9);
        autoavaliar(ana, 5);
        autoavaliar(bia, 7);
        avaliacoes.guardar(id(rui), notas(4, null, null, null, null, null), ana);
        avaliacoes.guardar(id(ana), notas(9, null, null, null, null, null), rui);

        assertThat(avaliacoes.cartas(ADMIN, OrdemCartas.AUTO)).extracting(CartaResumo::nomeVisivel)
            .startsWith("Nome rui", "Nome bia", "Nome ana");
        // pelos colegas: quem tem média primeiro (9,0 e 4,0); quem não tem fica depois, pela autoavaliação
        assertThat(avaliacoes.cartas(ADMIN, OrdemCartas.COLEGAS)).extracting(CartaResumo::nomeVisivel)
            .startsWith("Nome ana", "Nome rui", "Nome bia");
        CartaResumo ruiNaLista = avaliacoes.cartas(rui, OrdemCartas.AUTO).stream()
            .filter(c -> c.nomeVisivel().equals("Nome rui")).findFirst().orElseThrow();
        assertThat(ruiNaLista.autoTexto()).isEqualTo("9,0");
        assertThat(ruiNaLista.colegasTexto()).isEqualTo("4,0");
        assertThat(ruiNaLista.votosTexto()).isEqualTo("1 voto");
        assertThat(ruiNaLista.eu()).isTrue();
        assertThat(OrdemCartas.de("colegas")).isEqualTo(OrdemCartas.COLEGAS);
        assertThat(OrdemCartas.de("xpto")).isEqualTo(OrdemCartas.AUTO);
    }

    @Test
    void removerUmMembroApagaAsAvaliacoesQueDeuERecebeu() {
        String rui = registar(utilizadores, "rui");
        String ana = registar(utilizadores, "ana");
        avaliacoes.guardar(id(ana), notas(8, null, null, null, null, null), rui);
        avaliacoes.guardar(id(rui), notas(6, null, null, null, null, null), ana);
        Long idRui = id(rui);

        // o Rui só avaliou: não tem histórico de jogos, por isso a conta é apagada de vez
        assertThat(remocao.remover(idRui, ADMIN)).isEqualTo(RemocaoService.Resultado.APAGADO);

        assertThat(avaliacoes.daCarta(id(ana), ADMIN).temVotos()).isFalse();
        assertThat(avaliacoes.medias()).doesNotContainKey(idRui);
    }
}
