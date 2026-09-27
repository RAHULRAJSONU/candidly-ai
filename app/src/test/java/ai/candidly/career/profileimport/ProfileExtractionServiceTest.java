package ai.candidly.career.profileimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ai.candidly.career.ai.GroqChatClient;

@ExtendWith(MockitoExtension.class)
class ProfileExtractionServiceTest {

    @Mock
    private GroqChatClient groqChatClient;
    @Mock
    private EmailExtractionService emailExtractionService;

    @Test
    void parsesFixedDelimitedFormatIntoStructuredDraft() {
        String completion = """
                FULL_NAME: Jane Doe
                EMAIL: jane.doe@example.com
                LOCATION: Austin, TX
                SUMMARY: Senior Engineer with experience leading backend platform teams and building distributed systems.
                SKILLS: Java, Spring Boot, AWS
                EXPERIENCE_START
                EMPLOYER: Acme Corp
                TITLE: Senior Engineer
                START_DATE: 2020-03
                END_DATE: PRESENT
                NARRATIVE: Led backend platform team, built distributed systems.
                EXPERIENCE_END
                EXPERIENCE_START
                EMPLOYER: Old Co
                TITLE: Software Engineer
                START_DATE: 2017-06
                END_DATE: 2020-02
                NARRATIVE: Built internal tools.
                EXPERIENCE_END""";
        when(groqChatClient.complete(any(), any())).thenReturn(completion);
        when(emailExtractionService.extractOwnEmail(any())).thenReturn(Optional.of("jane.doe@example.com"));

        var result = new ProfileExtractionService(groqChatClient, emailExtractionService).extractFromResume("some resume text");

        assertThat(result.fullName()).isEqualTo("Jane Doe");
        assertThat(result.email()).isEqualTo("jane.doe@example.com");
        assertThat(result.location()).isEqualTo("Austin, TX");
        assertThat(result.professionalSummary())
                .isEqualTo("Senior Engineer with experience leading backend platform teams and building distributed systems.");
        assertThat(result.rawSkillMentions()).containsExactly("Java", "Spring Boot", "AWS");
        assertThat(result.experiences()).hasSize(2);

        var first = result.experiences().get(0);
        assertThat(first.employer()).isEqualTo("Acme Corp");
        assertThat(first.title()).isEqualTo("Senior Engineer");
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(first.endDate()).isNull();

        var second = result.experiences().get(1);
        assertThat(second.endDate()).isEqualTo(LocalDate.of(2020, 2, 1));
    }

    @Test
    void noneFieldsBecomeNull() {
        String completion = """
                FULL_NAME: NONE
                EMAIL: NONE
                LOCATION: NONE
                SUMMARY: NONE
                SKILLS: NONE""";
        when(groqChatClient.complete(any(), any())).thenReturn(completion);

        var result = new ProfileExtractionService(groqChatClient, emailExtractionService).extractFromResume("blank resume");

        assertThat(result.fullName()).isNull();
        assertThat(result.email()).isNull();
        assertThat(result.location()).isNull();
        assertThat(result.professionalSummary()).isNull();
        assertThat(result.rawSkillMentions()).isEmpty();
        assertThat(result.experiences()).isEmpty();
    }

    @Test
    void blankInputShortCircuitsWithoutCallingGroq() {
        var result = new ProfileExtractionService(groqChatClient, emailExtractionService).extractFromResume("  ");

        assertThat(result.fullName()).isNull();
        assertThat(result.experiences()).isEmpty();
    }

    @Test
    void groqFailureReturnsEmptyDraftInsteadOfPropagating() {
        when(groqChatClient.complete(any(), any())).thenThrow(new IllegalStateException("Groq unavailable"));

        var result = new ProfileExtractionService(groqChatClient, emailExtractionService).extractFromResume("some resume text");

        assertThat(result.fullName()).isNull();
        assertThat(result.rawSkillMentions()).isEmpty();
        assertThat(result.experiences()).isEmpty();
    }
}
