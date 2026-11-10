package pt.futapp.audit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditoriaController {

    private final AuditService audit;

    public AuditoriaController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/admin/auditoria")
    public String listar(@RequestParam(name = "jogo", required = false) Long jogoId,
                         @RequestParam(name = "ator", required = false) String ator,
                         Model model) {
        model.addAttribute("eventos", audit.listar(jogoId, ator));
        model.addAttribute("filtroJogo", jogoId);
        model.addAttribute("filtroAtor", ator);
        model.addAttribute("nav", "perfil");
        return "admin/auditoria";
    }
}
