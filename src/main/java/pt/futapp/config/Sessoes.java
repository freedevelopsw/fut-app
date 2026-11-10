package pt.futapp.config;

import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Termina a sessão no dispositivo atual: sessão do servidor, contexto de segurança e cookie de sessão persistente. */
public final class Sessoes {

    /** Nome por omissão do cookie de "remember-me" do Spring Security. */
    public static final String COOKIE_LEMBRAR = "remember-me";

    private Sessoes() {
    }

    public static void terminar(HttpServletRequest pedido, HttpServletResponse resposta) {
        HttpSession sessao = pedido.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie apagar = new Cookie(COOKIE_LEMBRAR, "");
        apagar.setMaxAge(0);
        apagar.setPath(pedido.getContextPath().isEmpty() ? "/" : pedido.getContextPath());
        resposta.addCookie(apagar);
    }
}
