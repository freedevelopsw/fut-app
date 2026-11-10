package pt.futapp.utilizador;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.config.Sessoes;

/** Ações do próprio utilizador sobre a sua conta. */
@Controller
public class PerfilController {

    private final UtilizadorService utilizadores;

    public PerfilController(UtilizadorService utilizadores) {
        this.utilizadores = utilizadores;
    }

    @GetMapping("/perfil/password")
    public String password(Model model) {
        model.addAttribute("form", new AlterarPasswordForm());
        model.addAttribute("nav", "perfil");
        return "perfil-password";
    }

    /**
     * Depois de mudar a password, a sessão neste dispositivo termina e a pessoa entra com a nova password:
     * assim a sessão persistente fica de novo válida, e as dos outros dispositivos deixam de valer.
     */
    @PostMapping("/perfil/password")
    public String alterar(@Valid @ModelAttribute("form") AlterarPasswordForm form, BindingResult erros,
                          Principal principal, Model model,
                          HttpServletRequest pedido, HttpServletResponse resposta) {
        model.addAttribute("nav", "perfil");
        if (!erros.hasFieldErrors("nova") && !erros.hasFieldErrors("confirmacao")
                && !form.getNova().equals(form.getConfirmacao())) {
            erros.rejectValue("confirmacao", "diferente", "As duas passwords novas não são iguais.");
        }
        if (erros.hasErrors()) {
            return "perfil-password";
        }
        try {
            utilizadores.alterarPassword(principal.getName(), form.getAtual(), form.getNova());
        } catch (PasswordErradaException e) {
            erros.rejectValue("atual", "errada", e.getMessage());
            return "perfil-password";
        } catch (OperacaoInvalidaException e) {
            erros.rejectValue("nova", "invalida", e.getMessage());
            return "perfil-password";
        }
        Sessoes.terminar(pedido, resposta);
        return "redirect:/login?passwordAlterada";
    }
}
