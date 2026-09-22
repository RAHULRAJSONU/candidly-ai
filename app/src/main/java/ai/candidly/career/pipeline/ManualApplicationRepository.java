package ai.candidly.career.pipeline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualApplicationRepository extends JpaRepository<ManualApplication, UUID> {

    List<ManualApplication> findByCandidateIdOrderByCreatedAtDesc(UUID candidateId);
}
