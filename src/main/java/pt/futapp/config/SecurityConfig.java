package pt.futapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import pt.futapp.utilizador.UtilizadorService;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UtilizadorService utilizadores,
                                                   @Value("${futapp.remember-me.key}") String chave,
                                                   @Value("${futapp.remember-me.dias:30}") int dias) throws Exception {
        http
            .authorizeHttpRequests(pedidos -> pedidos
                .requestMatchers("/login", "/registo", "/css/**", "/img/**", "/manifest.webmanifest", "/error").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/jogos/novo", "/jogos/*/equipas", "/jogos/*/registo", "/jogos/*/gestao/**").hasAnyRole("ORGANIZADOR", "ADMIN")
                .anyRequest().authenticated())
            .formLogin(login -> login
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureHandler((pedido, resposta, erro) -> resposta.sendRedirect(pedido.getContextPath()
                    + (erro instanceof DisabledException ? "/login?bloqueado" : "/login?error")))
                .permitAll())
            .rememberMe(lembrar -> lembrar
                .key(chave)
                .tokenValiditySeconds(dias * 24 * 60 * 60)
                .userDetailsService(utilizadores)
                .alwaysRemember(true))
            .logout(sair -> sair
                .logoutSuccessUrl("/login?saiu")
                .permitAll())
            .addFilterAfter(new ContaBloqueadaFilter(utilizadores), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Publica os eventos de login, que a auditoria regista. */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }
}
