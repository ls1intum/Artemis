package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

public interface QuizResult {

    void evaluateQuizSubmission();

    void setRated(Boolean rated);

    void setAssessmentType(AssessmentType assessmentType);

    void setCompletionDate(ZonedDateTime completionDate);

    void setSubmission(Submission submission);

    Result getEntity();
}
