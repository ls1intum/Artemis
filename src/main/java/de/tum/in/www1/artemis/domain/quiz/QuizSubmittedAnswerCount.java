package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizSubmittedAnswerCount {

    private final long quizSubmissionId;

    private final long count;

    private final long participationId;

    public long getQuizSubmissionId() {
        return quizSubmissionId;
    }

    public long getParticipationId() {
        return participationId;
    }

    public long getCount() {
        return count;
    }

    public QuizSubmittedAnswerCount(long count, long quizSubmissionId, long participationId) {
        this.quizSubmissionId = quizSubmissionId;
        this.count = count;
        this.participationId = participationId;
    }
}
