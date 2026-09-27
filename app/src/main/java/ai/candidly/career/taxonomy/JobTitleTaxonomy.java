package ai.candidly.career.taxonomy;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * A fixed, in-memory catalog of target-role archetypes spanning the individual-contributor
 * and management tracks at every seniority band. This exists so {@code TitleFitJudgeService}
 * can ask "is this a credible target for this candidate" as a selection over a closed,
 * enumerable set (typesafe-ai skill: "select instead of generate") rather than letting a
 * model free-generate job titles, which risks inventing implausible or non-existent titles.
 * Swapping this for a real O*NET/Lightcast occupation catalog is the production upgrade path.
 */
@Component
public class JobTitleTaxonomy {

    private final List<JobTitleTaxonomyEntry> entries = List.of(
            new JobTitleTaxonomyEntry("title.swe-i", "Software Engineer I",
                    "Entry-level individual contributor implementing well-specified features under close guidance."),
            new JobTitleTaxonomyEntry("title.swe-ii", "Software Engineer II",
                    "Mid-level individual contributor who independently owns features and small components."),
            new JobTitleTaxonomyEntry("title.senior-swe", "Senior Software Engineer",
                    "Senior individual contributor who owns significant systems end-to-end and mentors others."),
            new JobTitleTaxonomyEntry("title.staff-swe", "Staff Software Engineer",
                    "Staff-level engineer who sets technical direction across multiple teams or a whole product area."),
            new JobTitleTaxonomyEntry("title.principal-swe", "Principal Software Engineer",
                    "Principal engineer shaping technical strategy at an organizational level with cross-team authority."),
            new JobTitleTaxonomyEntry("title.distinguished-engineer", "Distinguished Engineer",
                    "The most senior individual-contributor track, setting technical direction company-wide."),
            new JobTitleTaxonomyEntry("title.backend-engineer", "Backend Engineer",
                    "Individual contributor focused on server-side services, APIs, and data persistence."),
            new JobTitleTaxonomyEntry("title.distributed-systems-engineer", "Distributed Systems Engineer",
                    "Engineer specializing in multi-node, fault-tolerant, horizontally-scaled system design."),
            new JobTitleTaxonomyEntry("title.platform-engineer", "Platform Engineer",
                    "Engineer building the internal tooling, infrastructure, and services other teams build on."),
            new JobTitleTaxonomyEntry("title.cloud-architect", "Cloud Architect",
                    "Architect responsible for cloud infrastructure design, migration, and cost/reliability trade-offs."),
            new JobTitleTaxonomyEntry("title.solutions-architect", "Solutions Architect",
                    "Architect who designs end-to-end technical solutions spanning multiple systems or teams."),
            new JobTitleTaxonomyEntry("title.technical-architect", "Technical Architect",
                    "Architect who defines the technical standards and system architecture for a product line."),
            new JobTitleTaxonomyEntry("title.devops-engineer", "DevOps / Site Reliability Engineer",
                    "Engineer focused on deployment pipelines, observability, and production reliability."),
            new JobTitleTaxonomyEntry("title.security-engineer", "Security Engineer",
                    "Engineer focused on application, infrastructure, or product security."),
            new JobTitleTaxonomyEntry("title.data-engineer", "Data Engineer",
                    "Engineer building data pipelines, warehousing, and large-scale data infrastructure."),
            new JobTitleTaxonomyEntry("title.ml-engineer", "Machine Learning Engineer",
                    "Engineer building, training, and productionizing machine learning models."),
            new JobTitleTaxonomyEntry("title.ai-platform-engineer", "AI Platform Engineer",
                    "Engineer building the infrastructure and tooling that AI/LLM-powered products run on."),
            new JobTitleTaxonomyEntry("title.applied-ai-engineer", "Applied AI / Agentic Systems Engineer",
                    "Engineer building AI-agent, LLM-orchestration, or retrieval-augmented product features."),
            new JobTitleTaxonomyEntry("title.frontend-engineer", "Frontend Engineer",
                    "Individual contributor focused on user-facing web or mobile application development."),
            new JobTitleTaxonomyEntry("title.fullstack-engineer", "Full-Stack Engineer",
                    "Individual contributor working across both frontend and backend layers of a product."),
            new JobTitleTaxonomyEntry("title.mobile-engineer", "Mobile Engineer",
                    "Engineer focused on native or cross-platform mobile application development."),
            new JobTitleTaxonomyEntry("title.qa-engineer", "QA / Test Automation Engineer",
                    "Engineer focused on test strategy, automation frameworks, and quality processes."),
            new JobTitleTaxonomyEntry("title.engineering-manager", "Engineering Manager",
                    "People manager responsible for a team's delivery, growth, and day-to-day technical decisions."),
            new JobTitleTaxonomyEntry("title.senior-engineering-manager", "Senior Engineering Manager",
                    "People manager responsible for multiple teams or a larger, more complex engineering group."),
            new JobTitleTaxonomyEntry("title.director-engineering", "Director of Engineering",
                    "Senior engineering leader responsible for a whole department's technical and people strategy."),
            new JobTitleTaxonomyEntry("title.vp-engineering", "VP of Engineering",
                    "Executive engineering leader responsible for the engineering organization's strategy and outcomes."),
            new JobTitleTaxonomyEntry("title.product-manager", "Product Manager",
                    "Individual contributor responsible for product strategy, roadmap, and requirements."));

    public List<JobTitleTaxonomyEntry> entries() {
        return entries;
    }
}
