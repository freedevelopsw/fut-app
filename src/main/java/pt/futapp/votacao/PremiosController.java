package pt.futapp.votacao;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pt.futapp.comum.OperacaoInvalidaException;

/** Gestão dos prémios brincadeira (Organizador e Admin). */
@Controller
public class PremiosController {

    private final PremiosService premios;

    public PremiosController(PremiosService premios) {
        this.premios = premios;
    }

    @GetMapping("/gestao/premios")
    public String listar(@RequestParam(required = false) Long editar, Model model) {
        model.addAttribute("premios", premios.todos());
        model.addAttribute("editar", editar);
        model.addAttribute("nav", "perfil");
        return "gestao/premios";
    }

    @PostMapping("/gestao/premios")
    public String criar(@RequestParam String nome, @RequestParam(required = false) String descricao,
                        Principal principal, RedirectAttributes redirect) {
        return executar(redirect, "Prémio criado e ativo.", () -> premios.criar(nome, descricao, principal.getName()));
    }

    @PostMapping("/gestao/premios/{id}")
    public String atualizar(@PathVariable Long id, @RequestParam String nome, @RequestParam(required = false) String descricao,
                            Principal principal, RedirectAttributes redirect) {
        return executar(redirect, "Prémio guardado.", () -> premios.atualizar(id, nome, descricao, principal.getName()));
    }

    @PostMapping("/gestao/premios/{id}/ativo")
    public String ativo(@PathVariable Long id, @RequestParam boolean ativo,
                        Principal principal, RedirectAttributes redirect) {
        return executar(redirect, null, () -> premios.definirAtivo(id, ativo, principal.getName()));
    }

    private String executar(RedirectAttributes redirect, String sucesso, Runnable acao) {
        try {
            acao.run();
            if (sucesso != null) {
                redirect.addFlashAttribute("sucesso", sucesso);
            }
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/gestao/premios";
    }
}
