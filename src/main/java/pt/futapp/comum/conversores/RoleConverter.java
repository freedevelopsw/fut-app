package pt.futapp.comum.conversores;

import jakarta.persistence.Converter;
import pt.futapp.utilizador.Role;

@Converter
public class RoleConverter extends EnumTextoConverter<Role> {

    public RoleConverter() {
        super(Role.class);
    }
}
