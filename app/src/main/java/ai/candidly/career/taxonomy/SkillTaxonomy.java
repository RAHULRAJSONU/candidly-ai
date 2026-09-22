package ai.candidly.career.taxonomy;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for the real ESCO/Lightcast taxonomy (docs/04 FR-2 calls for
 * mapping raw skill mentions to ESCO/Lightcast IDs and measuring mapping coverage).
 * Swapping this for a real taxonomy source is the production upgrade path; the
 * disambiguation logic in SkillNormalizationService does not change.
 */
@Component
public class SkillTaxonomy {

    private final List<SkillTaxonomyEntry> entries = List.of(
            new SkillTaxonomyEntry("skill.java", "Java", "The Java programming language and JVM ecosystem"),
            new SkillTaxonomyEntry("skill.postgresql", "PostgreSQL", "The PostgreSQL relational database"),
            new SkillTaxonomyEntry("skill.kubernetes", "Kubernetes", "Container orchestration with Kubernetes"),
            new SkillTaxonomyEntry("skill.react", "React", "The React JavaScript UI framework"),
            new SkillTaxonomyEntry("skill.python", "Python", "The Python programming language"),
            new SkillTaxonomyEntry("skill.aws", "AWS", "Amazon Web Services cloud platform"),
            new SkillTaxonomyEntry("skill.kafka", "Apache Kafka", "The Apache Kafka event streaming platform"),
            new SkillTaxonomyEntry("skill.spring", "Spring Framework", "The Spring / Spring Boot Java application framework"),
            new SkillTaxonomyEntry("skill.ml", "Machine Learning", "Applied machine learning and model development"),
            new SkillTaxonomyEntry("skill.sql", "SQL", "Structured Query Language for relational databases"),
            new SkillTaxonomyEntry("skill.docker", "Docker", "Containerization with Docker"),
            new SkillTaxonomyEntry("skill.terraform", "Terraform", "Infrastructure as code with Terraform"),
            new SkillTaxonomyEntry("skill.typescript", "TypeScript", "The TypeScript programming language"),
            new SkillTaxonomyEntry("skill.golang", "Go", "The Go programming language"),
            new SkillTaxonomyEntry("skill.product-management", "Product Management", "Product management and roadmap ownership"));

    public List<SkillTaxonomyEntry> entries() {
        return entries;
    }
}
