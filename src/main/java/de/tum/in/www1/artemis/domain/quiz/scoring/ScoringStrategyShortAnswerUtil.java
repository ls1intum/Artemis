package de.tum.in.www1.artemis.domain.quiz.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.quiz.*;

public class ScoringStrategyShortAnswerUtil {

    /**
     * Get number of correct and incorrect solutions for the short answer question
     * 
     * @param shortAnswerQuestion for which the correct and incorrect solutions should be counted
     * @param shortAnswerAnswer for the given short answer question
     * @return array with correct and incorrect solution count
     */
    // TODO I don't really like the int array return type. Instead we should use something different here, either an object or
    public static int[] getCorrectAndIncorrectSolutionCount(ShortAnswerQuestion shortAnswerQuestion, ShortAnswerSubmittedAnswer shortAnswerAnswer) {
        boolean foundCorrectSolution;
        int correctSolutionsCount = 0;
        int incorrectSolutionsCount = 0;
        List<ShortAnswerSolution> notUsedSolutions = new ArrayList<>(shortAnswerQuestion.getSolutions());

        // iterate through each spot and compare its correct solutions with the submitted texts
        for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
            if (spot.isInvalid() == Boolean.TRUE) {
                correctSolutionsCount++;
                continue;
            }

            Set<ShortAnswerSolution> solutionsForSpot = shortAnswerQuestion.getCorrectSolutionForSpot(spot);
            ShortAnswerSubmittedText submittedTextForSpot = shortAnswerAnswer.getSubmittedTextForSpot(spot);
            foundCorrectSolution = false;

            if (submittedTextForSpot != null) {
                submittedTextForSpot.setIsCorrect(false);
                for (ShortAnswerSolution solution : solutionsForSpot) {
                    if (submittedTextForSpot.isSubmittedTextCorrect(submittedTextForSpot.getText(), solution.getText()) && notUsedSolutions.contains(solution)) {
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
        int[] values = { correctSolutionsCount, incorrectSolutionsCount };
        return values;
    }
}
