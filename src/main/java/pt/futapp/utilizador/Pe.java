package pt.futapp.utilizador;

public enum Pe {
    DIREITO("Direito"),
    ESQUERDO("Esquerdo"),
    AMBOS("Ambos");

    private final String descricao;

    Pe(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() { return descricao; }
}
