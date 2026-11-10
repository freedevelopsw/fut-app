package pt.futapp.utilizador;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.futapp.TesteApoio.CHAVE_CONVITE;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import pt.futapp.config.AppConfig;

class LinkPasswordServiceTest {

    private static final String HASH = "{bcrypt}$2a$10$hash-de-exemplo";

    private final Clock agora = Clock.system(AppConfig.ZONA);
    private final LinkPasswordService links = new LinkPasswordService(CHAVE_CONVITE, 24, agora);

    @Test
    void umLinkAcabadoDeGerarEValidoParaEssaPassword() {
        LinkPasswordService.Link link = links.gerar(7L, HASH, "admin");

        LinkPasswordService.Leitura leitura = links.ler(link.token());

        assertThat(leitura.estado()).isEqualTo(LinkPasswordService.Estado.VALIDO);
        assertThat(leitura.utilizadorId()).isEqualTo(7L);
        assertThat(leitura.criadoPor()).isEqualTo("admin");
        assertThat(leitura.correspondeA(HASH)).isTrue();
        assertThat(link.expiraEm()).isAfter(java.time.LocalDateTime.now(agora).plusHours(23));
    }

    @Test
    void quandoAPasswordMudaOLinkDeixaDeCorresponder() {
        LinkPasswordService.Leitura leitura = links.ler(links.gerar(7L, HASH, "admin").token());

        assertThat(leitura.correspondeA("{bcrypt}$2a$10$outra-password")).isFalse();
    }

    @Test
    void depoisDaValidadeOLinkExpira() {
        LinkPasswordService antigo = new LinkPasswordService(CHAVE_CONVITE, 24, Clock.offset(agora, Duration.ofHours(-25)));
        String token = antigo.gerar(7L, HASH, "admin").token();

        assertThat(links.ler(token).estado()).isEqualTo(LinkPasswordService.Estado.EXPIRADO);
    }

    @Test
    void umTokenAlteradoOuInventadoEInvalido() {
        String token = links.gerar(7L, HASH, "admin").token();
        int meio = token.length() / 2;
        String alterado = token.substring(0, meio) + (token.charAt(meio) == 'A' ? 'B' : 'A') + token.substring(meio + 1);

        assertThat(links.ler(alterado).estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
        assertThat(links.ler("inventado").estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
        assertThat(links.ler("").estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
        assertThat(links.ler(null).estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
    }

    @Test
    void umLinkDeConviteNaoServeParaMudarAPassword() {
        String convite = new ConviteService(CHAVE_CONVITE, 7, agora).gerar(1, "admin").token();

        assertThat(links.ler(convite).estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
    }

    @Test
    void comOutraChaveOLinkEInvalido() {
        String token = new LinkPasswordService("outra-chave", 24, agora).gerar(7L, HASH, "admin").token();

        assertThat(links.ler(token).estado()).isEqualTo(LinkPasswordService.Estado.INVALIDO);
    }

    @Test
    void oConteudoDoLinkNaoSeLe() {
        String token = links.gerar(7L, HASH, "admin").token();
        String bytes = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.ISO_8859_1);

        assertThat(bytes).doesNotContain("admin").doesNotContain("v1|");
    }
}
