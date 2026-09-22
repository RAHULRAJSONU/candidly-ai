package ai.candidly.career.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateExperienceRepository extends JpaRepository<CandidateExperience, UUID> {

    List<CandidateExperience> findByCandidateId(UUID candidateId);
}
