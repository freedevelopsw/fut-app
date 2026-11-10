package pt.futapp.audit;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.web.Formatos;

@Service
public class AuditService {

    private final AuditEventoRepository repo;
    private final Clock clock;
    private final Formatos fmt;

    public AuditService(AuditEventoRepository repo, Clock clock, Formatos fmt) {
        this.repo = repo;
        this.clock = clock;
        this.fmt = fmt;
    }

    /** Grava o evento numa transação própria, independente da que originou a ação. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void gravar(AuditEvent evento) {
        repo.save(new AuditEvento(LocalDateTime.now(clock), evento));
    }

    @Transactional(readOnly = true)
    public List<AuditView> listar(Long jogoId, String ator) {
        List<AuditEvento> eventos;
        if (jogoId != null) {
            eventos = repo.findTop200ByJogoIdOrderByDataHoraDescIdDesc(jogoId);
        } else if (ator != null && !ator.isBlank()) {
            eventos = repo.findTop200ByAtorIgnoreCaseOrderByDataHoraDescIdDesc(ator.trim());
        } else {
            eventos = repo.findTop200ByOrderByDataHoraDescIdDesc();
        }
        return eventos.stream()
            .map(e -> new AuditView(fmt.dataHora(e.getDataHora()), e.getAtor(), e.getAcao().name(), e.getJogoId(), e.getDetalhes()))
            .toList();
    }

    public record AuditView(String quando, String ator, String acao, Long jogoId, String detalhes) {
    }
}
