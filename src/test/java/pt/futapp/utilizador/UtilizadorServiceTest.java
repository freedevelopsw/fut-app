package pt.futapp.utilizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.convites;
import static pt.futapp.TesteApoio.form;
import static pt.futapp.TesteApoio.registar;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.comum.OperacaoInvalidaException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UtilizadorServiceTest {

    @Autowired
    private UtilizadorService utilizadores;

    @Autowired
    private UtilizadorRepository repo;

    @Autowired
    private AdminInicial adminInicial;

    private Long idDe(String username) {
        return utilizadores.vista(username).orElseThrow().id();
    }

    // ---------------------------------------------------------------- admin no arranque

    @Test
    void oAdminECriadoNoArranqueComTodasAsRoles() {
        EuView admin = utilizadores.vista(ADMIN).orElseThrow();

        assertThat(admin.admin()).isTrue();
        assertThat(admin.gestor()).isTrue();
        assertThat(admin.nome()).isEqualTo("Administrador");
    }

    @Test
    void arrancarOutraVezNaoDuplicaOAdminNemMudaAPassword() throws Exception {
        String hashAntes = repo.findByUsernameIgnoreCase(ADMIN).orElseThrow().getPasswordHash();

        adminInicial.run(null);
        boolean criou = utilizadores.garantirAdmin("outro", "Outro", "x");

        assertThat(criou).isFalse();
        assertThat(utilizadores.vista("outro")).isEmpty();
        assertThat(repo.findByUsernameIgnoreCase(ADMIN).orElseThrow().getPasswordHash()).isEqualTo(hashAntes);
    }

    // ---------------------------------------------------------------- registo por convite

    @Test
    void oRegistoPrecisaSempreDeUmConviteValido() {
        assertThatThrownBy(() -> utilizadores.registar(form("rui"), null))
            .isInstanceOf(ConviteInvalidoException.class);
        assertThatThrownBy(() -> utilizadores.registar(form("rui"), "token-inventado"))
            .isInstanceOf(ConviteInvalidoException.class);
        assertThat(utilizadores.vista("rui")).isEmpty();
    }

    @Test
    void umConviteExpiradoNaoServeParaRegistar() {
        Clock haOitoDias = Clock.offset(Clock.systemUTC(), Duration.ofDays(-8));
        String expirado = convites(haOitoDias).gerar(7, ADMIN).token();

        assertThatThrownBy(() -> utilizadores.registar(form("rui"), expirado))
            .isInstanceOf(ConviteInvalidoException.class)
            .hasMessageContaining("expirou");
    }

    @Test
    void comConviteValidoVariosSeRegistamEFicamSoJogadores() {
        String token = utilizadores.gerarConvite(7, ADMIN).token();

        utilizadores.registar(form("rui"), token);
        utilizadores.registar(form("nuno"), token);

        assertThat(utilizadores.vista("rui").orElseThrow().gestor()).isFalse();
        assertThat(utilizadores.loadUserByUsername("nuno").getAuthorities()).extracting(Object::toString)
            .containsExactly("ROLE_JOGADOR");
    }

    @Test
    void soOAdminGeraConvites() {
        String rui = registar(utilizadores, "rui");

        assertThatThrownBy(() -> utilizadores.gerarConvite(7, rui)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void oUsernameNaoPodeRepetirNemComMaiusculas() {
        registar(utilizadores, "rui");

        assertThatThrownBy(() -> registar(utilizadores, "RUI")).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void aPasswordFicaCifrada() {
        registar(utilizadores, "rui");

        assertThat(utilizadores.loadUserByUsername("rui").getPassword()).isNotEqualTo("segredo123");
    }

    // ---------------------------------------------------------------- password

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void oUtilizadorMudaAPropriaPassword() {
        String rui = registar(utilizadores, "rui");

        utilizadores.alterarPassword(rui, "segredo123", "nova-password");

        String hash = utilizadores.loadUserByUsername(rui).getPassword();
        assertThat(passwordEncoder.matches("nova-password", hash)).isTrue();
        assertThat(passwordEncoder.matches("segredo123", hash)).isFalse();
    }

    @Test
    void semAPasswordAtualCertaNaoMuda() {
        String rui = registar(utilizadores, "rui");

        assertThatThrownBy(() -> utilizadores.alterarPassword(rui, "errada", "nova-password"))
            .isInstanceOf(PasswordErradaException.class);
        assertThat(passwordEncoder.matches("segredo123", utilizadores.loadUserByUsername(rui).getPassword())).isTrue();
    }

    @Test
    void aNovaPasswordTemDeSerDiferenteEComPeloMenos6Caracteres() {
        String rui = registar(utilizadores, "rui");

        assertThatThrownBy(() -> utilizadores.alterarPassword(rui, "segredo123", "segredo123"))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.alterarPassword(rui, "segredo123", "123"))
            .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void oAdminTambemMudaAPassword() {
        utilizadores.alterarPassword(ADMIN, "segredo-admin", "outra-password");

        assertThat(passwordEncoder.matches("outra-password", utilizadores.loadUserByUsername(ADMIN).getPassword())).isTrue();
    }

    // ---------------------------------------------------------------- nível de acesso (roles)

    @Test
    void oAdminMudaQualquerNivelEAsRolesSaoCumulativas() {
        String rui = registar(utilizadores, "rui");

        utilizadores.definirNivel(idDe(rui), NivelAcesso.ADMIN, ADMIN);
        assertThat(utilizadores.loadUserByUsername(rui).getAuthorities()).extracting(Object::toString)
            .containsExactlyInAnyOrder("ROLE_JOGADOR", "ROLE_ORGANIZADOR", "ROLE_ADMIN");

        utilizadores.definirNivel(idDe(rui), NivelAcesso.ORGANIZADOR, ADMIN);
        assertThat(utilizadores.vista(rui).orElseThrow().gestor()).isTrue();
        assertThat(utilizadores.vista(rui).orElseThrow().admin()).isFalse();

        utilizadores.definirNivel(idDe(rui), NivelAcesso.JOGADOR, ADMIN);
        assertThat(utilizadores.vista(rui).orElseThrow().gestor()).isFalse();
    }

    @Test
    void oAdminNaoMudaOProprioNivel() {
        assertThatThrownBy(() -> utilizadores.definirNivel(idDe(ADMIN), NivelAcesso.JOGADOR, ADMIN))
            .isInstanceOf(OperacaoInvalidaException.class)
            .hasMessageContaining("próprio");
        assertThat(utilizadores.vista(ADMIN).orElseThrow().admin()).isTrue();
    }

    @Test
    void oOrganizadorMudaEntreJogadorEOrganizadorMasNaoMexeEmAdmin() {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");
        utilizadores.definirNivel(idDe(rui), NivelAcesso.ORGANIZADOR, ADMIN);

        utilizadores.definirNivel(idDe(nuno), NivelAcesso.ORGANIZADOR, rui);
        assertThat(utilizadores.vista(nuno).orElseThrow().gestor()).isTrue();

        assertThatThrownBy(() -> utilizadores.definirNivel(idDe(nuno), NivelAcesso.ADMIN, rui))
            .isInstanceOf(OperacaoInvalidaException.class)
            .hasMessageContaining("Só o Admin");
        assertThatThrownBy(() -> utilizadores.definirNivel(idDe(ADMIN), NivelAcesso.JOGADOR, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(utilizadores.vista(nuno).orElseThrow().admin()).isFalse();
        assertThat(utilizadores.vista(ADMIN).orElseThrow().admin()).isTrue();
    }

    @Test
    void umJogadorNaoMudaNiveisNemOSeu() {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");

        assertThatThrownBy(() -> utilizadores.definirNivel(idDe(rui), NivelAcesso.ORGANIZADOR, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.definirNivel(idDe(nuno), NivelAcesso.ORGANIZADOR, rui))
            .isInstanceOf(OperacaoInvalidaException.class);
        assertThat(utilizadores.vista(rui).orElseThrow().gestor()).isFalse();
    }

    // ---------------------------------------------------------------- bloqueio

    @Test
    void umMembroBloqueadoFicaSemAcessoEDesbloqueadoVolta() {
        String rui = registar(utilizadores, "rui");

        utilizadores.bloquear(idDe(rui), ADMIN);
        assertThat(utilizadores.loadUserByUsername(rui).isEnabled()).isFalse();
        assertThat(utilizadores.estaBloqueado(rui)).isTrue();
        assertThat(utilizadores.membros(ADMIN)).filteredOn(MembroView::bloqueado)
            .extracting(MembroView::username).containsExactly(rui);

        utilizadores.desbloquear(idDe(rui), ADMIN);
        assertThat(utilizadores.loadUserByUsername(rui).isEnabled()).isTrue();
    }

    @Test
    void oBloqueioMantemOMembroNaBaseDeDados() {
        String rui = registar(utilizadores, "rui");

        utilizadores.bloquear(idDe(rui), ADMIN);

        assertThat(utilizadores.vista(rui)).isPresent();
    }

    @Test
    void soOAdminBloqueiaENinguemSeBloqueiaASiProprio() {
        String rui = registar(utilizadores, "rui");
        String nuno = registar(utilizadores, "nuno");

        assertThatThrownBy(() -> utilizadores.bloquear(idDe(nuno), rui)).isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> utilizadores.bloquear(idDe(ADMIN), ADMIN)).isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void umAdminBloqueadoPerdeOsPoderesDeAdmin() {
        String rui = registar(utilizadores, "rui");
        utilizadores.definirNivel(idDe(rui), NivelAcesso.ADMIN, ADMIN);
        utilizadores.bloquear(idDe(rui), ADMIN);

        assertThatThrownBy(() -> utilizadores.gerarConvite(7, rui)).isInstanceOf(OperacaoInvalidaException.class);
    }
}
