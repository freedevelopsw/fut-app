package pt.futapp.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditCleanupJobTest {

    @Autowired
    private AuditEventoRepository repo;

    @Autowired
    private AuditCleanupJob job;

    @Test
    void apagaSoOsEventosMaisAntigosQueARetencao() {
        repo.deleteAll();
        repo.save(new AuditEvento(LocalDateTime.now().minusDays(91),
            new AuditEvent("rui", Acao.LOGIN, null, "UTILIZADOR", null, "antigo")));
        repo.save(new AuditEvento(LocalDateTime.now().minusDays(10),
            new AuditEvent("rui", Acao.LOGIN, null, "UTILIZADOR", null, "recente")));

        int apagados = job.limpar();

        assertThat(apagados).isEqualTo(1);
        assertThat(repo.findAll()).extracting(AuditEvento::getDetalhes).containsExactly("recente");
    }
}
