package pt.futapp.utilizador;

/** O utilizador autenticado, tal como os templates o veem. */
public record EuView(Long id, String nome, String username, boolean gestor, boolean admin) {
}
