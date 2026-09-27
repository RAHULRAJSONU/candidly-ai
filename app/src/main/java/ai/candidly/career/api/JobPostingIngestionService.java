package ai.candidly.career.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.CurrencyCodes;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.JobPostingSource;
import ai.candidly.career.domain.ScreeningDecision;
import ai.candidly.career.screening.JobPostingScreeningService;
import ai.candidly.career.screening.ScreeningResult;
import ai.candidly.career.taxonomy.SkillNormalizationService;

/**
 * FR-1/FR-2 discovery+ingestion, simplified: this app receives an already-fetched
 * posting rather than polling Greenhouse/Lever/Ashby/Workday itself. The parts that
 * matter for this slice - dedupe hashing (docs/01 §3.1), the fuzzy re-post/cross-post
 * catch {@link FuzzyDuplicateJobPostingService} adds on top of it, and the mandatory
 * prompt-injection screen (docs/02 §1, C-1) before the posting can ever reach matching
 * or tailoring - are all here.
 */
@Service
public class JobPostingIngestionService {

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingScreeningService screeningService;
    private final SkillNormalizationService skillNormalizationService;
    private final JinaEmbeddingClient embeddingClient;
    private final AuditLedgerService auditLedgerService;
    private final FuzzyDuplicateJobPostingService fuzzyDuplicateService;
    private final JobPostingExtractionService extractionService;
    private final boolean extractionEnabled;

    public JobPostingIngestionService(JobPostingRepository jobPostingRepository,
            JobPostingScreeningService screeningService,
            SkillNormalizationService skillNormalizationService,
            JinaEmbeddingClient embeddingClient,
            AuditLedgerService auditLedgerService,
            FuzzyDuplicateJobPostingService fuzzyDuplicateService,
            JobPostingExtractionService extractionService,
            @Value("${candidly.extraction.enabled:true}") boolean extractionEnabled) {
        this.jobPostingRepository = jobPostingRepository;
        this.screeningService = screeningService;
        this.skillNormalizationService = skillNormalizationService;
        this.embeddingClient = embeddingClient;
        this.auditLedgerService = auditLedgerService;
        this.fuzzyDuplicateService = fuzzyDuplicateService;
        this.extractionService = extractionService;
        this.extractionEnabled = extractionEnabled;
    }

    @Transactional
    public JobPosting ingest(JobPostingRequest request) {
        // Validated before the fuzzy-dedupe/screening steps below, which make billed TypeSafe calls.
        String currency = request.currency() == null ? null : CurrencyCodes.normalize(request.currency(), "currency");
        String dedupeHash = sha256(String.join("|", request.company(), request.title(), request.location(), request.sourceId()));
        var existing = jobPostingRepository.findByDedupeHash(dedupeHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        var fuzzyDuplicate = fuzzyDuplicateService.findDuplicate(request);
        if (fuzzyDuplicate.isPresent()) {
            return fuzzyDuplicate.get();
        }

        JobPosting job = new JobPosting(dedupeHash, request.company(), request.title(), request.location(),
                request.remote(), request.compMinMinorUnits(), request.compMaxMinorUnits(),
                request.acceptedWorkAuthorizations() == null ? Set.of() : request.acceptedWorkAuthorizations(),
                normalizeAll(request.mandatorySkillMentions()), normalizeAll(request.preferredSkillMentions()),
                request.domain(), request.minYearsExperience(), request.rawDescription());
        job.assignSource(sourceFromSourceId(request.sourceId()));
        job.assignCurrency(currency);

        ScreeningResult screening = screeningService.screen(request.rawDescription());
        job.applyScreening(screening.decision(), screening.humanReadableReason());

        boolean extracted = false;
        boolean embedded = false;
        if (screening.decision() != ScreeningDecision.BLOCK) {
            if (extractionEnabled && needsExtraction(job)) {
                JobPostingExtractionService.ExtractedAttributes attributes = extractionService.extract(request.rawDescription());
                int minYearsExperience = ExperienceYearsExtractor.extract(request.rawDescription());
                job.applyExtraction(attributes.mandatorySkillIds(), attributes.preferredSkillIds(),
                        attributes.domain(), minYearsExperience);
                extracted = true;
            }
            // Embedding is non-generative (it cannot execute an instruction found in the
            // text), so it's safe to run even on a REVIEW posting; only BLOCK withholds it.
            float[] embedding = embeddingClient.embed(request.rawDescription());
            job.assignEmbedding(embedding, JinaEmbeddingClient.MODEL_ID, 1);
            embedded = true;
        }

        JobPosting saved = jobPostingRepository.save(job);
        // Recorded in pipeline order (not code-execution order, which computes extraction/
        // embedding before the row has an id) - the live pipeline feed (GET /api/audit/
        // pipeline-feed) relies on this sequence to animate a posting moving through
        // stages, and every one of these shares the same subjectId (the job posting's own
        // id, unlike the candidate-scoped events further downstream).
        auditLedgerService.record(AuditEventType.JOB_POSTING_DISCOVERED, saved.getId(),
                "company=%s title=%s source=%s".formatted(saved.getCompany(), saved.getTitle(), saved.getSource()));
        auditLedgerService.record(AuditEventType.JOB_POSTING_SCREENED, saved.getId(), screening.humanReadableReason());
        if (extracted) {
            auditLedgerService.record(AuditEventType.JOB_POSTING_EXTRACTED, saved.getId(),
                    "mandatorySkills=%s preferredSkills=%s domain=%s minYearsExperience=%d".formatted(
                            saved.getMandatorySkillIds(), saved.getPreferredSkillIds(), saved.getDomain(),
                            saved.getMinYearsExperience()));
        }
        if (embedded) {
            auditLedgerService.record(AuditEventType.JOB_POSTING_EMBEDDED, saved.getId(),
                    "model=%s".formatted(saved.getEmbeddingModel()));
        }
        return saved;
    }

    /** {@code sourceId} is namespaced by its originating adapter (e.g. "greenhouse:gitlab:123",
     * "lever:palantir:abc", "ashby:ashby:uuid", "workday:tenant:site:/job/..." - see
     * DiscoveryScheduler.toRequest and the four adapters); anything else (a hand-submitted
     * posting, or a test fixture's arbitrary id) is MANUAL. */
    private JobPostingSource sourceFromSourceId(String sourceId) {
        if (sourceId.startsWith("greenhouse:")) {
            return JobPostingSource.GREENHOUSE;
        }
        if (sourceId.startsWith("lever:")) {
            return JobPostingSource.LEVER;
        }
        if (sourceId.startsWith("ashby:")) {
            return JobPostingSource.ASHBY;
        }
        if (sourceId.startsWith("workday:")) {
            return JobPostingSource.WORKDAY;
        }
        if (sourceId.startsWith("jsonld:")) {
            return JobPostingSource.JSONLD;
        }
        return JobPostingSource.MANUAL;
    }

    /** True only when the submitter/adapter left every extractable field empty - a MANUAL
     * posting that already states its own skills/domain/years is never overridden. */
    private boolean needsExtraction(JobPosting job) {
        return job.getMandatorySkillIds().isEmpty() && job.getPreferredSkillIds().isEmpty()
                && (job.getDomain() == null || job.getDomain().isBlank())
                && job.getMinYearsExperience() <= 0;
    }

    private Set<String> normalizeAll(Set<String> rawMentions) {
        Set<String> normalized = new HashSet<>();
        if (rawMentions == null) {
            return normalized;
        }
        for (String mention : rawMentions) {
            skillNormalizationService.normalize(mention).ifPresent(normalized::add);
        }
        return normalized;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
