package ai.candidly.career.vault;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateSkillProfileRepository extends JpaRepository<CandidateSkillProfile, UUID> {

    List<CandidateSkillProfile> findByCandidateId(UUID candidateId);

    Optional<CandidateSkillProfile> findByCandidateIdAndSkillNameIgnoreCase(UUID candidateId, String skillName);

    void deleteByCandidateId(UUID candidateId);
}
