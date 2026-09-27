package ai.candidly.career.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared Schema.org {@code JobPosting} JSON-LD extraction, factored out of
 * {@link GenericJsonLdDiscoveryAdapter} so {@link HeadlessCareerPageDiscoveryAdapter} can reuse
 * the exact same parsing against a headless-rendered page's HTML instead of duplicating it.
 * Handles the three shapes JSON-LD job pages actually use: a bare {@code JobPosting} object, an
 * array of top-level objects, or a {@code @graph} wrapper.
 */
@Component
public class JsonLdJobPostingParser {

    private static final Pattern JSON_LD_SCRIPT = Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern REMOTE_HINT = Pattern.compile("remote|telecommute", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public JsonLdJobPostingParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<JsonNode> extractJobPostingNodes(String html) {
        List<JsonNode> result = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return result;
        }
        Matcher matcher = JSON_LD_SCRIPT.matcher(html);
        while (matcher.find()) {
            for (JsonNode node : parse(matcher.group(1).trim())) {
                collectJobPostingNodes(node, result);
            }
        }
        return result;
    }

    private List<JsonNode> parse(String json) {
        try {
            return List.of(objectMapper.readTree(json));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void collectJobPostingNodes(JsonNode node, List<JsonNode> found) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectJobPostingNodes(child, found));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (isJobPosting(node)) {
            found.add(node);
        }
        if (node.has("@graph")) {
            collectJobPostingNodes(node.get("@graph"), found);
        }
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) {
            return false;
        }
        if (type.isTextual()) {
            return "JobPosting".equals(type.asText());
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("JobPosting".equals(t.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Maps one {@code JobPosting} JSON-LD node to plain fields, shared by both JSON-LD-reading
     * adapters. {@code companyFallback} is used only when the node has no
     * {@code hiringOrganization.name} of its own. */
    public ParsedJobPosting toFields(String companyFallback, JsonNode job) {
        String title = job.path("title").asText("").trim();
        String description = stripHtml(job.path("description").asText(""));
        String company = job.path("hiringOrganization").path("name").asText("").trim();
        if (company.isBlank()) {
            company = companyFallback == null ? "Unknown" : companyFallback;
        }
        String location = locationText(job);
        boolean remote = "TELECOMMUTE".equalsIgnoreCase(job.path("jobLocationType").asText(""))
                || REMOTE_HINT.matcher(location).find();
        String identifierValue = job.path("identifier").path("value").asText("");
        if (identifierValue.isBlank()) {
            identifierValue = job.path("url").asText("");
        }
        if (identifierValue.isBlank()) {
            identifierValue = Integer.toHexString((title + company + location).hashCode());
        }
        Salary salary = salaryFrom(job.path("baseSalary"));
        return new ParsedJobPosting(title, description, company, location, remote, identifierValue,
                salary == null ? null : salary.minMinorUnits(), salary == null ? null : salary.maxMinorUnits(),
                salary == null ? null : salary.currency());
    }

    /** Schema.org {@code baseSalary} (a {@code MonetaryAmount}) nests its numbers under a
     * {@code value} {@code QuantitativeValue}. Only {@code unitText: "YEAR"} (or absent, treated
     * as annual) is used - {@code JobPosting.compMinMinorUnits}/{@code compMaxMinorUnits} are
     * compared directly against a candidate's annual comp floor elsewhere
     * (EligibilityGateService), so an hourly/monthly figure would need converting to be
     * comparable, and this doesn't attempt that. */
    private Salary salaryFrom(JsonNode baseSalary) {
        if (baseSalary == null || baseSalary.isMissingNode() || baseSalary.isNull()) {
            return null;
        }
        String currency = baseSalary.path("currency").asText("");
        JsonNode value = baseSalary.has("value") ? baseSalary.path("value") : baseSalary;
        String unitText = value.path("unitText").asText("YEAR");
        if (!"YEAR".equalsIgnoreCase(unitText)) {
            return null;
        }
        if (currency.isBlank() || !value.has("minValue") || !value.has("maxValue")) {
            return null;
        }
        try {
            String normalizedCurrency = ai.candidly.career.domain.CurrencyCodes.normalize(currency, "baseSalary.currency");
            long min = ai.candidly.career.domain.CurrencyCodes.toMinorUnits(value.path("minValue").asLong(), normalizedCurrency);
            long max = ai.candidly.career.domain.CurrencyCodes.toMinorUnits(value.path("maxValue").asLong(), normalizedCurrency);
            return new Salary(min, max, normalizedCurrency);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Salary(long minMinorUnits, long maxMinorUnits, String currency) {
    }

    /** {@code jobLocation} can be a single {@code Place} object or an array of them; falls back
     * to {@code applicantLocationRequirements} (a list of countries) for a fully-remote posting
     * that has no concrete office location at all. */
    private String locationText(JsonNode job) {
        List<String> places = new ArrayList<>();
        JsonNode jobLocation = job.path("jobLocation");
        if (jobLocation.isArray()) {
            jobLocation.forEach(loc -> addIfPresent(places, placeText(loc)));
        } else if (jobLocation.isObject()) {
            addIfPresent(places, placeText(jobLocation));
        }
        if (places.isEmpty()) {
            JsonNode requirements = job.path("applicantLocationRequirements");
            if (requirements.isArray()) {
                requirements.forEach(country -> addIfPresent(places, country.path("name").asText("")));
            }
        }
        return String.join("; ", places);
    }

    private String placeText(JsonNode place) {
        JsonNode address = place.path("address");
        List<String> parts = new ArrayList<>();
        for (String field : new String[] {"addressLocality", "addressRegion", "addressCountry"}) {
            addIfPresent(parts, address.path(field).asText(""));
        }
        return String.join(", ", parts);
    }

    private void addIfPresent(List<String> list, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isBlank()) {
            list.add(trimmed);
        }
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String unescaped = HtmlUtils.htmlUnescape(html);
        return HTML_TAG.matcher(unescaped).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    public record ParsedJobPosting(String title, String description, String company, String location,
            boolean remote, String identifierValue, Long compMinMinorUnits, Long compMaxMinorUnits, String currency) {
    }
}
