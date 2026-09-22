package ai.candidly.career.interviewprep;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * An in-memory question bank, same "stand-in, not a real content pipeline" pattern as
 * {@code taxonomy}'s in-memory ESCO/Lightcast stand-in - a real product would source
 * these from a maintained bank, not a hardcoded list.
 */
@Component
public class InterviewQuestionBank {

    private static final List<InterviewQuestion> QUESTIONS = List.of(
            new InterviewQuestion("sd-1", InterviewCategory.SYSTEM_DESIGN,
                    "Design a URL shortener that needs to handle 100M requests per day. Walk through your approach.", false),
            new InterviewQuestion("sd-2", InterviewCategory.SYSTEM_DESIGN,
                    "How would you design a rate limiter for a public API?", false),
            new InterviewQuestion("code-1", InterviewCategory.CODING,
                    "Explain how you'd detect a cycle in a linked list, and the time/space complexity of your approach.", false),
            new InterviewQuestion("code-2", InterviewCategory.CODING,
                    "Describe how you'd find the k most frequent elements in a large stream of data.", false),
            new InterviewQuestion("behavioral-1", InterviewCategory.BEHAVIORAL,
                    "Tell me about a time you disagreed with a technical decision. What did you do?", false),
            new InterviewQuestion("behavioral-2", InterviewCategory.BEHAVIORAL,
                    "Describe a project that failed or fell short. What did you learn?", false),
            new InterviewQuestion("domain-1", InterviewCategory.DOMAIN,
                    "How would you evaluate whether a matching/ranking system in production is actually working well?", false),
            new InterviewQuestion("domain-2", InterviewCategory.DOMAIN,
                    "What tradeoffs would you consider between a deterministic rule and a model-based judgment in a decision pipeline?", false));

    public List<InterviewQuestion> all() {
        return QUESTIONS;
    }

    public List<InterviewQuestion> byCategory(InterviewCategory category) {
        return QUESTIONS.stream().filter(q -> q.category() == category).toList();
    }

    public InterviewQuestion get(String id) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question id: " + id));
    }
}
