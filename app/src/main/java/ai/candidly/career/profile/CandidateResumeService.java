package ai.candidly.career.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.CandidateResume;
import ai.candidly.career.domain.CandidateResumeRepository;
import ai.candidly.career.profileimport.ProfileExtractionService;
import ai.candidly.career.profileimport.ProfileImportResult;
import ai.candidly.career.profileimport.ResumeTextExtractor;
import ai.candidly.career.taxonomy.SkillNormalizationService;

/**
 * Stores/serves the candidate's uploaded resume file for the "Resume &amp; Profile" page.
 * Deliberately not a TypeSafe judgment or a docs/00-04 requirement - this is plain file
 * storage, distinct from {@code tailoring/}'s AI-generated per-job resume artifacts and
 * from {@code api/ProfileExtractionService}'s transient onboarding pre-fill extraction
 * (that endpoint never persists the file; this one's whole job is to persist it).
 *
 * <p>On upload, also best-effort back-fills {@link Candidate#getProfessionalSummary()} and
 * the candidate's {@link CandidateExperience} timeline via {@link ProfileExtractionService} -
 * but only for whichever of those is still empty (never overwrites an existing/edited
 * summary, and never touches experience rows once at least one exists - e.g. from
 * onboarding or a manual add in the Career Vault), and only with facts the extraction
 * prompt is instructed to derive strictly from the resume text (never fabricated - see
 * {@code ProfileExtractionService}'s SUMMARY/EXPERIENCE field instructions). The candidate
 * can still edit, delete, or add to either afterward from the Career Vault.
 */
@Service
public class CandidateResumeService {

    private static final Logger log = LoggerFactory.getLogger(CandidateResumeService.class);

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final CandidateRepository candidateRepository;
    private final CandidateResumeRepository resumeRepository;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ProfileExtractionService profileExtractionService;
    private final CandidateExperienceRepository experienceRepository;
    private final SkillNormalizationService skillNormalizationService;
    private final JinaEmbeddingClient embeddingClient;

    public CandidateResumeService(CandidateRepository candidateRepository, CandidateResumeRepository resumeRepository,
            ResumeTextExtractor resumeTextExtractor, ProfileExtractionService profileExtractionService,
            CandidateExperienceRepository experienceRepository, SkillNormalizationService skillNormalizationService,
            JinaEmbeddingClient embeddingClient) {
        this.candidateRepository = candidateRepository;
        this.resumeRepository = resumeRepository;
        this.resumeTextExtractor = resumeTextExtractor;
        this.profileExtractionService = profileExtractionService;
        this.experienceRepository = experienceRepository;
        this.skillNormalizationService = skillNormalizationService;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public ResumeMeta upload(UUID candidateId, MultipartFile file) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only PDF and Word documents are accepted");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String filename = Optional.ofNullable(file.getOriginalFilename()).filter(s -> !s.isBlank()).orElse("resume");
        Instant now = Instant.now();

        CandidateResume resume = resumeRepository.findByCandidateId(candidateId).orElse(null);
        if (resume == null) {
            resume = new CandidateResume(candidateId, filename, contentType, bytes, now);
            resumeRepository.save(resume);
        } else {
            resume.replace(filename, contentType, bytes, now);
        }

        boolean needsSummary = candidate.getProfessionalSummary() == null || candidate.getProfessionalSummary().isBlank();
        boolean needsExperience = experienceRepository.findByCandidateId(candidateId).isEmpty();
        if (needsSummary || needsExperience) {
            backfillFromResume(candidate, file, needsSummary, needsExperience);
        }

        return toMeta(resume);
    }

    /** Best-effort only - a failed extraction just leaves the summary/experience section
     * blank for the candidate to fill in manually, same fallback behavior as onboarding's
     * import. Only fills whichever of the two is actually still empty, so a resume
     * re-upload never overwrites a summary or experience rows the candidate already has. */
    private void backfillFromResume(Candidate candidate, MultipartFile file, boolean needsSummary, boolean needsExperience) {
        try {
            String text = resumeTextExtractor.extract(file);
            ProfileImportResult result = profileExtractionService.extractFromResume(text);
            if (needsSummary && result.professionalSummary() != null && !result.professionalSummary().isBlank()) {
                candidate.setProfessionalSummary(result.professionalSummary());
            }
            if (needsExperience) {
                for (ProfileImportResult.ExperienceDraft draft : result.experiences()) {
                    Set<String> skillIds = normalizeAll(draft.rawSkillMentions());
                    String narrative = draft.narrative() == null ? "" : draft.narrative();
                    CandidateExperience experience = new CandidateExperience(candidate, draft.employer(), draft.title(),
                            draft.startDate(), draft.endDate(), narrative, Set.of(), skillIds,
                            draft.rawSkillMentions() == null ? Set.of() : draft.rawSkillMentions());
                    String embedText = narrative.isBlank() ? draft.title() + " at " + draft.employer() : narrative;
                    float[] embedding = embeddingClient.embed(embedText);
                    experience.assignEmbedding(embedding, JinaEmbeddingClient.MODEL_ID, 1);
                    experienceRepository.save(experience);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not back-fill profile data from the uploaded resume for candidate {}",
                    candidate.getId(), e);
        }
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

    @Transactional(readOnly = true)
    public Optional<ResumeMeta> getMeta(UUID candidateId) {
        return resumeRepository.findByCandidateId(candidateId).map(CandidateResumeService::toMeta);
    }

    @Transactional(readOnly = true)
    public Optional<CandidateResume> get(UUID candidateId) {
        return resumeRepository.findByCandidateId(candidateId);
    }

    @Transactional
    public void delete(UUID candidateId) {
        resumeRepository.deleteByCandidateId(candidateId);
    }

    private static ResumeMeta toMeta(CandidateResume resume) {
        return new ResumeMeta(resume.getFilename(), resume.getContentType(), resume.getSizeBytes(), resume.getUploadedAt());
    }
}
