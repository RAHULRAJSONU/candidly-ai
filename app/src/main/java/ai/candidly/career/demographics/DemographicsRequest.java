package ai.candidly.career.demographics;

/**
 * Every field is optional and blank/omitted maps to {@link CandidateDemographics#DECLINED}
 * - the candidate is never required to answer (docs/02 §4, docs/04).
 */
public record DemographicsRequest(String gender, String raceEthnicity, String veteranStatus, String disabilityStatus) {
}
