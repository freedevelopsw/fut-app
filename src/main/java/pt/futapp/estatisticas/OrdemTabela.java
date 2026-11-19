package pt.futapp.estatisticas;

/**
 * Ordenação da tabela "Todos os jogadores": por vitórias (omissão) ou por golos. Em empate conta o outro critério,
 * depois os jogos e o nome. Os convidados ficam sempre no fundo da tabela.
 */
public enum OrdemTabela {
    VITORIAS("vitorias"),
    GOLOS("golos");

    private final String parametro;

    OrdemTabela(String parametro) {
        this.parametro = parametro;
    }

    /** Valor no endereço ({@code /estatisticas?ordem=golos}). */
    public String getParametro() {
        return parametro;
    }

    /** Lê o parâmetro do endereço; vazio ou desconhecido = por vitórias. */
    public static OrdemTabela de(String parametro) {
        for (OrdemTabela o : values()) {
            if (o.parametro.equalsIgnoreCase(parametro == null ? "" : parametro.trim())) {
                return o;
            }
        }
        return VITORIAS;
    }
}
