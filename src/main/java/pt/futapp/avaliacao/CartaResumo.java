package pt.futapp.avaliacao;

/**
 * Uma linha das Cartas dos jogadores (ecrã Estatísticas): a autoavaliação ({@code auto}) e a média dos colegas
 * ({@code colegas}) com o número de votos. As duas aparecem com uma casa decimal.
 */
public record CartaResumo(Long id, String nomeVisivel, String iniciais, String posicaoSigla, Double auto,
                          Double colegas, int votos, boolean eu) {

    public String autoTexto() {
        return MediaColegas.decimal(auto);
    }

    public String colegasTexto() {
        return MediaColegas.decimal(colegas);
    }

    public String votosTexto() {
        return MediaColegas.votosTexto(votos);
    }
}
