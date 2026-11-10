package pt.futapp.votacao;

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
import pt.futapp.utilizador.NivelAcesso;
import pt.futapp.utilizador.UtilizadorService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PremiosServiceTest {

    @Autowired private PremiosService premios;
    @Autowired private UtilizadorService utilizadores;

    @Test
    void osDezPremiosDeOrigemEstaoAtivos() {
        assertThat(premios.ativos()).extracting(Premio::getNome).containsExactly(
            "Frango da noite", "Pior falhanço", "Muralha", "Assistência de ouro", "Golo da noite",
            "Mais queixinhas", "Fominha", "Pulmão", "Fantasma", "Ator de Hollywood");
    }

    @Test
    void oOrganizadorCriaEditaEDesativa() {
        String rui = registar(utilizadores, "rui");
        utilizadores.definirNivel(utilizadores.vista(rui).orElseThrow().id(), NivelAcesso.ORGANIZADOR, ADMIN);

        Premio novo = premios.criar("Bota de ouro", "Quem marcou mais", rui);
        assertThat(premios.ativos()).extracting(Premio::getNome).contains("Bota de ouro");

        premios.atualizar(novo.getId(), "Bota de diamante", null, rui);
        premios.definirAtivo(novo.getId(), false, rui);
        assertThat(premios.ativos()).extracting(Premio::getNome).doesNotContain("Bota de diamante");
        assertThat(premios.todos()).extracting(Premio::getNome).contains("Bota de diamante");
    }

    @Test
    void naoHaDoisPremiosComOMesmoNome() {
        assertThatThrownBy(() -> premios.criar("muralha", null, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> premios.criar("  ", null, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void umJogadorNaoGereOsPremios() {
        String rui = registar(utilizadores, "rui");

        assertThatThrownBy(() -> premios.criar("Novo", null, rui)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> premios.definirAtivo(premios.todos().get(0).getId(), false, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
    }
}
