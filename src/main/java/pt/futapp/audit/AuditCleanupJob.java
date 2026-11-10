package pt.futapp.audit;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga os eventos de auditoria mais antigos que o prazo de retenção.
 * Corre todos os dias e também no arranque, porque em alojamento gratuito a app pode estar parada à hora marcada.
 */
@Component
public class AuditCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanupJob.class);

    private final AuditEventoRepository repo;
    private final Clock clock;
    private final int retencaoDias;

    public AuditCleanupJob(AuditEventoRepository repo, Clock clock,
                           @Value("${futapp.audit.retencao-dias:90}") int retencaoDias) {
        this.repo = repo;
        this.clock = clock;
        this.retencaoDias = retencaoDias;
    }

    @Scheduled(cron = "${futapp.audit.limpeza-cron:0 0 4 * * *}", zone = "Europe/Lisbon")
    public int limpar() {
        LocalDateTime limite = LocalDateTime.now(clock).minusDays(retencaoDias);
        int apagados = repo.apagarAnterioresA(limite);
        if (apagados > 0) {
            log.info("Auditoria: {} eventos anteriores a {} apagados", apagados, limite);
        }
        return apagados;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoArrancar() {
        limpar();
    }
}
