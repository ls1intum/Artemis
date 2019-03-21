package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScoringStrategyShortAnswerUtil {

    /**
     * TODO Francisco describe this method in detail here
     * @param shortAnswerQuestion
     * @param shortAnswerAnswer
     * @return
     */
    //TODO I don't really like the int array return type. Instead we should use something different here, either an object or
    public static int[] getCorrectAndIncorrectSolutionCount(ShortAnswerQuestion shortAnswerQuestion, ShortAnswerSubmittedAnswer shortAnswerAnswer) {
        boolean foundCorrectSolution;
        int correctSolutionsCount = 0;
        int incorrectSolutionsCount = 0;
        List<ShortAnswerSolution> notUsedSolutions = new ArrayList<>(shortAnswerQuestion.getSolutions());

        // iterate through each spot and compare its correct solutions with the submitted texts
        for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
            if(spot.isInvalid()) {
                correctSolutionsCount++;
                continue;
            }

            Set<ShortAnswerSolution> solutionsForSpot = shortAnswerQuestion.getCorrectSolutionForSpot(spot);
            ShortAnswerSubmittedText submittedTextForSpot = shortAnswerAnswer.getSubmittedTextForSpot(spot);
            foundCorrectSolution = false;

            if(submittedTextForSpot != null) {
                submittedTextForSpot.setIsCorrect(false);
                for (ShortAnswerSolution solution : solutionsForSpot) {
                    if (submittedTextForSpot.isSubmittedTextCorrect(submittedTextForSpot.getText(), solution.getText())
                        && notUsedSolutions.contains(solution)) {
                        notUsedSolutions.remove(solution);
                        submittedTextForSpot.setIsCorrect(true);
                        correctSolutionsCount++;
                        foundCorrectSolution = true;
                        break;
                    }
                }
                if (!foundCorrectSolution) {
                    incorrectSolutionsCount++;
                }
            }
        }
        int[] values = {correctSolutionsCount, incorrectSolutionsCount};
        return values;
    }
}
