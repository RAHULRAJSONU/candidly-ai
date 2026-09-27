package ai.candidly.career.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidatePhotoRepository extends JpaRepository<CandidatePhoto, UUID> {

    Optional<CandidatePhoto> findByCandidateId(UUID candidateId);

    void deleteByCandidateId(UUID candidateId);
}
