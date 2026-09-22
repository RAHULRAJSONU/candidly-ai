package ai.candidly.career.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findTopByOrderByOccurredAtDescIdDesc();

    List<AuditEvent> findAllByOrderByOccurredAtAscIdAsc();

    List<AuditEvent> findBySubjectIdOrderByOccurredAtAsc(UUID subjectId);

    List<AuditEvent> findByOccurredAtBeforeOrderByOccurredAtAscIdAsc(Instant cutoff);
}
