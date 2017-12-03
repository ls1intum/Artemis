package de.tum.in.www1.exerciseapp.domain.scoring;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestion;
import de.tum.in.www1.exerciseapp.domain.Question;
import de.tum.in.www1.exerciseapp.domain.enumeration.ScoringType;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class ScoringStrategyFactory {
    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given question
     * @param question the question that needs the ScoringStrategy
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    public static ScoringStrategy makeScoringStrategy(Question question) {
        if (question instanceof MultipleChoiceQuestion && question.getScoringType() == ScoringType.ALL_OR_NOTHING) {
            return new ScoringStrategyMultipleChoiceAllOrNothing();
        } else {
            throw new NotImplementedException();
        }
    }
}
