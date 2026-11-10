package pt.futapp.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuditEventoRepository extends JpaRepository<AuditEvento, Long> {

    List<AuditEvento> findTop200ByOrderByDataHoraDescIdDesc();

    List<AuditEvento> findTop200ByJogoIdOrderByDataHoraDescIdDesc(Long jogoId);

    List<AuditEvento> findTop200ByAtorIgnoreCaseOrderByDataHoraDescIdDesc(String ator);

    @Modifying
    @Transactional
    @Query("delete from AuditEvento a where a.dataHora < :limite")
    int apagarAnterioresA(@Param("limite") LocalDateTime limite);
}
