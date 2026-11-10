package pt.futapp.jogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JogoRepository extends JpaRepository<Jogo, Long> {

    List<Jogo> findByEstadoNotOrderByDataHoraAsc(EstadoJogo estado);

    List<Jogo> findTop10ByEstadoOrderByDataHoraDesc(EstadoJogo estado);
}
