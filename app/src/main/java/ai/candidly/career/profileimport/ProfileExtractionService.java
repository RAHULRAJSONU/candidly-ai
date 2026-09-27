package ai.candidly.career.profileimport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.candidly.career.ai.GroqChatClient;

/**
 * Extracts a best-effort {@link ProfileImportResult} from free-text resume/LinkedIn
 * content via Groq ({@link GroqChatClient}) - per the typesafe-ai skill, TypeSafe's Jev
 * model is "not a generator," so free-text extraction goes through Groq in this repo
 * (same precedent as {@code tailoring.GroundedResumeGenerator} and
 * {@code interviewprep.PersonalizedQuestionGeneratorService}). Groq is asked to emit a
 * fixed delimited text format rather than JSON, and this class parses it with plain
 * string/regex code - more robust to an imperfect model output than trusting strict
 * JSON, and consistent with this repo's existing "generate then code-parse a
 * constrained format" pattern.
 *
 * <p>Unlike {@code GroundedResumeGenerator}, there is no grounding-verifier step here:
 * this result only pre-fills an onboarding form the candidate reviews and edits before
 * anything is submitted (see {@code CandidateIngestionService.ingest}, which is the
 * actual point nothing bypasses), so hallucination risk is bounded by that human review
 * rather than by a verifier.
 *
 * <p><b>Email is the one exception</b> ({@link EmailExtractionService}): it's lexically
 * well-defined (regex-identifiable) and wrong values corrupt account identity, so it
 * goes through TypeSafe's pre-parsed value extraction pattern (regex-found candidates,
 * Jev {@code Choice} only disambiguates among them) rather than free-text Groq output.
 * Skills already use the same "select instead of generate" pattern one step later, in
 * {@link ai.candidly.career.taxonomy.SkillNormalizationService} - the raw mentions
 * collected here are unconstrained on purpose (Groq's job is just noticing what's
 * mentioned), and normalization against the real taxonomy happens downstream in
 * {@code CandidateIngestionService}, not in this class.
 */
@Service
public class ProfileExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ProfileExtractionService.class);

    private static final String SYSTEM_PROMPT = """
            You extract structured profile data from resume or LinkedIn profile text. \
            Output ONLY the following fixed format, nothing else - no preamble, no commentary, no markdown:

            FULL_NAME: <full name, or NONE if not found>
            LOCATION: <city/region, or NONE if not found>
            SUMMARY: <a 2-3 sentence professional summary, written ONLY from facts explicitly \
            stated in the source text (role, years of experience, domains, technologies actually \
            mentioned) - never invent, embellish, or infer anything not present in the text. \
            Output NONE if the text does not contain enough information for a truthful summary, \
            or if it already contains its own summary/objective section (repeat that one verbatim \
            instead of writing a new one).>
            SKILLS: <comma-separated list of skills/technologies mentioned>
            EXPERIENCE_START
            EMPLOYER: <company name>
            TITLE: <job title>
            START_DATE: <YYYY-MM, or NONE if unknown>
            END_DATE: <YYYY-MM, or PRESENT if current, or NONE if unknown>
            NARRATIVE: <1-3 sentence summary of the role and its impact, in third person>
            EXPERIENCE_END

            Repeat the EXPERIENCE_START/EXPERIENCE_END block once per job, most recent first. \
            If no work experience is found, omit all EXPERIENCE_START blocks entirely.""";

    private static final Pattern FIELD_PATTERN = Pattern.compile("^([A-Z_]+):\\s*(.*)$");

    private final GroqChatClient groqChatClient;
    private final EmailExtractionService emailExtractionService;

    public ProfileExtractionService(GroqChatClient groqChatClient, EmailExtractionService emailExtractionService) {
        this.groqChatClient = groqChatClient;
        this.emailExtractionService = emailExtractionService;
    }

    public ProfileImportResult extractFromResume(String resumeText) {
        return extract(resumeText, "The following text was extracted from an uploaded resume file:");
    }

    public ProfileImportResult extractFromLinkedInText(String linkedInText) {
        return extract(linkedInText, "The following text is a pasted LinkedIn profile (URL and/or exported text):");
    }

    private ProfileImportResult extract(String sourceText, String userPromptPrefix) {
        if (sourceText == null || sourceText.isBlank()) {
            return new ProfileImportResult(null, null, null, null, Set.of(), List.of());
        }
        String email = emailExtractionService.extractOwnEmail(sourceText).orElse(null);
        try {
            String completion = groqChatClient.complete(SYSTEM_PROMPT, userPromptPrefix + "\n\n" + sourceText);
            ProfileImportResult parsed = parse(completion);
            return new ProfileImportResult(parsed.fullName(), email, parsed.location(), parsed.professionalSummary(),
                    parsed.rawSkillMentions(), parsed.experiences());
        } catch (RuntimeException e) {
            log.warn("Profile extraction failed - returning an empty draft for the candidate to fill in manually", e);
            return new ProfileImportResult(null, email, null, null, Set.of(), List.of());
        }
    }

    private ProfileImportResult parse(String completion) {
        if (completion == null) {
            return new ProfileImportResult(null, null, null, null, Set.of(), List.of());
        }
        String[] lines = completion.split("\\R");

        String fullName = null;
        String location = null;
        String summary = null;
        Set<String> skills = new LinkedHashSet<>();
        List<ProfileImportResult.ExperienceDraft> experiences = new ArrayList<>();

        String employer = null, title = null, startDate = null, endDate = null, narrative = null;
        boolean inExperience = false;

        for (String line : lines) {
            var matcher = FIELD_PATTERN.matcher(line.strip());
            if (line.strip().equals("EXPERIENCE_START")) {
                inExperience = true;
                employer = title = startDate = endDate = narrative = null;
                continue;
            }
            if (line.strip().equals("EXPERIENCE_END")) {
                if (inExperience && notBlank(employer) && notBlank(title)) {
                    experiences.add(new ProfileImportResult.ExperienceDraft(employer, title, parseDate(startDate),
                            parseDate(endDate), narrative == null ? "" : narrative, Set.of()));
                }
                inExperience = false;
                continue;
            }
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1);
            String value = valueOrNull(matcher.group(2));
            if (inExperience) {
                switch (key) {
                    case "EMPLOYER" -> employer = value;
                    case "TITLE" -> title = value;
                    case "START_DATE" -> startDate = value;
                    case "END_DATE" -> endDate = value;
                    case "NARRATIVE" -> narrative = value;
                    default -> { }
                }
            } else {
                switch (key) {
                    case "FULL_NAME" -> fullName = value;
                    case "LOCATION" -> location = value;
                    case "SUMMARY" -> summary = value;
                    case "SKILLS" -> skills.addAll(splitSkills(matcher.group(2)));
                    default -> { }
                }
            }
        }

        return new ProfileImportResult(fullName, null, location, summary, skills, experiences);
    }

    private Set<String> splitSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<String> skills = new LinkedHashSet<>();
        for (String skill : raw.split(",")) {
            String trimmed = skill.strip();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("NONE")) {
                skills.add(trimmed);
            }
        }
        return skills;
    }

    private String valueOrNull(String value) {
        String trimmed = value == null ? "" : value.strip();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("NONE") ? null : trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Accepts YYYY-MM or YYYY-MM-DD; NONE/PRESENT/unparseable -> null (ongoing/unknown). */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NONE") || value.equalsIgnoreCase("PRESENT")) {
            return null;
        }
        try {
            if (value.length() == 7) {
                return LocalDate.parse(value + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
