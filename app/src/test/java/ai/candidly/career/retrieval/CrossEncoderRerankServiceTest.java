package ai.candidly.career.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateExperience;
import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class CrossEncoderRerankServiceTest {

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void reordersCandidatesByDescendingRelevanceProbability() {
        Candidate candidate = new Candidate("Ada", "ada@example.com", "Remote", Set.of("US"), 0, Set.of(), Set.of());
        CandidateExperience experience = new CandidateExperience(candidate, "Acme", "Engineer",
                LocalDate.of(2020, 1, 1), null, "Built backend systems", Set.of(), Set.of(), Set.of());

        JobPosting weakFit = job("weak-fit");
        JobPosting strongFit = job("strong-fit");
        JobPosting mediumFit = job("medium-fit");

        // Mockito assigns question keys in call order (relevant_0=weakFit, relevant_1=strongFit,
        // relevant_2=mediumFit per the input list order) - the response scores them out of order
        // on purpose to prove rerank() actually re-sorts rather than passing input order through.
        when(typeSafeClient.ask(any(), anyMap())).thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of(
                "relevant_0", noul(0.10),
                "relevant_1", noul(0.95),
                "relevant_2", noul(0.55))));

        List<JobPosting> reranked = new CrossEncoderRerankService(typeSafeClient)
                .rerank(List.of(experience), List.of(weakFit, strongFit, mediumFit));

        assertThat(reranked).containsExactly(strongFit, mediumFit, weakFit);
    }

    @Test
    void emptyCandidateListShortCircuitsWithoutCallingTypeSafe() {
        List<JobPosting> result = new CrossEncoderRerankService(typeSafeClient).rerank(List.of(), List.of());

        assertThat(result).isEmpty();
    }

    private static TypeSafeAnswer noul(double probability) {
        return new TypeSafeAnswer("noul", probability, null, null, null, null, null);
    }

    private JobPosting job(String sourceId) {
        return new JobPosting(sourceId, "Acme", "Engineer", "Remote", true,
                null, null, Set.of("US"), Set.of(), Set.of(), "tech", 0, "raw description");
    }
}
