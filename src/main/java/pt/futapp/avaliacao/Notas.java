package pt.futapp.avaliacao;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** As seis notas de 1 a 10 de uma avaliação, pela ordem da carta. Cada uma é opcional (null). */
public record Notas(Integer velocidade, Integer tecnica, Integer remate, Integer passe, Integer defesa,
                    Integer resistencia) {

    /** Nomes dos atributos, pela mesma ordem de {@link #valores()}. */
    public static final List<String> NOMES = List.of("Velocidade", "Técnica", "Remate", "Passe", "Defesa", "Resistência");

    /** Siglas para a tabela de detalhes (mesma ordem). */
    public static final List<String> SIGLAS = List.of("VEL", "TÉC", "REM", "PAS", "DEF", "RES");

    public List<Integer> valores() {
        return Arrays.asList(velocidade, tecnica, remate, passe, defesa, resistencia);
    }

    public boolean vazia() {
        return valores().stream().allMatch(Objects::isNull);
    }

    public boolean validas() {
        return valores().stream().filter(Objects::nonNull).allMatch(v -> v >= 1 && v <= 10);
    }
}
