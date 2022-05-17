package de.tum.in.www1.artemis.domain.quiz.scoring;

import java.util.Set;

import de.tum.in.www1.artemis.domain.quiz.*;

/**
 * Proportional with penalty means that every correct mapping increases the score by x and every incorrect mapping decreases the score by x where x = maxScore /
 * numberOfDropLocationsThatShouldHaveAMapping if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyDragAndDropProportionalWithPenalty implements ScoringStrategy {

    @Override
    public double calculateScore(QuizQuestion quizQuestion, SubmittedAnswer submittedAnswer) {
        // check if the quizQuestion is invalid: if true: -> return with full points
        if (quizQuestion.isInvalid()) {
            return quizQuestion.getPoints();
        }

        if (submittedAnswer instanceof DragAndDropSubmittedAnswer dndAnswer && quizQuestion instanceof DragAndDropQuestion dndQuestion) {
            double mappedDropLocations = 0;
            double correctMappings = 0;
            double incorrectMappings = 0;

            // iterate through each drop location and compare its correct mappings with the answer's mapping
            for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
                Set<DragItem> correctDragItems = dndQuestion.getCorrectDragItemsForDropLocation(dropLocation);
                DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(dropLocation);

                // count the number of drop locations that were meant to not stay empty
                if (!correctDragItems.isEmpty()) {
                    mappedDropLocations++;
                }

                // invalid drop location or invalid drag item => always correct
                if (dropLocation.isInvalid() || (selectedDragItem != null && selectedDragItem.isInvalid())) {
                    // but points are only given for drop locations that were meant to not stay empty
                    if (!correctDragItems.isEmpty()) {
                        correctMappings++;
                    }
                }
                else {
                    // check if user's mapping is correct
                    if (dropLocation.isDropLocationCorrect(dndAnswer)) {
                        // points are only given for drop locations that were meant to not stay empty
                        if (!correctDragItems.isEmpty()) {
                            correctMappings++;
                        }
                    }
                    else {
                        // wrong mappings always deduct points
                        incorrectMappings++;
                    }
                }
            }

            // calculate the fraction of the total score the user should get
            // every correct mapping increases fraction by 1/mappedDropLocations,
            // every incorrect mapping decreases fraction by 1/mappedDropLocations
            double fraction = ((correctMappings / mappedDropLocations) - (incorrectMappings / mappedDropLocations));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, quizQuestion.getPoints() * fraction);
        }
        // the submitted answer's type doesn't fit the quizQuestion's type => it cannot be correct
        return 0.0;
    }
}
