package pt.futapp.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

import pt.futapp.estatisticas.EstatisticasService;
import pt.futapp.jogo.JogoService;
import pt.futapp.utilizador.JogadorService;

@Controller
public class HomeController {

    private final JogoService jogos;
    private final JogadorService jogadores;
    private final EstatisticasService estatisticas;

    public HomeController(JogoService jogos, JogadorService jogadores, EstatisticasService estatisticas) {
        this.jogos = jogos;
        this.jogadores = jogadores;
        this.estatisticas = estatisticas;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("proximos", jogos.proximosJogos());
        model.addAttribute("anteriores", jogos.jogosAnteriores());
        model.addAttribute("nav", "jogo");
        return "inicio";
    }

    @GetMapping("/estatisticas")
    public String estatisticas(Principal principal, Model model) {
        model.addAttribute("jogadores", jogadores.jogadores(principal.getName()));
        model.addAttribute("estatisticas", estatisticas.temporada(principal.getName()));
        model.addAttribute("nav", "estatisticas");
        return "estatisticas";
    }

}
