package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.utilizador.Pe;

@Converter
public class PeConverter extends EnumTextoConverter<Pe> {

    public PeConverter() {
        super(Pe.class);
    }
}
