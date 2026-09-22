package ai.candidly.career.emailintake;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailIntakeRecordRepository extends JpaRepository<EmailIntakeRecord, UUID> {

    List<EmailIntakeRecord> findByCandidateIdOrderByClassifiedAtDesc(UUID candidateId);

    List<EmailIntakeRecord> findByCandidateIdAndReviewStatusOrderByClassifiedAtDesc(UUID candidateId, EmailReviewStatus status);
}
