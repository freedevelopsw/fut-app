package pt.futapp.comum;

import java.util.Locale;

/** Números com casas decimais como aparecem na interface (português de Portugal: vírgula). */
public final class Decimais {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");

    private Decimais() {
    }

    /** Uma casa decimal com vírgula ("7,3"); "–" quando não há valor. */
    public static String umaCasa(Double valor) {
        return valor == null ? "–" : String.format(PT, "%.1f", valor);
    }
}
