package pt.futapp.config;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pt.futapp.utilizador.UtilizadorService;

/**
 * Termina a sessão de quem foi bloqueado depois de ter entrado: no pedido seguinte, a sessão e o cookie
 * de sessão persistente são apagados e a pessoa vai para o login.
 * Não é um @Component de propósito: é registado só na cadeia do Spring Security (SecurityConfig).
 */
public class ContaBloqueadaFilter extends OncePerRequestFilter {

    private final UtilizadorService utilizadores;

    public ContaBloqueadaFilter(UtilizadorService utilizadores) {
        this.utilizadores = utilizadores;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken)
                && utilizadores.estaBloqueado(autenticacao.getName())) {
            Sessoes.terminar(pedido, resposta);
            resposta.sendRedirect(pedido.getContextPath() + "/login?bloqueado");
            return;
        }
        cadeia.doFilter(pedido, resposta);
    }
}
