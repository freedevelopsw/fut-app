package pt.futapp.utilizador;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.stream.IntStream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.web.Formatos;

/** Gestão de membros pelo Admin: bloqueio e links de convite (a única forma de registo). As roles mudam-se na carta do jogador. */
@Controller
public class MembrosController {

    private final UtilizadorService utilizadores;
    private final Formatos fmt;

    public MembrosController(UtilizadorService utilizadores, Formatos fmt) {
        this.utilizadores = utilizadores;
        this.fmt = fmt;
    }

    @GetMapping("/admin/membros")
    public String listar(Principal principal, Model model) {
        var membros = utilizadores.membros(principal.getName());
        model.addAttribute("ativos", membros.stream().filter(m -> !m.bloqueado()).toList());
        model.addAttribute("bloqueados", membros.stream().filter(MembroView::bloqueado).toList());
        model.addAttribute("nav", "perfil");
        return "admin/membros";
    }

    // ---------------------------------------------------------------- bloquear / desbloquear

    @PostMapping("/admin/membros/{id}/bloqueio")
    public String bloqueio(@PathVariable Long id, @RequestParam boolean bloquear,
                           Principal principal, RedirectAttributes redirect) {
        try {
            if (bloquear) {
                utilizadores.bloquear(id, principal.getName());
                redirect.addFlashAttribute("sucesso",
                    "Membro bloqueado. Já não consegue entrar, mas o histórico mantém-se.");
            } else {
                utilizadores.desbloquear(id, principal.getName());
                redirect.addFlashAttribute("sucesso", "Membro desbloqueado. Já pode voltar a entrar.");
            }
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/membros";
    }

    // ---------------------------------------------------------------- links de convite

    @GetMapping("/admin/convites")
    public String convites(Model model) {
        prepararConvites(model, utilizadores.validadeMaxConviteDias());
        return "admin/convites";
    }

    /** Gera o link e mostra-o logo. Não fica guardado: a página é a única cópia. */
    @PostMapping("/admin/convites")
    public String gerarConvite(@RequestParam int dias, Principal principal, HttpServletRequest pedido, Model model) {
        prepararConvites(model, dias);
        try {
            ConviteService.Convite convite = utilizadores.gerarConvite(dias, principal.getName());
            String urlBase = ServletUriComponentsBuilder.fromContextPath(pedido).build().toUriString();
            String link = urlBase + "/registo?convite=" + convite.token();
            String validoAte = fmt.dia(convite.expiraEm()) + ", " + fmt.hora(convite.expiraEm());
            String mensagem = "Junta-te ao nosso futebol na FutApp! Cria a tua conta neste link (válido até "
                + validoAte + "):\n" + link;
            model.addAttribute("link", link);
            model.addAttribute("validoAte", validoAte);
            model.addAttribute("whatsappUrl", "https://wa.me/?text="
                + URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20"));
        } catch (OperacaoInvalidaException e) {
            model.addAttribute("erro", e.getMessage());
        }
        return "admin/convites";
    }

    private void prepararConvites(Model model, int diasEscolhidos) {
        int max = utilizadores.validadeMaxConviteDias();
        model.addAttribute("opcoesDias", IntStream.rangeClosed(1, max).boxed().toList());
        model.addAttribute("diasEscolhidos", Math.min(Math.max(diasEscolhidos, 1), max));
        model.addAttribute("nav", "perfil");
    }
}
