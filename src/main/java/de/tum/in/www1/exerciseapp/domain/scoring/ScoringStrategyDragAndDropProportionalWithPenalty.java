package de.tum.in.www1.exerciseapp.domain.scoring;

import de.tum.in.www1.exerciseapp.domain.*;

import java.util.Set;

/**
 * Proportional with Penalty means that
 * every correct mapping increases the score by x and
 * every incorrect mapping decreases the score by x
 * where x = maxScore / numberOfDropLocationsThatShouldHaveAMapping
 * if the result is negative, a score of 0 is given instead
 */
public class ScoringStrategyDragAndDropProportionalWithPenalty implements ScoringStrategy {
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (submittedAnswer instanceof DragAndDropSubmittedAnswer && question instanceof DragAndDropQuestion) {
            DragAndDropSubmittedAnswer dndAnswer = (DragAndDropSubmittedAnswer) submittedAnswer;
            DragAndDropQuestion dndQuestion = (DragAndDropQuestion) question;

            double mappedDropLocations = 0;
            double correctMappings = 0;
            double incorrectMappings = 0;

            // iterate through each drop location and compare its correct mappings with the answer's mapping
            for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
                Set<DragItem> correctDragItems = dndQuestion.getCorrectDragItemsForDropLocation(dropLocation);
                DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(dropLocation);

                // count the number of drop locations that should have a drag item
                if (correctDragItems.size() > 0) {
                    mappedDropLocations++;
                }

                // count the number of correct and incorrect mappings
                if ((correctDragItems.size() == 0 && selectedDragItem == null) ||
                    (selectedDragItem != null && correctDragItems.contains(selectedDragItem))) {
                    // this drop location was meant to stay empty and user didn't drag anything onto it
                    // OR the user dragged one of the correct drag items onto this drop location
                    // => this is correct
                    correctMappings++;
                } else {
                    // incorrect
                    incorrectMappings++;
                }
            }
            // calculate the fraction of the total score the user should get
            // every correct mapping increases fraction by 1/mappedDropLocations,
            // every incorrect mapping decreases fraction by 1/mappedDropLocations
            double fraction = ((correctMappings / mappedDropLocations) - (incorrectMappings / mappedDropLocations));

            // end result is maxScore * fraction, but at least 0
            return Math.max(0, question.getScore() * fraction);
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return 0.0;
        }
    }
}
