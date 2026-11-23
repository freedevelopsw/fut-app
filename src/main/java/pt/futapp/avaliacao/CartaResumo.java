package pt.futapp.avaliacao;

/**
 * Uma linha das Cartas dos jogadores (ecrã Estatísticas): a autoavaliação ({@code auto}, rating inteiro da carta) e a
 * média dos colegas ({@code colegas}, com uma casa decimal) com o número de votos.
 */
public record CartaResumo(Long id, String nomeVisivel, String iniciais, String posicaoSigla, Integer auto,
                          Double colegas, int votos, boolean eu) {

    public String autoTexto() {
        return auto == null ? "–" : auto.toString();
    }

    public String colegasTexto() {
        return MediaColegas.decimal(colegas);
    }

    public String votosTexto() {
        return MediaColegas.votosTexto(votos);
    }
}
