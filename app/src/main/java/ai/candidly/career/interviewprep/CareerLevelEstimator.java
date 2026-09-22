package ai.candidly.career.interviewprep;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import ai.candidly.career.domain.CandidateExperience;

/**
 * Suggests a starting {@link CareerLevel} from a candidate's own experience rows -
 * total years worked (date-range arithmetic) plus a senior-title keyword check. Plain
 * code, no TypeSafe/Groq call: this is exact date math and a fixed keyword lookup, not a
 * semantic judgment (typesafe-ai skill: "keep arithmetic, counting, and date math in
 * code" - same precedent as {@code ats.AtsScoreService}'s deterministic checks). The
 * result only pre-fills a UI control the candidate can override, so a rough heuristic is
 * an acceptable, non-authoritative starting point.
 */
@Component
public class CareerLevelEstimator {

    private static final List<String> SENIOR_TITLE_KEYWORDS = List.of(
            "staff", "principal", "lead", "head", "director", "vp", "chief");

    public CareerLevel estimate(List<CandidateExperience> experience) {
        if (experience == null || experience.isEmpty()) {
            return CareerLevel.ENTRY;
        }

        double totalYears = experience.stream()
                .mapToDouble(this::yearsOf)
                .sum();

        boolean seniorTitle = experience.stream()
                .map(CandidateExperience::getTitle)
                .filter(title -> title != null)
                .map(title -> title.toLowerCase(Locale.ROOT))
                .anyMatch(title -> SENIOR_TITLE_KEYWORDS.stream().anyMatch(title::contains));

        CareerLevel byYears = levelForYears(totalYears);
        return seniorTitle ? bumpUp(byYears) : byYears;
    }

    private double yearsOf(CandidateExperience experience) {
        LocalDate start = experience.getStartDate();
        if (start == null) {
            return 0;
        }
        LocalDate end = experience.getEndDate() != null ? experience.getEndDate() : LocalDate.now();
        if (end.isBefore(start)) {
            return 0;
        }
        return Period.between(start, end).toTotalMonths() / 12.0;
    }

    private CareerLevel levelForYears(double years) {
        if (years < 2) {
            return CareerLevel.ENTRY;
        }
        if (years < 5) {
            return CareerLevel.MID;
        }
        if (years < 10) {
            return CareerLevel.SENIOR;
        }
        return CareerLevel.STAFF_PLUS;
    }

    private CareerLevel bumpUp(CareerLevel level) {
        return switch (level) {
            case ENTRY -> CareerLevel.MID;
            case MID -> CareerLevel.SENIOR;
            case SENIOR, STAFF_PLUS -> CareerLevel.STAFF_PLUS;
        };
    }
}
