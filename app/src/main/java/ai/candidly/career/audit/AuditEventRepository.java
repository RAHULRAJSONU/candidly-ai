package ai.candidly.career.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Optional<AuditEvent> findTopByOrderByOccurredAtDescIdDesc();

    List<AuditEvent> findAllByOrderByOccurredAtAscIdAsc();

    List<AuditEvent> findBySubjectIdOrderByOccurredAtAsc(UUID subjectId);

    List<AuditEvent> findByOccurredAtBeforeOrderByOccurredAtAscIdAsc(Instant cutoff);

    /** Backs the live pipeline feed (PipelineActivityService) - most recent first, capped by {@code pageable}. */
    List<AuditEvent> findByEventTypeInOrderByOccurredAtDesc(Collection<AuditEventType> eventTypes, Pageable pageable);
}
