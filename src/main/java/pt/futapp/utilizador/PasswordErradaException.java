package pt.futapp.utilizador;

import pt.futapp.comum.OperacaoInvalidaException;

/** A password atual indicada não corresponde à do utilizador. */
public class PasswordErradaException extends OperacaoInvalidaException {

    public PasswordErradaException() {
        super("A password atual não está certa.");
    }
}
