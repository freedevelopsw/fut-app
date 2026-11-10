package pt.futapp.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import pt.futapp.jogo.JogoService;

@Controller
public class HomeController {

    private final JogoService jogos;

    public HomeController(JogoService jogos) {
        this.jogos = jogos;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("proximos", jogos.proximosJogos());
        model.addAttribute("anteriores", jogos.jogosAnteriores());
        model.addAttribute("nav", "jogo");
        return "inicio";
    }

    @GetMapping("/estatisticas")
    public String estatisticas(Model model) {
        model.addAttribute("nav", "estatisticas");
        return "estatisticas";
    }

    @GetMapping("/perfil")
    public String perfil(Model model) {
        model.addAttribute("nav", "perfil");
        return "perfil";
    }
}
