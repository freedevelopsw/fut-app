package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.utilizador.Posicao;

@Converter
public class PosicaoConverter extends EnumTextoConverter<Posicao> {

    public PosicaoConverter() {
        super(Posicao.class);
    }
}
