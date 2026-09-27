package ai.candidly.career.emailintake;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailIntakeRecordRepository extends JpaRepository<EmailIntakeRecord, UUID> {

    List<EmailIntakeRecord> findByCandidateIdOrderByClassifiedAtDesc(UUID candidateId);

    List<EmailIntakeRecord> findByCandidateIdAndReviewStatusOrderByClassifiedAtDesc(UUID candidateId, EmailReviewStatus status);

    /** Every classified email tied to the same job posting - backs the mock's "Previous Emails" thread panel. */
    List<EmailIntakeRecord> findByCandidateIdAndJobPostingIdOrderByClassifiedAtDesc(UUID candidateId, UUID jobPostingId);
}
