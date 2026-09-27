package ai.candidly.career.aiops;

import java.util.List;

/**
 * Explainable "which sources/domains/skills actually lead to approvals and interviews"
 * breakdown (docs/03 §3's recalibration goal, plan Phase 3) - three independent
 * groupings over data already in this database (job_posting, match_scorecard,
 * tailored_artifact, interview), each a plain rate a human reads and acts on by hand
 * (e.g. adjusting {@code candidly.matching.weights.*} or a candidate's tracked-company
 * list) - never an input that feeds back into scoring/ranking automatically. Consistent
 * with this repo's LL144/explainability posture (see {@code BiasAuditReportService}):
 * no opaque auto-tuning.
 */
public record InsightsSummary(
        List<SourceInsight> bySource,
        List<DomainInsight> byDomain,
        List<SkillInsight> byMandatorySkill) {

    /** {@code approvalRate}/{@code interviewRate} are {@code null} when the denominator is
     * zero (nothing reviewed/shortlisted yet for this source), not zero - a real "no data"
     * distinct from a real "0%". */
    public record SourceInsight(String source, long discovered, long shortlisted, long tailored, long approved,
            long rejected, long interviews, long offers, Double approvalRate, Double interviewRate) {
    }

    public record DomainInsight(String domain, long discovered, long shortlisted, long approved, long rejected,
            long interviews, Double shortlistRate, Double approvalRate) {
    }

    public record SkillInsight(String skillId, String skillLabel, long postingsRequiring, long shortlisted,
            long approved, long rejected, Double shortlistRate, Double approvalRate) {
    }
}
