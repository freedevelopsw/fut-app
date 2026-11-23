package pt.futapp.utilizador;

import java.util.List;

/** A carta de um jogador, tal como os templates a mostram. */
public record CartaView(
    Long id,
    String nome,
    String alcunha,
    String nomeVisivel,
    String iniciais,
    String posicaoSigla,
    String posicao,
    String posicaoSecundaria,
    String pe,
    Integer rating,
    Double media,
    List<Atributo> atributos,
    boolean preenchida,
    boolean bloqueado,
    boolean eu,
    boolean podeEditar,
    NivelAcesso nivel,
    boolean mostraNivel,
    List<NivelAcesso> opcoesNivel,
    String motivoNivelFixo) {

    /** O rating da autoavaliação com uma casa decimal ("8,2"); "–" sem atributos preenchidos. */
    public String ratingTexto() {
        return pt.futapp.comum.Decimais.umaCasa(media);
    }

    public boolean podeMudarNivel() {
        return !opcoesNivel.isEmpty();
    }

    /** Um atributo de 1 a 10; {@code valor} é null quando o jogador não o preencheu. */
    public record Atributo(String nome, Integer valor) {

        public int percentagem() {
            return valor == null ? 0 : valor * 10;
        }
    }
}
