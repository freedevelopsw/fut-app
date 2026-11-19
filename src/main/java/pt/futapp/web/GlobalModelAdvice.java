package pt.futapp.web;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;

import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.EuView;
import pt.futapp.utilizador.UtilizadorService;

/** Disponibiliza o utilizador autenticado ("eu") a todos os templates e trata erros de negócio. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UtilizadorService utilizadores;
    private final Versao versao;

    public GlobalModelAdvice(UtilizadorService utilizadores, Versao versao) {
        this.utilizadores = utilizadores;
        this.versao = versao;
    }

    /** Versão da app (por exemplo "1.0.0"), mostrada no Perfil. */
    @ModelAttribute("versao")
    public String versao() {
        return versao.numero();
    }

    @ModelAttribute("eu")
    public EuView eu(Principal principal) {
        if (principal == null) {
            return null;
        }
        return utilizadores.vista(principal.getName()).orElse(null);
    }

    /** Erros de negócio que não foram tratados no controller (por exemplo, um jogo que não existe). */
    @ExceptionHandler(OperacaoInvalidaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String operacaoInvalida(OperacaoInvalidaException e, Model model) {
        model.addAttribute("erro", e.getMessage());
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        return "error";
    }
}
