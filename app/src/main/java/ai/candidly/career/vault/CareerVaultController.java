package ai.candidly.career.vault;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates/{candidateId}/vault")
public class CareerVaultController {

    private final CareerVaultService careerVaultService;
    private final CandidateRepository candidateRepository;
    private final AchievementRepository achievementRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;

    public CareerVaultController(CareerVaultService careerVaultService, CandidateRepository candidateRepository,
            AchievementRepository achievementRepository, EducationRepository educationRepository,
            CertificationRepository certificationRepository) {
        this.careerVaultService = careerVaultService;
        this.candidateRepository = candidateRepository;
        this.achievementRepository = achievementRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
    }

    @GetMapping
    public CareerVaultService.CareerVaultView get(@PathVariable UUID candidateId) {
        try {
            return careerVaultService.get(candidateId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
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

    private Candidate requireCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }
}
