package pt.futapp.utilizador;

public enum Posicao {
    GUARDA_REDES("Guarda-redes", "GR"),
    DEFESA("Defesa", "DEF"),
    MEDIO("Médio", "MED"),
    AVANCADO("Avançado", "AV");

    private final String descricao;
    private final String sigla;

    Posicao(String descricao, String sigla) {
        this.descricao = descricao;
        this.sigla = sigla;
    }

    public String getDescricao() { return descricao; }
    public String getSigla() { return sigla; }
}
