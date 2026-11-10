package pt.futapp.utilizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.ConviteService.EstadoConvite;

/** Testes unitários do token de convite (sem Spring). */
class ConviteServiceTest {

    private static final ZoneId ZONA = ZoneId.of("Europe/Lisbon");
    private static final Instant AGORA = Instant.parse("2026-11-10T20:00:00Z");

    private static ConviteService em(Instant instante) {
        return new ConviteService("segredo", 7, Clock.fixed(instante, ZONA));
    }

    @Test
    void umConviteGeradoEValidoEDizQuemOGerou() {
        ConviteService.Convite convite = em(AGORA).gerar(7, "admin");

        ConviteService.Validacao v = em(AGORA.plus(Duration.ofDays(6))).validar(convite.token());
        assertThat(v.estado()).isEqualTo(EstadoConvite.VALIDO);
        assertThat(v.criadoPor()).isEqualTo("admin");
        assertThat(v.expiraEm()).isEqualTo(convite.expiraEm());
    }

    @Test
    void oMesmoLinkPodeSerUsadoVariasVezes() {
        ConviteService servico = em(AGORA);
        String token = servico.gerar(3, "admin").token();

        assertThat(servico.validar(token).valido()).isTrue();
        assertThat(servico.validar(token).valido()).isTrue();
    }

    @Test
    void depoisDaValidadeOConviteExpira() {
        String token = em(AGORA).gerar(2, "admin").token();

        assertThat(em(AGORA.plus(Duration.ofDays(2))).validar(token).estado()).isEqualTo(EstadoConvite.EXPIRADO);
        assertThat(em(AGORA.plus(Duration.ofDays(2)).minusSeconds(1)).validar(token).estado()).isEqualTo(EstadoConvite.VALIDO);
    }

    @Test
    void naoSeGeramConvitesComMaisDiasQueOMaximo() {
        assertThatThrownBy(() -> em(AGORA).gerar(8, "admin")).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> em(AGORA).gerar(0, "admin")).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void oMaximoEConfiguravel() {
        ConviteService curto = new ConviteService("segredo", 3, Clock.fixed(AGORA, ZONA));

        assertThat(curto.getValidadeMaxDias()).isEqualTo(3);
        assertThat(curto.gerar(3, "admin").token()).isNotBlank();
        assertThatThrownBy(() -> curto.gerar(4, "admin")).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void umTokenAlteradoOuDeOutraChaveEInvalido() {
        String token = em(AGORA).gerar(7, "admin").token();
        char ultimo = token.charAt(token.length() - 1);
        String alterado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');
        ConviteService outraChave = new ConviteService("outro-segredo", 7, Clock.fixed(AGORA, ZONA));

        assertThat(em(AGORA).validar(alterado).estado()).isEqualTo(EstadoConvite.INVALIDO);
        assertThat(outraChave.validar(token).estado()).isEqualTo(EstadoConvite.INVALIDO);
        assertThat(em(AGORA).validar("isto-nao-e-um-token").estado()).isEqualTo(EstadoConvite.INVALIDO);
        assertThat(em(AGORA).validar(null).estado()).isEqualTo(EstadoConvite.AUSENTE);
    }

    @Test
    void oTokenNaoMostraADataNemOAdminEmTextoSimples() {
        String token = em(AGORA).gerar(7, "admin").token();

        assertThat(token).matches("[A-Za-z0-9_-]+");
        String bytes = new String(java.util.Base64.getUrlDecoder().decode(token), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain("admin").doesNotContain("v1|");
    }
}
