package pt.futapp.avaliacao;

/**
 * Ordenação das Cartas dos jogadores no ecrã Estatísticas: pela autoavaliação (omissão) ou pela média dos colegas.
 * Em empate conta a outra nota, depois o nome; quem não tem nota fica no fim.
 */
public enum OrdemCartas {
    AUTO("auto"),
    COLEGAS("colegas");

    private final String parametro;

    OrdemCartas(String parametro) {
        this.parametro = parametro;
    }

    /** Valor no endereço ({@code /estatisticas?cartas=colegas}). */
    public String getParametro() {
        return parametro;
    }

    /** Lê o parâmetro do endereço; vazio ou desconhecido = pela autoavaliação. */
    public static OrdemCartas de(String parametro) {
        for (OrdemCartas o : values()) {
            if (o.parametro.equalsIgnoreCase(parametro == null ? "" : parametro.trim())) {
                return o;
            }
        }
        return AUTO;
    }
}
