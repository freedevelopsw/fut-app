package pt.futapp.utilizador;

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
import pt.futapp.avaliacao.AvaliacaoService;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.estatisticas.EstatisticasService;

/** Carta de cada jogador (visível a todos) e edição dos dados (o próprio ou o Admin). */
@Controller
public class JogadoresController {

    private final JogadorService jogadores;
    private final UtilizadorService utilizadores;
    private final EstatisticasService estatisticas;
    private final AvaliacaoService avaliacoes;

    public JogadoresController(JogadorService jogadores, UtilizadorService utilizadores, EstatisticasService estatisticas,
                               AvaliacaoService avaliacoes) {
        this.jogadores = jogadores;
        this.utilizadores = utilizadores;
        this.estatisticas = estatisticas;
        this.avaliacoes = avaliacoes;
    }

    /** {@code vista}: separador da carta — eu (autoavaliação, omissão) ou colegas. */
    @GetMapping("/jogadores/{id}")
    public String carta(@PathVariable Long id, @RequestParam(required = false) String vista, Principal principal,
                        Model model) {
        CartaView carta = jogadores.carta(id, principal.getName());
        model.addAttribute("carta", carta);
        model.addAttribute("avaliacoes", avaliacoes.daCarta(id, principal.getName()));
        model.addAttribute("vista", "colegas".equals(vista) ? "colegas" : "eu");
        model.addAttribute("numeros", estatisticas.doJogador(id, principal.getName()));
        boolean admin = utilizadores.vista(principal.getName()).map(EuView::admin).orElse(false);
        model.addAttribute("podeGerarLinkPassword", admin && !carta.eu() && !carta.bloqueado());
        model.addAttribute("nav", carta.eu() ? "perfil" : "estatisticas");
        return "jogadores/detalhe";
    }

    @GetMapping("/perfil/dados")
    public String meusDados(Principal principal) {
        return "redirect:/jogadores/" + jogadores.idDe(principal.getName()) + "/editar";
    }

    @GetMapping("/jogadores/{id}/editar")
    public String editar(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("form", jogadores.formulario(id, principal.getName()));
        prepararEdicao(id, principal, model);
        return "jogadores/editar";
    }

    @PostMapping("/jogadores/{id}/editar")
    public String guardar(@PathVariable Long id, @Valid @ModelAttribute("form") DadosForm form, BindingResult erros,
                          Principal principal, Model model, RedirectAttributes redirect) {
        if (!erros.hasFieldErrors("posicaoSecundaria") && form.getPosicao() != null
                && form.getPosicao() == form.getPosicaoSecundaria()) {
            erros.rejectValue("posicaoSecundaria", "igual", "Tem de ser diferente da posição principal.");
        }
        if (erros.hasErrors()) {
            prepararEdicao(id, principal, model);
            return "jogadores/editar";
        }
        try {
            jogadores.atualizar(id, form, principal.getName());
        } catch (OperacaoInvalidaException e) {
            model.addAttribute("erro", e.getMessage());
            prepararEdicao(id, principal, model);
            return "jogadores/editar";
        }
        redirect.addFlashAttribute("sucesso", "Dados guardados.");
        return "redirect:/jogadores/" + id;
    }

    /** Muda o nível de acesso a partir do dropdown da carta. As regras estão no serviço. */
    @PostMapping("/jogadores/{id}/nivel")
    public String definirNivel(@PathVariable Long id, @RequestParam NivelAcesso nivel,
                               Principal principal, RedirectAttributes redirect) {
        try {
            utilizadores.definirNivel(id, nivel, principal.getName());
            redirect.addFlashAttribute("sucesso", "Nível de acesso atualizado.");
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogadores/" + id;
    }

    private void prepararEdicao(Long id, Principal principal, Model model) {
        CartaView carta = jogadores.carta(id, principal.getName());
        model.addAttribute("carta", carta);
        model.addAttribute("posicoes", Posicao.values());
        model.addAttribute("pes", Pe.values());
        model.addAttribute("nav", carta.eu() ? "perfil" : "estatisticas");
    }
}
