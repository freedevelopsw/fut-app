package pt.futapp.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Regista na auditoria cada login, com password ou retomado pela sessão persistente. */
@Component
public class LoginAuditListener {

    private final AuditService audit;

    public LoginAuditListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void aoAutenticar(AuthenticationSuccessEvent evento) {
        Authentication autenticacao = evento.getAuthentication();
        String detalhes = autenticacao instanceof RememberMeAuthenticationToken
            ? "Sessão retomada no dispositivo"
            : "Login com password";
        audit.gravar(new AuditEvent(autenticacao.getName(), Acao.LOGIN, null, "UTILIZADOR", null, detalhes));
    }
}
