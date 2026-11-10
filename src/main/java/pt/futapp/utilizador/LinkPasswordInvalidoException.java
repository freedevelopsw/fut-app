package pt.futapp.utilizador;

import pt.futapp.comum.OperacaoInvalidaException;

/** O link de redefinição de password não serve (expirou, já foi usado, é inválido ou a conta está bloqueada). */
public class LinkPasswordInvalidoException extends OperacaoInvalidaException {

    public LinkPasswordInvalidoException(String mensagem) {
        super(mensagem);
    }
}
