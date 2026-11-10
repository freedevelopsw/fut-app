package pt.futapp.jogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByEstadoNotInOrderByDataHoraAsc(List<EstadoJogo> estados);

    List<Jogo> findTop10ByEstadoInOrderByDataHoraDesc(List<EstadoJogo> estados);

    List<Jogo> findByEstadoOrderByDataHoraAsc(EstadoJogo estado);
}
