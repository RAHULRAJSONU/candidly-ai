package ai.candidly.career.autopilot;

import java.util.Set;

/** PUT body for the Autopilot settings screen - every field optional/nullable, so a partial save only touches what changed. */
public record AutopilotSettingsRequest(
        Set<String> preferredRoles,
        Set<String> preferredLocations,
        String experienceLevel,
        String jobType,
        Long salaryMinMinorUnits,
        Long salaryMaxMinorUnits,
        Boolean remoteOnly,
        Boolean openToRelocation,
        Boolean includeGlobalOpportunities,
        Set<String> keySkills,
        Set<String> includeKeywords,
        Set<String> excludeKeywords,
        Set<String> enabledJobSources,
        Boolean autoApply,
        Boolean aiTailorResume,
        Boolean generateCoverLetter,
        Boolean autoFillForms,
        Boolean skipSponsorshipRequired,
        Boolean notifyBeforeApplying,
        Boolean autoFollowUp,
        Integer dailyApplicationLimit,
        Boolean notifyNewMatches,
        Boolean notifyApplicationSubmitted,
        Boolean notifyStatusChanges,
        Boolean notifyInterviewInvitations,
        Boolean notifyWeeklySummary) {
}
