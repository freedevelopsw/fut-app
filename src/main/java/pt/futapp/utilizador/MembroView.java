package pt.futapp.utilizador;

public record MembroView(Long id, String nome, String username, boolean organizador, boolean admin,
                         boolean bloqueado, String bloqueadoEm, boolean eu) {
}
