package ai.candidly.career.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchScorecardRepository extends JpaRepository<MatchScorecard, UUID> {

    Optional<MatchScorecard> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);

    List<MatchScorecard> findByCandidateId(UUID candidateId);
}
