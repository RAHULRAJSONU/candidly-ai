package ai.candidly.career.audit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditCheckpointRepository extends JpaRepository<AuditCheckpoint, UUID> {

    Optional<AuditCheckpoint> findTopByOrderByCreatedAtDesc();
}
