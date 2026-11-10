package pt.futapp.utilizador;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import pt.futapp.comum.OperacaoInvalidaException;

/**
 * Login e registo. O registo só é possível com um link de convite válido gerado pelo Admin;
 * não há links nem menus para o registo, só o link de convite.
 */
@Controller
public class AuthController {

    private final UtilizadorService utilizadores;
    private final ConviteService convites;

    public AuthController(UtilizadorService utilizadores, ConviteService convites) {
        this.utilizadores = utilizadores;
        this.convites = convites;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/registo")
    public String registo(@RequestParam(name = "convite", required = false) String convite, Model model) {
        if (!prepararRegisto(convite, model)) {
            return "auth/registo";
        }
        model.addAttribute("form", new RegistoForm());
        return "auth/registo";
    }

    @PostMapping("/registo")
    public String registar(@RequestParam(name = "convite", required = false) String convite,
                           @Valid @ModelAttribute("form") RegistoForm form, BindingResult erros, Model model) {
        if (!prepararRegisto(convite, model)) {
            return "auth/registo";
        }
        if (erros.hasErrors()) {
            return "auth/registo";
        }
        try {
            utilizadores.registar(form, convite);
        } catch (ConviteInvalidoException e) {
            model.addAttribute("conviteErro", e.getMessage());
            return "auth/registo";
        } catch (OperacaoInvalidaException e) {
            erros.rejectValue("username", "duplicado", e.getMessage());
            return "auth/registo";
        }
        return "redirect:/login?registado";
    }

    /** Põe no modelo o estado do convite. Devolve false quando o formulário não deve ser mostrado. */
    private boolean prepararRegisto(String convite, Model model) {
        ConviteService.Validacao validacao = convites.validar(convite);
        if (!validacao.valido()) {
            model.addAttribute("conviteErro", validacao.mensagem());
            return false;
        }
        model.addAttribute("convite", convite);
        return true;
    }
}
