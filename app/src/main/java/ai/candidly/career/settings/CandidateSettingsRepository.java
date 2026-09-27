package ai.candidly.career.settings;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateSettingsRepository extends JpaRepository<CandidateSettings, UUID> {

    Optional<CandidateSettings> findByCandidateId(UUID candidateId);
}
