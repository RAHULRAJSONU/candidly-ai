package ai.candidly.career.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TailoredArtifactRepository extends JpaRepository<TailoredArtifact, UUID> {

    List<TailoredArtifact> findByStatusInOrderByGeneratedAtAsc(List<TailoredArtifactStatus> statuses);

    List<TailoredArtifact> findByStatusInOrderByReviewedAtDesc(List<TailoredArtifactStatus> statuses);

    List<TailoredArtifact> findByCandidateId(UUID candidateId);
}
