package ai.candidly.career.vault;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.CandidateExperienceRepository;
import ai.candidly.career.domain.CandidateRepository;

/**
 * Aggregates a candidate's Career Vault (docs/01 §2: "verified professional record")
 * across experience, achievements, education, and certifications, plus a simple
 * completeness score. "Verified" in the docs/01 sense means grounded against these
 * exact records elsewhere (see {@code DeterministicGroundingVerifier}) - this service
 * itself does no verification, it just aggregates what's on file.
 */
@Service
public class CareerVaultService {

    private final CandidateRepository candidateRepository;
    private final CandidateExperienceRepository experienceRepository;
    private final AchievementRepository achievementRepository;
    private final EducationRepository educationRepository;
    private final CertificationRepository certificationRepository;
    private final ProjectRepository projectRepository;
    private final CandidateSkillProfileRepository skillProfileRepository;

    public CareerVaultService(CandidateRepository candidateRepository, CandidateExperienceRepository experienceRepository,
            AchievementRepository achievementRepository, EducationRepository educationRepository,
            CertificationRepository certificationRepository, ProjectRepository projectRepository,
            CandidateSkillProfileRepository skillProfileRepository) {
        this.candidateRepository = candidateRepository;
        this.experienceRepository = experienceRepository;
        this.achievementRepository = achievementRepository;
        this.educationRepository = educationRepository;
        this.certificationRepository = certificationRepository;
        this.projectRepository = projectRepository;
        this.skillProfileRepository = skillProfileRepository;
    }

    public CareerVaultView get(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        List<CandidateExperience> experiences = experienceRepository.findByCandidateId(candidateId);
        List<Achievement> achievements = achievementRepository.findByCandidateId(candidateId);
        List<Education> education = educationRepository.findByCandidateId(candidateId);
        List<Certification> certifications = certificationRepository.findByCandidateId(candidateId);
        List<Project> projects = projectRepository.findByCandidateId(candidateId);
        List<CandidateSkillProfile> skillProfiles = skillProfileRepository.findByCandidateId(candidateId);

        // completeness intentionally stays a fraction of these original 5 sections (see
        // CLAUDE.md) - projects is an additional, optional section that doesn't change
        // that denominator's meaning for anything already depending on it.
        boolean[] present = {
                !candidate.getSkillIds().isEmpty(),
                !experiences.isEmpty(),
                !achievements.isEmpty(),
                !education.isEmpty(),
                !certifications.isEmpty(),
        };
        int filled = 0;
        for (boolean p : present) {
            if (p) filled++;
        }
        double completeness = (double) filled / present.length;

        return new CareerVaultView(candidate, experiences, achievements, education, certifications, projects,
                skillProfiles, completeness);
    }

    /** Creates or updates the calling candidate's profile for one skill, matched by exact
     * skill name (case-insensitive) - see {@link CandidateSkillProfile}'s javadoc for why
     * this keys on the raw mention string rather than a normalized taxonomy id. */
    public CandidateSkillProfile upsertSkillProfile(UUID candidateId, SkillProfileRequest request) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

        CandidateSkillProfile profile = skillProfileRepository
                .findByCandidateIdAndSkillNameIgnoreCase(candidateId, request.skillName())
                .orElse(null);
        if (profile == null) {
            profile = new CandidateSkillProfile(candidate, request.skillName(), request.proficiencyLevel(),
                    request.yearsOfExperience(), request.confidenceScore(), request.lastUsedOn(), request.lastUsedVersion());
        } else {
            profile.update(request.proficiencyLevel(), request.yearsOfExperience(), request.confidenceScore(),
                    request.lastUsedOn(), request.lastUsedVersion());
        }
        return skillProfileRepository.save(profile);
    }

    public record CareerVaultView(
            Candidate candidate,
            List<CandidateExperience> experiences,
            List<Achievement> achievements,
            List<Education> education,
            List<Certification> certifications,
            List<Project> projects,
            List<CandidateSkillProfile> skillProfiles,
            double completeness) {
    }
}
