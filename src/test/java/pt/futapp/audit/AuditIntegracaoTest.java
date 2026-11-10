package pt.futapp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static pt.futapp.TesteApoio.ADMIN;
import static pt.futapp.TesteApoio.criarJogo;
import static pt.futapp.TesteApoio.registar;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import pt.futapp.jogo.InscricaoRepository;
import pt.futapp.jogo.JogoRepository;
import pt.futapp.jogo.JogoService;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.utilizador.UtilizadorService;

/**
 * Sem @Transactional de propósito: a auditoria só é gravada depois do commit, por isso este teste
 * confirma as transações a sério e limpa a base de dados no fim.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditIntegracaoTest {

    @Autowired private UtilizadorService utilizadores;
    @Autowired private JogoService jogos;
    @Autowired private AuditEventoRepository audit;
    @Autowired private InscricaoRepository inscricoes;
    @Autowired private JogoRepository jogoRepo;
    @Autowired private UtilizadorRepository utilizadorRepo;

    @BeforeEach
    @AfterEach
    void limpar() {
        audit.deleteAll();
        inscricoes.deleteAll();
        jogoRepo.deleteAll();
        // O admin criado no arranque fica, porque os outros testes contam com ele
        utilizadorRepo.deleteAll(utilizadorRepo.findAll().stream().filter(u -> !u.getUsername().equals(ADMIN)).toList());
    }

    @Test
    void asAcoesFicamNaAuditoriaDepoisDoCommit() {
        Long jogo = criarJogo(jogos, ADMIN);
        String j1 = registar(utilizadores, "j1");
        jogos.inscrever(jogo, j1);
        jogos.cancelarPropria(jogo, j1);

        assertThat(audit.findAll()).extracting(AuditEvento::getAcao)
            .containsExactlyInAnyOrder(Acao.REGISTO_UTILIZADOR, Acao.CRIACAO_JOGO,
                Acao.INSCRICAO_JOGO, Acao.CANCELAMENTO_INSCRICAO);
        assertThat(audit.findTop200ByJogoIdOrderByDataHoraDescIdDesc(jogo)).hasSize(3);
        assertThat(audit.findTop200ByAtorIgnoreCaseOrderByDataHoraDescIdDesc("j1"))
            .extracting(AuditEvento::getAcao)
            .contains(Acao.INSCRICAO_JOGO, Acao.CANCELAMENTO_INSCRICAO);
    }
}
