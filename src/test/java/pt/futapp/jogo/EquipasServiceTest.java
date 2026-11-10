package pt.futapp.jogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
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
class EquipasServiceTest {

    private static final String URL = "http://localhost:8080";

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;
    @Autowired private EquipasService equipas;

    private Long jogo;

    @BeforeEach
    void preparar() {
        jogo = criarJogo(jogos, ADMIN);
    }

    private void inscrever(int quantos) {
        for (int i = 1; i <= quantos; i++) {
            jogos.inscrever(jogo, registar(utilizadores, "j" + i));
        }
    }

    private EquipasView vista(String quem) {
        return equipas.vista(jogo, quem, URL);
    }

    @Test
    void geraDuasEquipasComOsInscritosSemAListaDeEspera() {
        inscrever(13);

        equipas.gerar(jogo, ADMIN);

        EquipasView v = vista(ADMIN);
        assertThat(v.coletes().tamanho()).isEqualTo(6);
        assertThat(v.semColetes().tamanho()).isEqualTo(6);
        assertThat(v.porAtribuir()).isEmpty();
        assertThat(v.publicadas()).isFalse();
    }

    @Test
    void soOOrganizadorOuOAdminFazemEquipas() {
        inscrever(4);

        assertThatThrownBy(() -> equipas.gerar(jogo, "j1")).isInstanceOf(OperacaoInvalidaException.class);
        assertThat(vista("j1").podeGerir()).isFalse();
    }

    @Test
    void precisaDePeloMenos2Inscritos() {
        inscrever(1);

        assertThatThrownBy(() -> equipas.gerar(jogo, ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void soSePublicaDepoisDeFecharAConvocatoria() {
        inscrever(4);
        equipas.gerar(jogo, ADMIN);

        assertThat(vista(ADMIN).podePublicar()).isFalse();
        assertThatThrownBy(() -> equipas.publicar(jogo, ADMIN, URL))
            .isInstanceOf(OperacaoInvalidaException.class)
            .hasMessageContaining("Fecha primeiro");

        jogos.fecharConvocatoria(jogo, ADMIN);
        equipas.publicar(jogo, ADMIN, URL);

        EquipasView v = vista("j1");
        assertThat(v.publicadas()).isTrue();
        assertThat(v.estado()).isEqualTo(EstadoJogo.EQUIPAS_PUBLICADAS);
    }

    @Test
    void moverPassaOJogadorParaAOutraEquipa() {
        inscrever(4);
        equipas.gerar(jogo, ADMIN);
        EquipasView.JogadorEquipa primeiro = vista(ADMIN).coletes().jogadores().get(0);

        equipas.mover(jogo, primeiro.inscricaoId(), null, ADMIN);

        EquipasView v = vista(ADMIN);
        assertThat(v.semColetes().jogadores()).extracting(EquipasView.JogadorEquipa::inscricaoId).contains(primeiro.inscricaoId());
        assertThat(v.coletes().tamanho()).isEqualTo(1);
    }

    @Test
    void quemSobeDaListaDeEsperaFicaSemEquipaENaoSePublicaAssim() {
        inscrever(13);
        equipas.gerar(jogo, ADMIN);
        jogos.fecharConvocatoria(jogo, ADMIN);
        Long inscricaoJ1 = jogos.convocatoria(jogo, ADMIN, URL).inscritos().get(0).inscricaoId();

        jogos.removerInscricao(jogo, inscricaoJ1, ADMIN);

        EquipasView v = vista(ADMIN);
        assertThat(v.porAtribuir()).extracting(EquipasView.JogadorEquipa::nome).containsExactly("Nome j13");
        assertThat(v.desatualizadas()).isTrue();
        assertThat(v.podePublicar()).isFalse();

        equipas.mover(jogo, v.porAtribuir().get(0).inscricaoId(), Equipa.COLETES, ADMIN);
        assertThat(vista(ADMIN).podePublicar()).isTrue();
    }

    @Test
    void asEquipasPublicadasVaoParaOWhatsApp() {
        inscrever(4);
        equipas.gerar(jogo, ADMIN);

        String url = vista(ADMIN).whatsappUrl();
        String texto = URLDecoder.decode(url.substring("https://wa.me/?text=".length()), StandardCharsets.UTF_8);
        assertThat(texto).contains("Equipas").contains("Coletes (2)").contains("Sem coletes (2)").contains("- Nome j");
    }

    @Test
    void depoisDePublicadasAindaSeAjustam() {
        inscrever(4);
        equipas.gerar(jogo, ADMIN);
        jogos.fecharConvocatoria(jogo, ADMIN);
        equipas.publicar(jogo, ADMIN, URL);

        equipas.gerar(jogo, ADMIN);
        equipas.mover(jogo, vista(ADMIN).coletes().jogadores().get(0).inscricaoId(), null, ADMIN);

        assertThat(vista(ADMIN).publicadas()).isTrue();
    }
}
