package ai.candidly.career.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.domain.JobPosting;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.typesafe.TypeSafeAnswer;
import ai.candidly.career.typesafe.TypeSafeClient;
import ai.candidly.career.typesafe.TypeSafeResponse;

@ExtendWith(MockitoExtension.class)
class FuzzyDuplicateJobPostingServiceTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private TypeSafeClient typeSafeClient;

    @Test
    void noCandidatesShortCircuitsWithoutCallingTypeSafe() {
        when(jobPostingRepository.findByCompanyIgnoreCase("Acme")).thenReturn(List.of());

        var result = new FuzzyDuplicateJobPostingService(jobPostingRepository, typeSafeClient)
                .findDuplicate(request("Acme", "Staff Engineer"));

        assertThat(result).isEmpty();
        verifyNoInteractions(typeSafeClient);
    }

    @Test
    void highProbabilityMatchIsReturnedAsDuplicate() {
        JobPosting existing = new JobPosting("hash-1", "Acme", "Sr. Backend Engineer", "Remote", true,
                null, null, Set.of(), Set.of(), Set.of(), "fintech", 5, "raw");
        when(jobPostingRepository.findByCompanyIgnoreCase("Acme")).thenReturn(List.of(existing));
        when(typeSafeClient.ask(any(), anyMap()))
                .thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of("same_opening_0", noul(0.92))));

        var result = new FuzzyDuplicateJobPostingService(jobPostingRepository, typeSafeClient)
                .findDuplicate(request("Acme", "Senior Backend Engineer"));

        assertThat(result).contains(existing);
    }

    @Test
    void lowProbabilityMatchIsNotTreatedAsDuplicate() {
        JobPosting existing = new JobPosting("hash-1", "Acme", "Marketing Manager", "Remote", true,
                null, null, Set.of(), Set.of(), Set.of(), "marketing", 3, "raw");
        when(jobPostingRepository.findByCompanyIgnoreCase("Acme")).thenReturn(List.of(existing));
        when(typeSafeClient.ask(any(), anyMap()))
                .thenReturn(new TypeSafeResponse("jev-1.13.0", Map.of("same_opening_0", noul(0.1))));

        var result = new FuzzyDuplicateJobPostingService(jobPostingRepository, typeSafeClient)
                .findDuplicate(request("Acme", "Senior Backend Engineer"));

        assertThat(result).isEmpty();
    }

    private static JobPostingRequest request(String company, String title) {
        return new JobPostingRequest("src-1", company, title, "Remote", true, null, null, null, Set.of(), Set.of(),
                Set.of(), "fintech", 5, "raw description");
    }

    private static TypeSafeAnswer noul(double value) {
        return new TypeSafeAnswer("noul", value, null, null, null, null, null);
    }
}
