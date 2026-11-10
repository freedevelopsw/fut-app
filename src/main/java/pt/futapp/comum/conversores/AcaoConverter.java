package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.audit.Acao;

@Converter
public class AcaoConverter extends EnumTextoConverter<Acao> {

    public AcaoConverter() {
        super(Acao.class);
    }
}
