package pt.futapp.utilizador;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nível de acesso escolhido no dropdown da carta. As roles são cumulativas:
 * Organizador inclui Jogador; Admin inclui Organizador e Jogador.
 */
public enum NivelAcesso {
    JOGADOR("Jogador"),
    ORGANIZADOR("Organizador"),
    ADMIN("Admin");

    private final String descricao;

    NivelAcesso(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<Role> roles() {
        return switch (this) {
            case JOGADOR -> EnumSet.of(Role.JOGADOR);
            case ORGANIZADOR -> EnumSet.of(Role.JOGADOR, Role.ORGANIZADOR);
            case ADMIN -> EnumSet.of(Role.JOGADOR, Role.ORGANIZADOR, Role.ADMIN);
        };
    }

    public static NivelAcesso de(Utilizador u) {
        if (u.temRole(Role.ADMIN)) {
            return ADMIN;
        }
        return u.temRole(Role.ORGANIZADOR) ? ORGANIZADOR : JOGADOR;
    }
}
