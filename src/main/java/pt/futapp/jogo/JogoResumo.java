package pt.futapp.jogo;

public record JogoResumo(Long id, String dia, String hora, String local, String estado,
                         boolean aberta, long inscritos, int vagas, String resultado) {
}
