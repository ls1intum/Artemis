package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.Question;
import de.tum.in.www1.artemis.domain.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.enumeration.ScoringType;

public class ScoringStrategyFactory {
    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given question
     *
     * @param question the question that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public static ScoringStrategy makeScoringStrategy(Question question) {
        if (question instanceof MultipleChoiceQuestion) {
            if (question.getScoringType() == ScoringType.ALL_OR_NOTHING) {
                return new ScoringStrategyMultipleChoiceAllOrNothing();
            } else if (question.getScoringType() == ScoringType.PROPORTIONAL_WITH_PENALTY) {
                return new ScoringStrategyMultipleChoiceProportionalWithPenalty();
            }
        } else if (question instanceof DragAndDropQuestion) {
            if (question.getScoringType() == ScoringType.ALL_OR_NOTHING) {
                return new ScoringStrategyDragAndDropAllOrNothing();
            } else if (question.getScoringType() == ScoringType.PROPORTIONAL_WITH_PENALTY) {
                return new ScoringStrategyDragAndDropProportionalWithPenalty();
            }
        }
        else if (question instanceof ShortAnswerQuestion) {
            if (question.getScoringType() == ScoringType.ALL_OR_NOTHING) {
                return new ScoringStrategyShortAnswerAllOrNothing();
            } else if (question.getScoringType() == ScoringType.PROPORTIONAL_WITH_PENALTY) {
                return new ScoringStrategyShortAnswerProportionalWithPenalty();
            }
        }
        throw new UnsupportedOperationException("Unknown ScoringType!");
    }
}
