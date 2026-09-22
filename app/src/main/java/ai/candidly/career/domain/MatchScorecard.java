package ai.candidly.career.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Deterministic scoring output for one (candidate, job) pair (docs/03 §2). Location and
 * work authorization never appear here as a weighted term - hardEligibilityPassed is a
 * gate evaluated first; reasonCodes give the human-readable adverse-action explanation
 * required by docs/02 §4.4.
 */
@Entity
@Table(name = "match_scorecard", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = { "candidate_id", "job_posting_id" }))
public class MatchScorecard {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    private boolean hardEligibilityPassed;

    private double skillScore;
    private double experienceScore;
    private double semanticScore;
    private double domainScore;
    private double compositeScore;

    private boolean shortlisted;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "match_scorecard_reason", joinColumns = @jakarta.persistence.JoinColumn(name = "scorecard_id"))
    @Column(name = "reason", length = 500)
    @jakarta.persistence.OrderColumn(name = "reason_order")
    private List<String> reasonCodes;

    @Column(nullable = false)
    private Instant decidedAt;

    protected MatchScorecard() {
        // JPA
    }

    public MatchScorecard(Candidate candidate, JobPosting jobPosting, boolean hardEligibilityPassed,
            double skillScore, double experienceScore, double semanticScore, double domainScore,
            double compositeScore, boolean shortlisted, List<String> reasonCodes) {
        this.candidate = candidate;
        this.jobPosting = jobPosting;
        this.hardEligibilityPassed = hardEligibilityPassed;
        this.skillScore = skillScore;
        this.experienceScore = experienceScore;
        this.semanticScore = semanticScore;
        this.domainScore = domainScore;
        this.compositeScore = compositeScore;
        this.shortlisted = shortlisted;
        this.reasonCodes = reasonCodes;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public boolean isHardEligibilityPassed() {
        return hardEligibilityPassed;
    }

    public double getSkillScore() {
        return skillScore;
    }

    public double getExperienceScore() {
        return experienceScore;
    }

    public double getSemanticScore() {
        return semanticScore;
    }

    public double getDomainScore() {
        return domainScore;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public boolean isShortlisted() {
        return shortlisted;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
