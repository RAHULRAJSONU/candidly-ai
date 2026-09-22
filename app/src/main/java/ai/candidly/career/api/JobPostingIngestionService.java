package ai.candidly.career.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.audit.AuditEventType;
import ai.candidly.career.audit.AuditLedgerService;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
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

    public JobPostingIngestionService(JobPostingRepository jobPostingRepository,
            JobPostingScreeningService screeningService,
            SkillNormalizationService skillNormalizationService,
            JinaEmbeddingClient embeddingClient,
            AuditLedgerService auditLedgerService,
            FuzzyDuplicateJobPostingService fuzzyDuplicateService) {
        this.jobPostingRepository = jobPostingRepository;
        this.screeningService = screeningService;
        this.skillNormalizationService = skillNormalizationService;
        this.embeddingClient = embeddingClient;
        this.auditLedgerService = auditLedgerService;
        this.fuzzyDuplicateService = fuzzyDuplicateService;
    }

    @Transactional
    public JobPosting ingest(JobPostingRequest request) {
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

        ScreeningResult screening = screeningService.screen(request.rawDescription());
        job.applyScreening(screening.decision(), screening.humanReadableReason());

        if (screening.decision() != ScreeningDecision.BLOCK) {
            // Embedding is non-generative (it cannot execute an instruction found in the
            // text), so it's safe to run even on a REVIEW posting; only BLOCK withholds it.
            float[] embedding = embeddingClient.embed(request.rawDescription());
            job.assignEmbedding(embedding, JinaEmbeddingClient.MODEL_ID, 1);
        }

        JobPosting saved = jobPostingRepository.save(job);
        auditLedgerService.record(AuditEventType.JOB_POSTING_SCREENED, saved.getId(), screening.humanReadableReason());
        return saved;
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
