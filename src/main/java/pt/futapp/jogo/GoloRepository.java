package pt.futapp.jogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoloRepository extends JpaRepository<Golo, Long> {

    List<Golo> findByJogoOrderByIdAsc(Jogo jogo);

    List<Golo> findByJogoAndEquipaAndMarcadorIsNullOrderByIdDesc(Jogo jogo, Equipa equipa);

    long countByJogoAndEquipa(Jogo jogo, Equipa equipa);

    long countByJogoAndEquipaAndMarcadorIsNotNull(Jogo jogo, Equipa equipa);
}
