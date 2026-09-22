package ai.candidly.career.interviewprep;

/** {@code generated} distinguishes a fixed bank question (looked up by id later) from a
 * personalized/follow-up one produced on the fly, whose full text the client must send
 * back with the answer since there is nothing to look up server-side for it. */
public record InterviewQuestion(String id, InterviewCategory category, String text, boolean generated) {
}
