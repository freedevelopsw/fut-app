package pt.futapp.avaliacao;

import java.util.List;

/**
 * O que a carta de um jogador mostra das avaliações: cada atributo com a autoavaliação e a média dos colegas.
 * {@code podeVerDetalhes}: o Admin, ou todos quando o interruptor do Admin está ligado.
 */
public record CartaAvaliacoesView(Long jogadorId, String nomeVisivel, MediaColegas media, List<Linha> atributos,
                                  boolean podeAvaliar, boolean jaAvaliei, boolean podeVerDetalhes) {

    public String ratingColegasTexto() {
        return media.ratingTexto();
    }

    public boolean temVotos() {
        return media.temVotos();
    }

    public int votos() {
        return media.votos();
    }

    public String votosTexto() {
        return media.votosTexto();
    }

    /** "Já votaram 12 colegas", "Já votou 1 colega", "Ainda nenhum colega votou". */
    public String jaVotaramTexto() {
        int v = media.votos();
        return v == 0 ? "Ainda nenhum colega votou" : (v == 1 ? "Já votou 1 colega" : "Já votaram " + v + " colegas");
    }

    /** Um atributo: a autoavaliação (1 a 10, ou null) e a média dos colegas (ou null). */
    public record Linha(String nome, Integer auto, Double colegas) {

        public String autoTexto() {
            return auto == null ? "–" : auto.toString();
        }

        public String colegasTexto() {
            return MediaColegas.decimal(colegas);
        }

        public int percentagemAuto() {
            return auto == null ? 0 : auto * 10;
        }

        public int percentagemColegas() {
            return colegas == null ? 0 : (int) Math.round(colegas * 10);
        }
    }
}
