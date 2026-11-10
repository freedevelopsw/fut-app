package pt.futapp.votacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.futapp.jogo.Jogo;
import pt.futapp.utilizador.Utilizador;

public interface VotoRepository extends JpaRepository<Voto, Long> {

    List<Voto> findByJogoOrderByIdAsc(Jogo jogo);

    List<Voto> findByJogoAndAnuladoFalse(Jogo jogo);

    List<Voto> findByJogoAndEleitorAndAnuladoFalse(Jogo jogo, Utilizador eleitor);
}
