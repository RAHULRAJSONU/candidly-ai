package ai.candidly.career.profile;

import java.util.Set;

/**
 * Partial-update payload for the "Resume &amp; Profile" page's Personal Info / Professional
 * Summary / Career Interests / Preferences editors (mockup-driven addition - no direct
 * docs/00-04 requirement). Every field is optional: a {@code null} means "leave unchanged",
 * letting each card save its own section independently instead of round-tripping the whole
 * candidate on every edit. An empty {@code Set} explicitly clears that section.
 */
public record CandidateProfileUpdateRequest(
        String fullName,
        String location,
        Set<String> workAuthorizations,
        String phone,
        String linkedinUrl,
        String portfolioUrl,
        String headline,
        String professionalSummary,
        String workMode,
        String noticePeriod,
        Boolean openToRelocation,
        Long expectedCompMinMinorUnits,
        Long expectedCompMaxMinorUnits,
        Long compFloorMinorUnits,
        String preferredCurrency,
        Set<String> preferredLocations,
        Set<String> preferredRoles,
        Set<String> preferredIndustries,
        Set<String> employmentTypes,
        Set<String> rawSkillMentions) {
}
