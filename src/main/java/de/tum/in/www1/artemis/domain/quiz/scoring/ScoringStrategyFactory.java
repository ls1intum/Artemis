package de.tum.in.www1.artemis.domain.quiz.scoring;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;

public class ScoringStrategyFactory {

    /**
     * default method for the super class to support polymorphism
     *
     * @param quizQuestion the quizQuestion that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public ScoringStrategy makeScoringStrategy(QuizQuestion quizQuestion) {
        throw new UnsupportedOperationException("Unknown quizQuestion type " + quizQuestion);
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given multiple choice question (based on polymorphism)
     *
     * @param quizQuestion the quizQuestion that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public ScoringStrategy makeScoringStrategy(MultipleChoiceQuestion quizQuestion) {
        return switch (quizQuestion.getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyMultipleChoiceAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyMultipleChoiceProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyMultipleChoiceProportionalWithoutPenalty();
        };
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given drag and drop question (based on polymorphism)
     *
     * @param quizQuestion the quizQuestion that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public ScoringStrategy makeScoringStrategy(DragAndDropQuestion quizQuestion) {
        return switch (quizQuestion.getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyDragAndDropAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyDragAndDropProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyDragAndDropProportionalWithoutPenalty();
        };
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given short answer question (based on polymorphism)
     *
     * @param quizQuestion the quizQuestion that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public ScoringStrategy makeScoringStrategy(ShortAnswerQuestion quizQuestion) {
        return switch (quizQuestion.getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyShortAnswerAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyShortAnswerProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyShortAnswerProportionalWithoutPenalty();
        };
    }
}
