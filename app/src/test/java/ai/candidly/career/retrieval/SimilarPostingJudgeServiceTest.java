package ai.candidly.career.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class SimilarPostingJudgeServiceTest {

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void returnsOnlyPostingsAboveTheSimilarityThresholdMostSimilarFirst() {
        JobPosting reference = job("ref", "Backend Engineer", "fintech", Set.of("java", "postgres"));
        JobPosting strongMatch = job("strong", "Senior Backend Engineer", "fintech", Set.of("java", "postgres"));
        JobPosting weakMatch = job("weak", "Backend Engineer, Data Platform", "fintech", Set.of("java", "kafka"));

        // Both share skills with the reference (so both survive the deterministic overlap
        // pre-filter), but the model judges only one a substantively similar role.
        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "similar_0", noul(0.92),
                "similar_1", noul(0.30))));

        List<JobPosting> similar = new SimilarPostingJudgeService(typeSafeClient)
                .findSimilar(reference, List.of(reference, strongMatch, weakMatch));

        assertThat(similar).containsExactly(strongMatch);
    }

    @Test
    void noOverlappingPostingsShortCircuitsWithoutCallingTypeSafe() {
        JobPosting reference = job("ref", "Backend Engineer", "fintech", Set.of("java", "postgres"));
        JobPosting unrelated = job("unrelated", "Farm Equipment Sales", "agriculture", Set.of("crm"));

        List<JobPosting> similar = new SimilarPostingJudgeService(typeSafeClient)
                .findSimilar(reference, List.of(reference, unrelated));

        assertThat(similar).isEmpty();
        org.mockito.Mockito.verify(typeSafeClient, never()).ask(any(), anyMap());
    }

    private static TypeSafeAnswer noul(double probability) {
        return new TypeSafeAnswer("noul", probability, null, null, null, null, null);
    }

    private JobPosting job(String sourceId, String title, String domain, Set<String> skills) {
        JobPosting job = new JobPosting(sourceId, "Acme", title, "Remote", true,
                null, null, Set.of("US"), skills, Set.of(), domain, 0, "raw description");
        setId(job);
        return job;
    }

    private void setId(JobPosting job) {
        try {
            var field = JobPosting.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(job, java.util.UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
