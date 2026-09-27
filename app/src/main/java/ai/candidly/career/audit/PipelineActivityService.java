package ai.candidly.career.audit;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;

/**
 * Turns the raw audit ledger into a live, per-posting pipeline feed for the frontend's
 * animated stage rail (docs' "animated visual pipeline" ask) - no new infra, no
 * WebSocket/SSE (see standing no-Kafka/Docker/K8s/Redis instruction and this repo's
 * existing polling-only precedent in Autopilot.tsx): the frontend just polls
 * {@code GET /api/audit/pipeline-feed} every few seconds.
 *
 * <p>{@link ai.candidly.career.api.JobPostingIngestionService} writes {@code
 * JOB_POSTING_DISCOVERED}/{@code _SCREENED}/{@code _EXTRACTED}/{@code _EMBEDDED} with the
 * job posting's own id as {@code subjectId}. Downstream events ({@code MATCH_SCORED},
 * {@code TAILORED_ARTIFACT_GENERATED}, {@code TAILORED_ARTIFACT_REVIEWED}) are recorded
 * against the candidate's id instead (pre-existing convention, unchanged here), so this
 * service recovers the job posting id from each one's {@code details} text - every one of
 * them already writes {@code job=<uuid>} for a human reading the raw ledger; this just
 * parses the same marker programmatically, the same way {@code AutopilotService} already
 * parses its own {@code job=} markers to detect "already queued/followed-up".
 */
@Service
public class PipelineActivityService {

    private static final Set<AuditEventType> PIPELINE_EVENT_TYPES = Set.of(
            AuditEventType.JOB_POSTING_DISCOVERED, AuditEventType.JOB_POSTING_SCREENED,
            AuditEventType.JOB_POSTING_EXTRACTED, AuditEventType.JOB_POSTING_EMBEDDED,
            AuditEventType.MATCH_SCORED, AuditEventType.TAILORED_ARTIFACT_GENERATED,
            AuditEventType.TAILORED_ARTIFACT_REVIEWED);

    private static final Set<AuditEventType> JOB_SUBJECT_EVENT_TYPES = Set.of(
            AuditEventType.JOB_POSTING_DISCOVERED, AuditEventType.JOB_POSTING_SCREENED,
            AuditEventType.JOB_POSTING_EXTRACTED, AuditEventType.JOB_POSTING_EMBEDDED);

    private static final Pattern JOB_ID_PATTERN =
            Pattern.compile("job=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private static final Map<AuditEventType, String> BASE_STAGE = new EnumMap<>(AuditEventType.class);

    static {
        BASE_STAGE.put(AuditEventType.JOB_POSTING_DISCOVERED, "DISCOVERED");
        BASE_STAGE.put(AuditEventType.JOB_POSTING_SCREENED, "SCREENED");
        BASE_STAGE.put(AuditEventType.JOB_POSTING_EXTRACTED, "EXTRACTED");
        BASE_STAGE.put(AuditEventType.JOB_POSTING_EMBEDDED, "EMBEDDED");
        BASE_STAGE.put(AuditEventType.MATCH_SCORED, "SCORED");
        BASE_STAGE.put(AuditEventType.TAILORED_ARTIFACT_GENERATED, "TAILORING");
        BASE_STAGE.put(AuditEventType.TAILORED_ARTIFACT_REVIEWED, "REVIEWED");
    }

    private final AuditEventRepository auditEventRepository;
    private final JobPostingRepository jobPostingRepository;

    public PipelineActivityService(AuditEventRepository auditEventRepository, JobPostingRepository jobPostingRepository) {
        this.auditEventRepository = auditEventRepository;
        this.jobPostingRepository = jobPostingRepository;
    }

    /** Most recent {@code limit} pipeline-relevant events, newest first, enriched with the
     * job posting's title/company where it can still be resolved (never fails the call if
     * a posting was since GDPR-erased or otherwise deleted - the event just carries nulls). */
    public List<PipelineActivityItem> liveFeed(int limit) {
        List<AuditEvent> events = auditEventRepository.findByEventTypeInOrderByOccurredAtDesc(
                PIPELINE_EVENT_TYPES, PageRequest.of(0, limit));
        return events.stream().map(this::toItem).toList();
    }

    private PipelineActivityItem toItem(AuditEvent event) {
        boolean jobIsSubject = JOB_SUBJECT_EVENT_TYPES.contains(event.getEventType());
        UUID jobPostingId = jobIsSubject ? event.getSubjectId() : extractJobId(event.getDetails());
        UUID candidateId = jobIsSubject ? null : event.getSubjectId();
        JobPosting job = jobPostingId == null ? null : jobPostingRepository.findById(jobPostingId).orElse(null);

        return new PipelineActivityItem(event.getId(), event.getEventType(), stageFor(event), event.getOccurredAt(),
                jobPostingId, job == null ? null : job.getTitle(), job == null ? null : job.getCompany(),
                candidateId, event.getDetails());
    }

    private UUID extractJobId(String details) {
        Matcher matcher = JOB_ID_PATTERN.matcher(details);
        return matcher.find() ? UUID.fromString(matcher.group(1)) : null;
    }

    private String stageFor(AuditEvent event) {
        String details = event.getDetails();
        if (event.getEventType() == AuditEventType.MATCH_SCORED) {
            return details.contains("shortlisted=true") ? "SHORTLISTED" : "SCORED";
        }
        if (event.getEventType() == AuditEventType.TAILORED_ARTIFACT_REVIEWED) {
            return details.contains("decision=APPROVED") ? "APPROVED" : "REJECTED";
        }
        return BASE_STAGE.get(event.getEventType());
    }

    public record PipelineActivityItem(UUID id, AuditEventType eventType, String stage, Instant occurredAt,
            UUID jobPostingId, String jobTitle, String company, UUID candidateId, String detail) {
    }
}
