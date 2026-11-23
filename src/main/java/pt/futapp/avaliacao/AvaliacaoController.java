package pt.futapp.avaliacao;

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

import jakarta.validation.Valid;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.JogadorService;

/** Avaliar um colega, ver quem avaliou e como, e o interruptor (temporário) do Admin. As regras estão nos serviços. */
@Controller
public class AvaliacaoController {

    private final AvaliacaoService avaliacoes;
    private final JogadorService jogadores;
    private final InterruptorDetalhesAvaliacoes interruptor;

    public AvaliacaoController(AvaliacaoService avaliacoes, JogadorService jogadores,
                               InterruptorDetalhesAvaliacoes interruptor) {
        this.avaliacoes = avaliacoes;
        this.jogadores = jogadores;
        this.interruptor = interruptor;
    }

    @GetMapping("/jogadores/{id}/avaliar")
    public String avaliar(@PathVariable Long id, Principal principal, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("form", avaliacoes.formulario(id, principal.getName()));
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/jogadores/" + id;
        }
        preparar(id, principal, model);
        return "jogadores/avaliar";
    }

    @PostMapping("/jogadores/{id}/avaliar")
    public String guardar(@PathVariable Long id, @Valid @ModelAttribute("form") AvaliacaoForm form, BindingResult erros,
                          Principal principal, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            preparar(id, principal, model);
            return "jogadores/avaliar";
        }
        try {
            avaliacoes.guardar(id, form, principal.getName());
        } catch (OperacaoInvalidaException e) {
            model.addAttribute("erro", e.getMessage());
            preparar(id, principal, model);
            return "jogadores/avaliar";
        }
        redirect.addFlashAttribute("sucesso", form.notas().vazia() ? "Avaliação apagada." : "Avaliação guardada.");
        return "redirect:/jogadores/" + id + "?vista=colegas";
    }

    @GetMapping("/jogadores/{id}/avaliacoes")
    public String detalhes(@PathVariable Long id, Principal principal, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("detalhes", avaliacoes.detalhes(id, principal.getName()));
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/jogadores/" + id + "?vista=colegas";
        }
        model.addAttribute("nav", "estatisticas");
        return "jogadores/avaliacoes";
    }

    /** TEMPORÁRIO: ver {@link InterruptorDetalhesAvaliacoes}. */
    @PostMapping("/admin/avaliacoes/visibilidade")
    public String visibilidade(@RequestParam boolean ligado, @RequestParam(required = false) String cartas,
                               Principal principal, RedirectAttributes redirect) {
        try {
            interruptor.definir(ligado, principal.getName());
            redirect.addFlashAttribute("sucesso", ligado
                ? "Quem avaliou e como passou a estar visível para todos."
                : "Quem avaliou e como voltou a estar escondido.");
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/estatisticas?cartas=" + OrdemCartas.de(cartas).getParametro();
    }

    private void preparar(Long id, Principal principal, Model model) {
        model.addAttribute("carta", jogadores.carta(id, principal.getName()));
        model.addAttribute("nav", "estatisticas");
    }
}
