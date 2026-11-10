package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.jogo.EstadoInscricao;

@Converter
public class EstadoInscricaoConverter extends EnumTextoConverter<EstadoInscricao> {

    public EstadoInscricaoConverter() {
        super(EstadoInscricao.class);
    }
}
