package ai.candidly.career.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationRepository extends JpaRepository<Education, UUID> {

    List<Education> findByCandidateId(UUID candidateId);
}
