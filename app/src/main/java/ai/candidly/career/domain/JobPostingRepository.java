package ai.candidly.career.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    Optional<JobPosting> findByDedupeHash(String dedupeHash);

    List<JobPosting> findByCompanyIgnoreCase(String company);
}
