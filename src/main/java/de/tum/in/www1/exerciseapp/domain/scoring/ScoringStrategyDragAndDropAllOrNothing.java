package de.tum.in.www1.exerciseapp.domain.scoring;

import de.tum.in.www1.exerciseapp.domain.*;

import java.util.Set;

public class ScoringStrategyDragAndDropAllOrNothing implements ScoringStrategy {
    // All or nothing means we get the full score if the answer is 100% correct, and 0 points otherwise
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        if (submittedAnswer instanceof DragAndDropSubmittedAnswer && question instanceof DragAndDropQuestion) {
            DragAndDropSubmittedAnswer dndAnswer = (DragAndDropSubmittedAnswer) submittedAnswer;
            DragAndDropQuestion dndQuestion = (DragAndDropQuestion) question;
            // iterate through each drop location and compare its correct mappings with the answer's mapping
            for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
                Set<DragItem> correctDragItems = dndQuestion.getCorrectDragItemsForDropLocation(dropLocation);
                DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(dropLocation);

                if ((correctDragItems.size() == 0 && selectedDragItem == null) ||
                    (selectedDragItem != null && correctDragItems.contains(selectedDragItem))) {
                    // this drop location was meant to stay empty and user didn't drag anything onto it
                    // OR the user dragged one of the correct drag items onto this drop location
                    // => this is correct => Do nothing
                } else {
                    // incorrect => entire answer can no longer be 100 % correct
                    return 0.0;
                }
            }
            // the user wasn't wrong about a single drop location => the answer is 100% correct
            return dndQuestion.getScore();
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return 0.0;
        }
    }
}
