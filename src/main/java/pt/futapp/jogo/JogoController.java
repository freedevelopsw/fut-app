package pt.futapp.jogo;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.estatisticas.ResumoService;
import pt.futapp.votacao.VotacaoService;

@Controller
@RequestMapping("/jogos")
public class JogoController {

    private final JogoService jogos;
    private final EquipasService equipas;
    private final RegistoService registo;
    private final VotacaoService votacao;
    private final ResumoService resumo;

    public JogoController(JogoService jogos, EquipasService equipas, RegistoService registo, VotacaoService votacao,
                          ResumoService resumo) {
        this.resumo = resumo;
        this.jogos = jogos;
        this.equipas = equipas;
        this.registo = registo;
        this.votacao = votacao;
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Principal principal, HttpServletRequest pedido, Model model) {
        String urlBase = urlBase(pedido);
        model.addAttribute("conv", jogos.convocatoria(id, principal.getName(), urlBase));
        model.addAttribute("equipas", equipas.vista(id, principal.getName(), urlBase));
        model.addAttribute("registo", registo.vista(id, principal.getName()));
        var vVotacao = votacao.vista(id, principal.getName());
        model.addAttribute("votacao", vVotacao);
        model.addAttribute("resumo", vVotacao.fechada() ? resumo.resumo(id, principal.getName(), urlBase) : null);
        model.addAttribute("nav", "jogo");
        return "jogos/detalhe";
    }

    // ---------------------------------------------------------------- histórico (Organizador / Admin)

    @GetMapping("/historico")
    public String historico(Principal principal, Model model) {
        model.addAttribute("meses", jogos.historico(principal.getName()));
        model.addAttribute("nav", "jogo");
        return "jogos/historico";
    }

    // ---------------------------------------------------------------- registo do jogo
    // Golos: qualquer membro. Resultado, faltas, fechar e reabrir: Organizador / Admin (/gestao/**).

    @GetMapping("/{id}/registo")
    public String registo(@PathVariable Long id, @RequestParam(required = false) Equipa golo,
                          Principal principal, Model model) {
        model.addAttribute("registo", registo.vista(id, principal.getName()));
        model.addAttribute("goloDe", golo);
        model.addAttribute("nav", "jogo");
        return "jogos/registo";
    }

    @PostMapping("/{id}/registo/golos")
    public String adicionarGolo(@PathVariable Long id, @RequestParam Equipa equipa,
                                @RequestParam(required = false) Long marcador,
                                @RequestParam(required = false) Long assistencia,
                                Principal principal, RedirectAttributes redirect) {
        return executarRegisto(id, redirect, "Golo registado.",
            () -> registo.adicionarGolo(id, equipa, marcador, assistencia, principal.getName()));
    }

    @PostMapping("/{id}/registo/golos/{goloId}/apagar")
    public String apagarGolo(@PathVariable Long id, @PathVariable Long goloId,
                             Principal principal, RedirectAttributes redirect) {
        return executarRegisto(id, redirect, "Golo apagado.", () -> registo.apagarGolo(id, goloId, principal.getName()));
    }

    @PostMapping("/{id}/gestao/registo/resultado")
    public String definirResultado(@PathVariable Long id, @RequestParam int coletes, @RequestParam int semColetes,
                                   Principal principal, RedirectAttributes redirect) {
        return executarRegisto(id, redirect, "Resultado guardado.",
            () -> registo.definirResultado(id, coletes, semColetes, principal.getName()));
    }

    @PostMapping("/{id}/gestao/registo/faltas")
    public String marcarFalta(@PathVariable Long id, @RequestParam Long inscricaoId, @RequestParam boolean faltou,
                              Principal principal, RedirectAttributes redirect) {
        return executarRegisto(id, redirect, null, () -> registo.marcarFalta(id, inscricaoId, faltou, principal.getName()));
    }

    @PostMapping("/{id}/gestao/registo/fechar")
    public String fecharJogo(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            registo.fechar(id, principal.getName());
            redirect.addFlashAttribute("sucesso", "Jogo fechado. A votação está aberta.");
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/jogos/" + id + "/registo";
        }
        return "redirect:/jogos/" + id;
    }

    @PostMapping("/{id}/gestao/registo/reabrir")
    public String reabrirRegisto(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executarRegisto(id, redirect, "Registo reaberto.", () -> registo.reabrir(id, principal.getName()));
    }

    private String executarRegisto(Long id, RedirectAttributes redirect, String sucesso, Runnable acao) {
        try {
            acao.run();
            if (sucesso != null) {
                redirect.addFlashAttribute("sucesso", sucesso);
            }
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id + "/registo";
    }

    // ---------------------------------------------------------------- equipas (Organizador / Admin)

    @GetMapping("/{id}/equipas")
    public String equipas(@PathVariable Long id, Principal principal, HttpServletRequest pedido, Model model) {
        model.addAttribute("equipas", equipas.vista(id, principal.getName(), urlBase(pedido)));
        model.addAttribute("nav", "jogo");
        return "jogos/equipas";
    }

    @PostMapping("/{id}/gestao/equipas/gerar")
    public String gerarEquipas(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executarEquipas(id, redirect, "Equipas geradas. Ajusta à mão se for preciso.",
            () -> equipas.gerar(id, principal.getName()));
    }

    @PostMapping("/{id}/gestao/equipas/mover")
    public String moverJogador(@PathVariable Long id, @RequestParam Long inscricaoId,
                               @RequestParam(required = false) Equipa destino,
                               Principal principal, RedirectAttributes redirect) {
        return executarEquipas(id, redirect, null, () -> equipas.mover(id, inscricaoId, destino, principal.getName()));
    }

    @PostMapping("/{id}/gestao/equipas/publicar")
    public String publicarEquipas(@PathVariable Long id, Principal principal, HttpServletRequest pedido,
                                  RedirectAttributes redirect) {
        String urlBase = urlBase(pedido);
        try {
            equipas.publicar(id, principal.getName(), urlBase);
            redirect.addFlashAttribute("sucesso", "Equipas publicadas. Partilha-as no grupo.");
            redirect.addFlashAttribute("equipasPublicadasAgora", true);
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id + "/equipas";
    }

    private String executarEquipas(Long id, RedirectAttributes redirect, String sucesso, Runnable acao) {
        try {
            acao.run();
            if (sucesso != null) {
                redirect.addFlashAttribute("sucesso", sucesso);
            }
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id + "/equipas";
    }

    private static String urlBase(HttpServletRequest pedido) {
        return ServletUriComponentsBuilder.fromContextPath(pedido).build().toUriString();
    }

    // ---------------------------------------------------------------- jogador

    @PostMapping("/{id}/inscricao")
    public String inscrever(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Estás inscrito.", true, () -> jogos.inscrever(id, principal.getName()));
    }

    @PostMapping("/{id}/inscricao/cancelar")
    public String cancelar(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "A tua inscrição foi cancelada.", true,
            () -> jogos.cancelarPropria(id, principal.getName()));
    }

    // ---------------------------------------------------------------- Organizador / Admin

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("form", new NovoJogoForm());
        model.addAttribute("nav", "jogo");
        return "jogos/novo";
    }

    @PostMapping("/novo")
    public String criar(@Valid @ModelAttribute("form") NovoJogoForm form, BindingResult erros,
                        Principal principal, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            model.addAttribute("nav", "jogo");
            return "jogos/novo";
        }
        Long id = jogos.criar(form, principal.getName());
        redirect.addFlashAttribute("sucesso", "Jogo criado. Partilha a convocatória no grupo.");
        redirect.addFlashAttribute("listaMudou", true);
        return "redirect:/jogos/" + id;
    }

    @PostMapping("/{id}/gestao/cancelar")
    public String cancelarJogo(@PathVariable Long id, @RequestParam(required = false) String motivo,
                               Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Jogo cancelado. Avisa o grupo no WhatsApp.", false,
            () -> jogos.cancelar(id, motivo, principal.getName()));
    }

    @PostMapping("/{id}/gestao/fechar")
    public String fechar(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Convocatória fechada.", false,
            () -> jogos.fecharConvocatoria(id, principal.getName()));
    }

    @PostMapping("/{id}/gestao/inscricoes/{inscricaoId}/remover")
    public String remover(@PathVariable Long id, @PathVariable Long inscricaoId,
                          Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Saída registada como desistência.", true,
            () -> jogos.removerInscricao(id, inscricaoId, principal.getName()));
    }

    @PostMapping("/{id}/gestao/jogadores")
    public String adicionarJogador(@PathVariable Long id, @RequestParam Long utilizadorId,
                                   Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Jogador adicionado.", true,
            () -> jogos.adicionarJogador(id, utilizadorId, principal.getName()));
    }

    @PostMapping("/{id}/gestao/convidados")
    public String adicionarConvidado(@PathVariable Long id, @RequestParam String nome,
                                     Principal principal, RedirectAttributes redirect) {
        return executar(id, redirect, "Convidado adicionado.", true,
            () -> jogos.adicionarConvidado(id, nome, principal.getName()));
    }

    private String executar(Long id, RedirectAttributes redirect, String sucesso, boolean mudaLista, Runnable acao) {
        try {
            acao.run();
            redirect.addFlashAttribute("sucesso", sucesso);
            if (mudaLista) {
                redirect.addFlashAttribute("listaMudou", true);
            }
        } catch (OperacaoInvalidaException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos/" + id;
    }
}
