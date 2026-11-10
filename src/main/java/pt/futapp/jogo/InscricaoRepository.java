package pt.futapp.jogo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.futapp.utilizador.Utilizador;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    /** Inscrições ativas por ordem de chegada: as primeiras {@code vagas} jogam, as restantes ficam em espera. */
    List<Inscricao> findByJogoAndEstadoOrderByInscritoEmAscIdAsc(Jogo jogo, EstadoInscricao estado);

    List<Inscricao> findByJogoAndEstadoOrderByCanceladoEmDesc(Jogo jogo, EstadoInscricao estado);

    Optional<Inscricao> findFirstByJogoAndJogadorAndEstado(Jogo jogo, Utilizador jogador, EstadoInscricao estado);

    boolean existsByJogoAndJogadorAndEstado(Jogo jogo, Utilizador jogador, EstadoInscricao estado);

    long countByJogoAndEstado(Jogo jogo, EstadoInscricao estado);

    List<Inscricao> findByJogadorAndEstadoAndJogo_Estado(Utilizador jogador, EstadoInscricao estado, EstadoJogo estadoJogo);
}
