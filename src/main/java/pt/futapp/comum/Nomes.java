package pt.futapp.comum;

import java.util.Locale;

public final class Nomes {

    private Nomes() {
    }

    /** Iniciais para o avatar: "Rui" → "RU", "Rui Costa" → "RC". */
    public static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = nome.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase(Locale.ROOT);
        }
        return (partes[0].substring(0, 1) + partes[partes.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }
}
