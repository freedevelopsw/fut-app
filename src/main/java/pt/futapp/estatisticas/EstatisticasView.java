package pt.futapp.estatisticas;

import java.util.List;

/**
 * Estatísticas da temporada, calculadas a partir dos jogos fechados. Os convidados contam (agregados pelo nome).
 * {@code ordem}: como a tabela vem ordenada.
 */
public record EstatisticasView(int jogosFechados, List<Ranking> rankings, List<Linha> tabela, OrdemTabela ordem) {

    /**
     * Um ranking (por exemplo, melhores marcadores): no máximo 5 nomes, empates na mesma posição.
     * {@code maisEmpatados}: quantos ficaram de fora com o mesmo valor do último mostrado.
     */
    public record Ranking(String titulo, String singular, String plural, List<Lugar> lugares, int maisEmpatados) {

        public String unidade(long valor) {
            return valor == 1 ? singular : plural;
        }

        public long ultimoValor() {
            return lugares.isEmpty() ? 0 : lugares.get(lugares.size() - 1).valor();
        }
    }

    /** {@code jogadorId} null = convidado (sem carta). */
    public record Lugar(int posicao, Long jogadorId, String nome, long valor, boolean eu, boolean convidado) {
    }

    /** Números de um jogador (ou de um convidado, com {@code jogadorId} null) em todos os jogos fechados. */
    public record Linha(Long jogadorId, String nome, int jogos, int vitorias, int empates, int derrotas,
                        int golos, int assistencias, int mvps, int premios, int faltas, boolean eu, boolean convidado) {

        public static Linha vazia(Long jogadorId, String nome, boolean eu) {
            return new Linha(jogadorId, nome, 0, 0, 0, 0, 0, 0, 0, 0, 0, eu, false);
        }

        public boolean temJogos() {
            return jogos > 0 || faltas > 0;
        }
    }
}
