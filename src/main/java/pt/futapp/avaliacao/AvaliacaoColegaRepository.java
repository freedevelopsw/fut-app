package pt.futapp.avaliacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.futapp.utilizador.Utilizador;

public interface AvaliacaoColegaRepository extends JpaRepository<AvaliacaoColega, Long> {

    Optional<AvaliacaoColega> findByAvaliadorAndAvaliado(Utilizador avaliador, Utilizador avaliado);

    List<AvaliacaoColega> findByAvaliado(Utilizador avaliado);

    /**
     * Apaga as avaliações dadas e recebidas por um membro (ao removê-lo). Carrega-as e apaga uma a uma (e não com um
     * DELETE em massa), para o contexto JPA ficar certo e estas linhas saírem antes do próprio utilizador.
     */
    void deleteByAvaliadorOrAvaliado(Utilizador avaliador, Utilizador avaliado);
}
