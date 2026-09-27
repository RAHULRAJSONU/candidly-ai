package ai.candidly.career.api;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.CurrencyCodes;
import ai.candidly.career.taxonomy.SkillNormalizationService;

/**
 * FR-2 profile ingestion (docs/04), simplified: this app receives already-parsed
 * structured JSON rather than parsing PDF/DOCX itself. Raw skill mentions are
 * normalized to taxonomy ids via SkillNormalizationService (embedding shortlist +
 * TypeSafe Choice disambiguation) rather than trusted as-is.
 */
@Service
public class CandidateIngestionService {

    private final CandidateRepository candidateRepository;
    private final CandidateExperienceRepository experienceRepository;
    private final SkillNormalizationService skillNormalizationService;
    private final JinaEmbeddingClient embeddingClient;

    public CandidateIngestionService(CandidateRepository candidateRepository,
            CandidateExperienceRepository experienceRepository,
            SkillNormalizationService skillNormalizationService,
            JinaEmbeddingClient embeddingClient) {
        this.candidateRepository = candidateRepository;
        this.experienceRepository = experienceRepository;
        this.skillNormalizationService = skillNormalizationService;
        this.embeddingClient = embeddingClient;
    }

    @Transactional
    public Candidate ingest(CandidateRequest request) {
        // Validated before normalizeAll, which makes billed embedding/TypeSafe calls.
        String currency = request.preferredCurrency() == null ? null
                : CurrencyCodes.normalize(request.preferredCurrency(), "preferredCurrency");
        Set<String> candidateSkillIds = normalizeAll(request.rawSkillMentions());
        Candidate newCandidate = new Candidate(
                request.fullName(), request.email(), request.location(),
                request.workAuthorizations(), request.compFloorMinorUnits(), candidateSkillIds,
                request.rawSkillMentions() == null ? Set.of() : request.rawSkillMentions());
        newCandidate.setPreferredCurrency(currency);
        Candidate candidate = candidateRepository.save(newCandidate);

        for (CandidateRequest.ExperienceRequest expRequest : request.experiences()) {
            Set<String> experienceSkillIds = normalizeAll(expRequest.rawSkillMentions());
            CandidateExperience experience = new CandidateExperience(candidate, expRequest.employer(),
                    expRequest.title(), expRequest.startDate(), expRequest.endDate(), expRequest.narrative(),
                    expRequest.verifiedMetrics() == null ? Set.of() : expRequest.verifiedMetrics(), experienceSkillIds,
                    expRequest.rawSkillMentions() == null ? Set.of() : expRequest.rawSkillMentions());
            float[] embedding = embeddingClient.embed(expRequest.narrative());
            experience.assignEmbedding(embedding, JinaEmbeddingClient.MODEL_ID, 1);
            experienceRepository.save(experience);
        }
        return candidate;
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
}
