package pt.futapp.utilizador;

public record JogadorResumo(Long id, String nomeVisivel, String nome, String iniciais,
                            String posicaoSigla, Double rating, boolean eu) {
}
