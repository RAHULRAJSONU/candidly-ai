package ai.candidly.career.demographics;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateDemographicsRepository extends JpaRepository<CandidateDemographics, UUID> {

    Optional<CandidateDemographics> findByCandidateId(UUID candidateId);

    void deleteByCandidateId(UUID candidateId);
}
