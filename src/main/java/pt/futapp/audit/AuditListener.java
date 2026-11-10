package pt.futapp.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Grava os eventos de auditoria só depois de a ação ter sido confirmada na base de dados. */
@Component
public class AuditListener {

    private final AuditService audit;

    public AuditListener(AuditService audit) {
        this.audit = audit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoEvento(AuditEvent evento) {
        audit.gravar(evento);
    }
}
