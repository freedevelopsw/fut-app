package pt.futapp.votacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PremioRepository extends JpaRepository<Premio, Long> {

    List<Premio> findAllByOrderByOrdemAscIdAsc();

    List<Premio> findByAtivoTrueOrderByOrdemAscIdAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
