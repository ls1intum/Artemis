package de.tum.in.www1.artemis.service.plagiarism;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

public final class ContinuousPlagiarismControlFeedbackHelper {

    private ContinuousPlagiarismControlFeedbackHelper() {
    }

    public static final String CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN = "Continuous Plagiarism Control:";

    public static final String CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE = "Kontinuierliche Plagiatskontrolle:";

    public static boolean isCpcFeedback(Feedback feedback) {
        return feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isPositive() && (feedback.getText().startsWith(CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN)
                || feedback.getText().startsWith(CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE));
    }
}
