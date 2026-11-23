package pt.futapp.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

import pt.futapp.avaliacao.AvaliacaoService;
import pt.futapp.avaliacao.InterruptorDetalhesAvaliacoes;
import pt.futapp.avaliacao.OrdemCartas;
import pt.futapp.estatisticas.EstatisticasService;
import pt.futapp.estatisticas.OrdemTabela;
import pt.futapp.jogo.JogoService;

@Controller
public class HomeController {

    private final JogoService jogos;
    private final EstatisticasService estatisticas;
    private final AvaliacaoService avaliacoes;
    private final InterruptorDetalhesAvaliacoes interruptor;

    public HomeController(JogoService jogos, EstatisticasService estatisticas, AvaliacaoService avaliacoes,
                          InterruptorDetalhesAvaliacoes interruptor) {
        this.jogos = jogos;
        this.estatisticas = estatisticas;
        this.avaliacoes = avaliacoes;
        this.interruptor = interruptor;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("proximos", jogos.proximosJogos());
        model.addAttribute("anteriores", jogos.jogosAnteriores());
        model.addAttribute("nav", "jogo");
        return "inicio";
    }

    /**
     * {@code ordem}: ordenação da tabela (vitorias / golos); {@code cartas}: ordenação das cartas (auto / colegas).
     * Quando vêm no endereço, a secção correspondente aparece aberta.
     */
    @GetMapping("/estatisticas")
    public String estatisticas(@RequestParam(required = false) String ordem, @RequestParam(required = false) String cartas,
                               Principal principal, Model model) {
        OrdemCartas ordemCartas = OrdemCartas.de(cartas);
        model.addAttribute("cartas", avaliacoes.cartas(principal.getName(), ordemCartas));
        model.addAttribute("ordemCartas", ordemCartas);
        model.addAttribute("cartasAbertas", cartas != null);
        model.addAttribute("detalhesVisiveis", interruptor.ligado());
        model.addAttribute("estatisticas", estatisticas.temporada(principal.getName(), OrdemTabela.de(ordem)));
        model.addAttribute("tabelaAberta", ordem != null);
        model.addAttribute("nav", "estatisticas");
        return "estatisticas";
    }

}
