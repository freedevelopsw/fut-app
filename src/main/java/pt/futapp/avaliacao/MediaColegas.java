package pt.futapp.avaliacao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Média das avaliações dos colegas a um jogador. Cada atributo é a média das notas dadas a esse atributo (null se
 * ninguém lhe deu nota); o rating é a média dos atributos com nota. Aparece logo a partir do primeiro voto.
 * {@code votos}: quantos colegas avaliaram (nunca "X de Y", porque o número de jogadores muda).
 */
public record MediaColegas(List<Double> atributos, Double rating, int votos) {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");

    public static final MediaColegas SEM_VOTOS = new MediaColegas(Collections.nCopies(Notas.NOMES.size(), null), null, 0);

    public static MediaColegas de(List<Notas> avaliacoes) {
        List<Notas> comNotas = avaliacoes.stream().filter(n -> !n.vazia()).toList();
        if (comNotas.isEmpty()) {
            return SEM_VOTOS;
        }
        List<Double> medias = new ArrayList<>();
        for (int i = 0; i < Notas.NOMES.size(); i++) {
            final int k = i;
            OptionalDouble media = comNotas.stream().map(n -> n.valores().get(k)).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average();
            medias.add(media.isPresent() ? media.getAsDouble() : null);
        }
        OptionalDouble rating = medias.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return new MediaColegas(Collections.unmodifiableList(medias), rating.isPresent() ? rating.getAsDouble() : null,
            comNotas.size());
    }

    public boolean temVotos() {
        return votos > 0;
    }

    public String ratingTexto() {
        return decimal(rating);
    }

    public String votosTexto() {
        return votosTexto(votos);
    }

    /** "1 voto", "12 votos". */
    public static String votosTexto(int votos) {
        return votos == 1 ? "1 voto" : votos + " votos";
    }

    /** Uma casa decimal com vírgula ("7,3"); "–" quando não há valor. */
    public static String decimal(Double valor) {
        return valor == null ? "–" : String.format(PT, "%.1f", valor);
    }
}
