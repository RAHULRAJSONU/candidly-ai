package ai.candidly.career.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    List<Achievement> findByCandidateId(UUID candidateId);
}
