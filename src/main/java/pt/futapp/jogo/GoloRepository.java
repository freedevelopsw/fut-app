package pt.futapp.jogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoloRepository extends JpaRepository<Golo, Long> {

    List<Golo> findByJogoOrderByIdAsc(Jogo jogo);

    List<Golo> findByJogoAndEquipaAndMarcadorIsNullOrderByIdDesc(Jogo jogo, Equipa equipa);

    long countByJogoAndEquipa(Jogo jogo, Equipa equipa);

    boolean existsByRegistadoPor(pt.futapp.utilizador.Utilizador utilizador);

    /** Golos por jogo e equipa, numa só consulta: [jogoId, equipa, quantidade]. Para as listas de jogos. */
    @Query("select g.jogo.id, g.equipa, count(g) from Golo g where g.jogo in :jogos group by g.jogo.id, g.equipa")
    List<Object[]> contarPorJogoEEquipa(@Param("jogos") java.util.Collection<Jogo> jogos);

    long countByJogoAndEquipaAndMarcadorIsNotNull(Jogo jogo, Equipa equipa);
}
