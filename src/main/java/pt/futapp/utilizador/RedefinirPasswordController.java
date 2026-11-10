package pt.futapp.utilizador;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.config.Sessoes;
import pt.futapp.web.Formatos;

/**
 * Redefinição de password quando alguém a esquece: o Admin gera um link na carta do jogador e envia-lho;
 * a pessoa abre o link (sem login) e escolhe a nova password.
 */
@Controller
public class RedefinirPasswordController {

    private final UtilizadorService utilizadores;
    private final Formatos fmt;

    public RedefinirPasswordController(UtilizadorService utilizadores, Formatos fmt) {
        this.utilizadores = utilizadores;
        this.fmt = fmt;
    }

    // ---------------------------------------------------------------- Admin: gerar o link

    /** Gera o link e mostra-o logo. Não fica guardado: a página é a única cópia. */
    @PostMapping("/jogadores/{id}/link-password")
    public String gerar(@PathVariable Long id, Principal principal, HttpServletRequest pedido,
                        Model model, RedirectAttributes redirect) {
        UtilizadorService.LinkPassword link;
        try {
            link = utilizadores.gerarLinkPassword(id, principal.getName());
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/jogadores/" + id;
        }
        String urlBase = ServletUriComponentsBuilder.fromContextPath(pedido).build().toUriString();
        String url = urlBase + "/redefinir-password?token=" + link.token();
        String validoAte = fmt.dia(link.expiraEm()) + ", " + fmt.hora(link.expiraEm());
        String mensagem = "Olá " + link.nome() + "! Usa este link para escolheres uma nova password na FutApp."
            + " Só funciona uma vez e é válido até " + validoAte + ":\n" + url;
        model.addAttribute("link", url);
        model.addAttribute("validoAte", validoAte);
        model.addAttribute("nome", link.nome());
        model.addAttribute("jogadorId", id);
        model.addAttribute("whatsappUrl", "https://wa.me/?text="
            + URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20"));
        model.addAttribute("nav", "estatisticas");
        return "jogadores/link-password";
    }

    // ---------------------------------------------------------------- página pública do link

    @GetMapping("/redefinir-password")
    public String pagina(@RequestParam(name = "token", required = false) String token, Model model) {
        if (preparar(token, model)) {
            model.addAttribute("form", new RedefinirPasswordForm());
        }
        return "auth/redefinir-password";
    }

    /**
     * Depois de redefinir, a sessão neste dispositivo termina (se houver alguma) e a pessoa entra com a nova
     * password. As sessões persistentes noutros dispositivos deixam de valer, porque dependem da password.
     */
    @PostMapping("/redefinir-password")
    public String redefinir(@RequestParam(name = "token", required = false) String token,
                            @Valid @ModelAttribute("form") RedefinirPasswordForm form, BindingResult erros,
                            Model model, HttpServletRequest pedido, HttpServletResponse resposta) {
        if (!preparar(token, model)) {
            return "auth/redefinir-password";
        }
        if (!erros.hasFieldErrors("nova") && !erros.hasFieldErrors("confirmacao")
                && !form.getNova().equals(form.getConfirmacao())) {
            erros.rejectValue("confirmacao", "diferente", "As duas passwords não são iguais.");
        }
        if (erros.hasErrors()) {
            return "auth/redefinir-password";
        }
        try {
            utilizadores.redefinirPassword(token, form.getNova());
        } catch (LinkPasswordInvalidoException e) {
            model.addAttribute("linkErro", e.getMessage());
            return "auth/redefinir-password";
        } catch (OperacaoInvalidaException e) {
            erros.rejectValue("nova", "invalida", e.getMessage());
            return "auth/redefinir-password";
        }
        Sessoes.terminar(pedido, resposta);
        return "redirect:/login?passwordRedefinida";
    }

    /** Põe no modelo o estado do link. Devolve false quando o formulário não deve ser mostrado. */
    private boolean preparar(String token, Model model) {
        try {
            UtilizadorService.LinkPassword link = utilizadores.validarLinkPassword(token);
            model.addAttribute("token", token);
            model.addAttribute("nome", link.nome());
            return true;
        } catch (LinkPasswordInvalidoException e) {
            model.addAttribute("linkErro", e.getMessage());
            return false;
        }
    }
}
