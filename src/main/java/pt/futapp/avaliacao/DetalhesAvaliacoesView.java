package pt.futapp.avaliacao;

import java.util.List;

/** Quem avaliou um jogador e com que notas (o Admin vê sempre; os outros quando o interruptor está ligado). */
public record DetalhesAvaliacoesView(Long jogadorId, String nomeVisivel, List<Linha> linhas, List<String> medias,
                                     String votosTexto) {

    public List<String> siglas() {
        return Notas.SIGLAS;
    }

    /** Um colega e as seis notas que deu ("–" quando deixou em branco). */
    public record Linha(String avaliador, List<String> notas) {
    }
}
