package ai.candidly.career.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateResumeRepository extends JpaRepository<CandidateResume, UUID> {

    Optional<CandidateResume> findByCandidateId(UUID candidateId);

    void deleteByCandidateId(UUID candidateId);
}
