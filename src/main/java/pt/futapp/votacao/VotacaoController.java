package pt.futapp.votacao;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pt.futapp.comum.OperacaoInvalidaException;

@Controller
public class VotacaoController {

    private static final String PREFIXO = "voto_";

    private final VotacaoService votacao;

    public VotacaoController(VotacaoService votacao) {
        this.votacao = votacao;
    }

    @GetMapping("/jogos/{id}/votar")
    public String boletim(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("votacao", votacao.vista(id, principal.getName()));
        model.addAttribute("nav", "jogo");
        return "votacao/votar";
    }

    /** Recebe o boletim: voto_MVP = inscrição escolhida para MVP; voto_{premioId} = escolha em cada prémio (opcional). */
    @PostMapping("/jogos/{id}/votar")
    public String votar(@PathVariable Long id, @RequestParam Map<String, String> parametros,
                        Principal principal, RedirectAttributes redirect) {
        Map<Long, Long> escolhas = new HashMap<>();
        try {
            for (Map.Entry<String, String> p : parametros.entrySet()) {
                if (!p.getKey().startsWith(PREFIXO) || p.getValue() == null || p.getValue().isBlank()) {
                    continue;
                }
                String chave = p.getKey().substring(PREFIXO.length());
                escolhas.put("MVP".equals(chave) ? null : Long.valueOf(chave), Long.valueOf(p.getValue()));
            }
            boolean fechou = votacao.votar(id, escolhas, principal.getName());
            redirect.addFlashAttribute("sucesso", fechou
                ? "Voto registado. Já todos votaram: a votação fechou e os resultados estão revelados."
                : "Voto registado. Podes alterá-lo até a votação fechar.");
            return "redirect:/jogos/" + id;
        } catch (NumberFormatException e) {
            redirect.addFlashAttribute("erro", "Boletim inválido.");
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id + "/votar";
    }

    @PostMapping("/jogos/{id}/gestao/votacao/fechar")
    public String fechar(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Votação fechada. Os resultados estão revelados.",
            () -> votacao.fechar(id, principal.getName()));
    }

    @PostMapping("/jogos/{id}/gestao/votacao/reabrir")
    public String reabrir(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Votação reaberta.", () -> votacao.reabrir(id, principal.getName()));
    }

    // ---------------------------------------------------------------- Admin

    @GetMapping("/jogos/{id}/votos")
    public String votos(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("votacao", votacao.vista(id, principal.getName()));
        model.addAttribute("votos", votacao.votosDoJogo(id, principal.getName()));
        model.addAttribute("nav", "jogo");
        return "votacao/votos";
    }

    @PostMapping("/jogos/{id}/votos/{votoId}/anular")
    public String anular(@PathVariable Long id, @PathVariable Long votoId, Principal principal, RedirectAttributes redirect) {
        try {
            votacao.anular(id, votoId, principal.getName());
            redirect.addFlashAttribute("sucesso", "Voto anulado. Já não conta para os resultados.");
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id + "/votos";
    }

    private String executar(Long id, RedirectAttributes redirect, String sucesso, Runnable acao) {
        try {
            acao.run();
            redirect.addFlashAttribute("sucesso", sucesso);
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id;
    }
}
