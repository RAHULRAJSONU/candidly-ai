package ai.candidly.career.autopilot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutopilotSettingsRepository extends JpaRepository<AutopilotSettings, UUID> {

    Optional<AutopilotSettings> findByCandidateId(UUID candidateId);

    List<AutopilotSettings> findByStatus(AutopilotStatus status);
}
