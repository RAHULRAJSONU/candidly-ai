package ai.candidly.career.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TailoringJobRepository extends JpaRepository<TailoringJob, UUID> {

    Optional<TailoringJob> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);

    List<TailoringJob> findByCandidateId(UUID candidateId);
}
