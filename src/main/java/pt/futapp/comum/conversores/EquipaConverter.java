package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.jogo.Equipa;

@Converter
public class EquipaConverter extends EnumTextoConverter<Equipa> {

    public EquipaConverter() {
        super(Equipa.class);
    }
}
