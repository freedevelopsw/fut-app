package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.jogo.EstadoJogo;

@Converter
public class EstadoJogoConverter extends EnumTextoConverter<EstadoJogo> {

    public EstadoJogoConverter() {
        super(EstadoJogo.class);
    }
}
