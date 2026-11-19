package pt.futapp.jogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByEstadoNotInOrderByDataHoraAsc(List<EstadoJogo> estados);

    List<Jogo> findByEstadoInAndDataHoraGreaterThanEqualOrderByDataHoraDesc(List<EstadoJogo> estados,
                                                                           java.time.LocalDateTime desde);

    /** Jogos num intervalo [desde, ate[. */
    List<Jogo> findByEstadoInAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraDesc(
        List<EstadoJogo> estados, java.time.LocalDateTime desde, java.time.LocalDateTime ate);

    List<Jogo> findByEstadoOrderByDataHoraAsc(EstadoJogo estado);

    boolean existsByCriadoPorOrCanceladoPor(pt.futapp.utilizador.Utilizador criadoPor,
                                            pt.futapp.utilizador.Utilizador canceladoPor);
}
