package pt.futapp.jogo;

public enum Equipa {
    COLETES("Coletes"),
    SEM_COLETES("Sem coletes");

    private final String descricao;

    Equipa(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Equipa outra() {
        return this == COLETES ? SEM_COLETES : COLETES;
    }
}
