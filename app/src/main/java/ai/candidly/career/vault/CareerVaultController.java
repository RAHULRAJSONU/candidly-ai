package ai.candidly.career.vault;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.ai.JinaEmbeddingClient;
import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.taxonomy.SkillNormalizationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates/{candidateId}/vault")
public class CareerVaultController {

    private final CareerVaultService careerVaultService;
    private final CandidateRepository candidateRepository;
    private final CandidateExperienceRepository experienceRepository;
    private final AchievementRepository achievementRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;
    private final ProjectRepository projectRepository;
    private final SkillNormalizationService skillNormalizationService;
    private final JinaEmbeddingClient embeddingClient;

    public CareerVaultController(CareerVaultService careerVaultService, CandidateRepository candidateRepository,
            CandidateExperienceRepository experienceRepository, AchievementRepository achievementRepository,
            EducationRepository educationRepository, CertificationRepository certificationRepository,
            ProjectRepository projectRepository, SkillNormalizationService skillNormalizationService,
            JinaEmbeddingClient embeddingClient) {
        this.careerVaultService = careerVaultService;
        this.candidateRepository = candidateRepository;
        this.experienceRepository = experienceRepository;
        this.achievementRepository = achievementRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.projectRepository = projectRepository;
        this.skillNormalizationService = skillNormalizationService;
        this.embeddingClient = embeddingClient;
    }

    @GetMapping
    public CareerVaultService.CareerVaultView get(@PathVariable UUID candidateId) {
        try {
            return careerVaultService.get(candidateId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }

    @PostMapping("/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateExperience addExperience(@PathVariable UUID candidateId, @Valid @RequestBody ExperienceRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        Set<String> skillIds = normalizeAll(request.rawSkillMentions());
        String narrative = request.narrative() == null ? "" : request.narrative();
        CandidateExperience experience = new CandidateExperience(candidate, request.employer(), request.title(),
                request.startDate(), request.endDate(), narrative,
                request.verifiedMetrics() == null ? Set.of() : request.verifiedMetrics(), skillIds,
                request.rawSkillMentions() == null ? Set.of() : request.rawSkillMentions());
        String embedText = narrative.isBlank() ? request.title() + " at " + request.employer() : narrative;
        float[] embedding = embeddingClient.embed(embedText);
        experience.assignEmbedding(embedding, JinaEmbeddingClient.MODEL_ID, 1);
        return experienceRepository.save(experience);
    }

    @DeleteMapping("/experiences/{experienceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@PathVariable UUID candidateId, @PathVariable UUID experienceId) {
        requireCandidate(candidateId);
        CandidateExperience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Experience not found"));
        if (!experience.getCandidate().getId().equals(candidateId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Experience not found");
        }
        experienceRepository.delete(experience);
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

    @PostMapping("/achievements")
    @ResponseStatus(HttpStatus.CREATED)
    public Achievement addAchievement(@PathVariable UUID candidateId, @Valid @RequestBody AchievementRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        return achievementRepository.save(new Achievement(candidate, request.title(), request.description(),
                request.occurredOn(), request.tags() == null ? java.util.Set.of() : request.tags()));
    }

    @PostMapping("/education")
    @ResponseStatus(HttpStatus.CREATED)
    public Education addEducation(@PathVariable UUID candidateId, @Valid @RequestBody EducationRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        return educationRepository.save(new Education(candidate, request.institution(), request.degree(),
                request.fieldOfStudy(), request.startDate(), request.endDate()));
    }

    @PostMapping("/certifications")
    @ResponseStatus(HttpStatus.CREATED)
    public Certification addCertification(@PathVariable UUID candidateId, @Valid @RequestBody CertificationRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        return certificationRepository.save(new Certification(candidate, request.name(), request.issuer(),
                request.issuedOn(), request.credentialId()));
    }

    @PutMapping("/skills")
    public CandidateSkillProfile upsertSkillProfile(@PathVariable UUID candidateId, @Valid @RequestBody SkillProfileRequest request) {
        try {
            return careerVaultService.upsertSkillProfile(candidateId, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public Project addProject(@PathVariable UUID candidateId, @Valid @RequestBody ProjectRequest request) {
        Candidate candidate = requireCandidate(candidateId);
        return projectRepository.save(new Project(candidate, request.title(), request.description(), request.url(),
                request.startDate(), request.endDate(), request.technologies()));
    }

    private Candidate requireCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }
}
