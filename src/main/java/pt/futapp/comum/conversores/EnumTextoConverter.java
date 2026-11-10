package pt.futapp.comum.conversores;

import jakarta.persistence.AttributeConverter;

/**
 * Guarda um enum como texto simples (VARCHAR), sem o tipo ENUM nativo da base de dados nem restrições de valores.
 * Assim, acrescentar um valor novo a um enum nunca obriga a mudar o esquema da base de dados.
 */
public abstract class EnumTextoConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected EnumTextoConverter(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E valor) {
        return valor == null ? null : valor.name();
    }

    @Override
    public E convertToEntityAttribute(String texto) {
        return texto == null || texto.isBlank() ? null : Enum.valueOf(tipo, texto.trim());
    }
}
