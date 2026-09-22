package ai.candidly.career.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    List<Certification> findByCandidateId(UUID candidateId);
}
