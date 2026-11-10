package pt.futapp.utilizador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilizadorRepository extends JpaRepository<Utilizador, Long> {

    Optional<Utilizador> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Utilizador> findAllByOrderByNomeAsc();

    List<Utilizador> findByBloqueadoFalseOrderByNomeAsc();

    boolean existsByRolesContaining(Role role);
}
