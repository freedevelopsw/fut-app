package pt.futapp.utilizador;

import pt.futapp.comum.OperacaoInvalidaException;

/** O registo foi recusado porque o link de convite falta, expirou ou não é válido. */
public class ConviteInvalidoException extends OperacaoInvalidaException {

    public ConviteInvalidoException(String mensagem) {
        super(mensagem);
    }
}
