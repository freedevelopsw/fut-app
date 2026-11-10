package pt.futapp.comum;

/**
 * Ação que as regras do negócio não permitem. A mensagem é mostrada ao utilizador.
 */
public class OperacaoInvalidaException extends RuntimeException {

    public OperacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
