package pt.futapp.jogo;

/** Estados do ciclo de um jogo (ver a especificação, secção "Ciclo do jogo"). */
public enum EstadoJogo {
    CONVOCATORIA_ABERTA("Convocatória aberta"),
    CONVOCATORIA_FECHADA("Convocatória fechada"),
    EQUIPAS_PUBLICADAS("Equipas publicadas"),
    EM_REGISTO("Em registo"),
    VOTACAO_ABERTA("Votação aberta"),
    FECHADO("Fechado");

    private final String descricao;

    EstadoJogo(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
