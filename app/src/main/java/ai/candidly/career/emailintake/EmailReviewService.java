package ai.candidly.career.emailintake;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;

/**
 * The mock's separate "Human-in-the-Loop" screen for the agent's outgoing email
 * replies - Approve & Send / Edit & Send / Schedule to Send / Cancel. None of these
 * actually deliver an email (no SMTP/Gmail-send integration exists in this codebase);
 * each action just records a human decision on the draft, same as {@code
 * TailoringReviewService.approve} records a decision without submitting anywhere.
 */
@Service
public class EmailReviewService {

    private final EmailIntakeRecordRepository recordRepository;
    private final AuditLedgerService auditLedgerService;

    public EmailReviewService(EmailIntakeRecordRepository recordRepository, AuditLedgerService auditLedgerService) {
        this.recordRepository = recordRepository;
        this.auditLedgerService = auditLedgerService;
    }

    public List<EmailIntakeRecord> needsReview(UUID candidateId) {
        return recordRepository.findByCandidateIdAndReviewStatusOrderByClassifiedAtDesc(candidateId, EmailReviewStatus.NEEDS_REVIEW);
    }

    public List<EmailIntakeRecord> history(UUID candidateId) {
        return recordRepository.findByCandidateIdOrderByClassifiedAtDesc(candidateId);
    }

    public List<EmailIntakeRecord> thread(UUID candidateId, UUID jobPostingId) {
        return recordRepository.findByCandidateIdAndJobPostingIdOrderByClassifiedAtDesc(candidateId, jobPostingId);
    }

    @Transactional
    public EmailIntakeRecord approveSend(UUID recordId, String note) {
        EmailIntakeRecord record = get(recordId);
        record.approveSend(note);
        return afterDecision(record, "APPROVED_SEND", note);
    }

    @Transactional
    public EmailIntakeRecord editAndSend(UUID recordId, String editedText, String note) {
        EmailIntakeRecord record = get(recordId);
        record.editAndSend(editedText, note);
        return afterDecision(record, "EDITED_SEND", note);
    }

    @Transactional
    public EmailIntakeRecord schedule(UUID recordId, String note) {
        EmailIntakeRecord record = get(recordId);
        record.schedule(note);
        return afterDecision(record, "SCHEDULED", note);
    }

    @Transactional
    public EmailIntakeRecord suggestAlternative(UUID recordId, String alternativeText, String note) {
        EmailIntakeRecord record = get(recordId);
        record.suggestAlternative(alternativeText, note);
        return afterDecision(record, "ALTERNATIVE_SUGGESTED", note);
    }

    @Transactional
    public EmailIntakeRecord cancel(UUID recordId, String note) {
        EmailIntakeRecord record = get(recordId);
        record.cancel(note);
        return afterDecision(record, "CANCELLED", note);
    }

    private EmailIntakeRecord afterDecision(EmailIntakeRecord record, String decision, String note) {
        EmailIntakeRecord saved = recordRepository.save(record);
        auditLedgerService.record(AuditEventType.EMAIL_REPLY_REVIEWED, saved.getCandidate().getId(),
                "record=%s decision=%s note=%s".formatted(saved.getId(), decision, note));
        return saved;
    }

    private EmailIntakeRecord get(UUID recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Email intake record not found: " + recordId));
    }
}
