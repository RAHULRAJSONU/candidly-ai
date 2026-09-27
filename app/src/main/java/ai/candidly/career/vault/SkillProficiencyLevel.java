package ai.candidly.career.vault;

/** Candidate self-reported skill level - Beginner/Intermediate/Expert, the three levels
 * requested for the Career Vault Skills tab. Self-reported, same honesty class as
 * {@link CandidateSkillProfile}'s other fields - not a TypeSafe judgment. */
public enum SkillProficiencyLevel {
    BEGINNER,
    INTERMEDIATE,
    EXPERT
}
