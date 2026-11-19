package pt.futapp.jogo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pt.futapp.utilizador.Utilizador;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    /** Inscrições ativas por ordem de chegada: as primeiras {@code vagas} jogam, as restantes ficam em espera. */
    List<Inscricao> findByJogoAndEstadoOrderByInscritoEmAscIdAsc(Jogo jogo, EstadoInscricao estado);

    List<Inscricao> findByJogoAndEstadoOrderByCanceladoEmDesc(Jogo jogo, EstadoInscricao estado);

    Optional<Inscricao> findFirstByJogoAndJogadorAndEstado(Jogo jogo, Utilizador jogador, EstadoInscricao estado);

    boolean existsByJogoAndJogadorAndEstado(Jogo jogo, Utilizador jogador, EstadoInscricao estado);

    long countByJogoAndEstado(Jogo jogo, EstadoInscricao estado);

    List<Inscricao> findByJogador(Utilizador jogador);

    /** Inscrições ativas por jogo, numa só consulta: [jogoId, quantidade]. Para as listas de jogos. */
    @Query("select i.jogo.id, count(i) from Inscricao i where i.jogo in :jogos and i.estado = :estado group by i.jogo.id")
    List<Object[]> contarPorJogo(@Param("jogos") java.util.Collection<Jogo> jogos, @Param("estado") EstadoInscricao estado);

    boolean existsByCanceladoPor(Utilizador utilizador);

    List<Inscricao> findByJogadorAndEstadoAndJogo_Estado(Utilizador jogador, EstadoInscricao estado, EstadoJogo estadoJogo);
}
