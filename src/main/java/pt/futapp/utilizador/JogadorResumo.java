package pt.futapp.utilizador;

public record JogadorResumo(Long id, String nomeVisivel, String nome, String iniciais,
                            String posicaoSigla, Integer rating, boolean eu) {
}
