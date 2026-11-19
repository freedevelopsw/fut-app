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

    /** Comprimento máximo de "Nome (alcunha)" numa lista de escolha (select) num telefone de 360 px. */
    public static final int MAX_NOME_COM_ALCUNHA = 30;

    /**
     * A alcunha, quando existe e é diferente do nome; senão null.
     */
    public static String alcunhaDistinta(String nome, String alcunha) {
        if (alcunha == null || alcunha.isBlank() || alcunha.trim().equalsIgnoreCase(nome == null ? "" : nome.trim())) {
            return null;
        }
        return alcunha.trim();
    }

    /**
     * "Nome (alcunha)" quando há alcunha e o texto cabe numa lista de escolha no telefone; senão só o nome.
     * Usado nos boletins de voto, onde as alcunhas sozinhas não chegam para saber quem é quem.
     */
    public static String nomeComAlcunha(String nome, String alcunha) {
        String a = alcunhaDistinta(nome, alcunha);
        if (a == null) {
            return nome;
        }
        String junto = nome + " (" + a + ")";
        return junto.length() <= MAX_NOME_COM_ALCUNHA ? junto : nome;
    }
}
