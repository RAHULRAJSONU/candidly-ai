package ai.candidly.career.taxonomy;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * In-memory stand-in domain/industry list (this implementation's own choice, not a spec
 * value or an external standard like ESCO) used only to keep the {@code target_domain}
 * values {@link ai.candidly.career.matching.DomainFitJudgeService} and
 * {@link ai.candidly.career.matching.SemanticFitJudgeService} compare against consistent
 * and free of typos across every posting {@link ai.candidly.career.api.JobPostingExtractionService}
 * labels. Swapping this list is safe - nothing else hardcodes it.
 */
@Component
public class DomainTaxonomy {

    private final List<String> domains = List.of(
            "fintech",
            "healthcare",
            "e-commerce",
            "enterprise software",
            "gaming",
            "education",
            "logistics and supply chain",
            "government and public sector",
            "media and entertainment",
            "cybersecurity",
            "developer tools and infrastructure",
            "general technology");

    public List<String> domains() {
        return domains;
    }
}
