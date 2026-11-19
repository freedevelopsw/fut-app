package pt.futapp.utilizador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilizadorRepository extends JpaRepository<Utilizador, Long> {

    Optional<Utilizador> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Utilizador> findByRemovidoFalseOrderByNomeAsc();

    /**
     * Um membro pelo id, se a conta não tiver sido removida. Usar SEMPRE isto (e não findById) para mostrar ou agir
     * sobre alguém: uma conta removida não aparece nem pode ser alterada. O findById só serve para histórico.
     */
    default Optional<Utilizador> findMembro(Long id) {
        return id == null ? Optional.empty() : findById(id).filter(u -> !u.isRemovido());
    }

    List<Utilizador> findByBloqueadoFalseOrderByNomeAsc();

    boolean existsByRolesContaining(Role role);
}
