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
     * @param shortAnswerSubmittedAnswer for the given short answer question
     * @return array with correct and incorrect solution count
     */
    public static int[] getCorrectAndIncorrectSolutionCount(ShortAnswerQuestion shortAnswerQuestion, ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        boolean foundCorrectSolution;
        int correctSolutionsCount = 0;
        int incorrectSolutionsCount = 0;
        List<ShortAnswerSolution> notUsedSolutions = new ArrayList<>(shortAnswerQuestion.getSolutions());

        // iterate through each spot and compare its correct solutions with the submitted texts
        for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
            if (Boolean.TRUE.equals(spot.isInvalid())) {
                correctSolutionsCount++;
                continue;
            }

            Set<ShortAnswerSolution> solutionsForSpot = shortAnswerQuestion.getCorrectSolutionForSpot(spot);
            ShortAnswerSubmittedText shortAnswerSubmittedText = shortAnswerSubmittedAnswer.getSubmittedTextForSpot(spot);
            foundCorrectSolution = false;

            if (shortAnswerSubmittedText != null) {
                // reconnect to avoid issues
                shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
                shortAnswerSubmittedText.setIsCorrect(false);
                for (ShortAnswerSolution solution : solutionsForSpot) {
                    if (shortAnswerSubmittedText.isSubmittedTextCorrect(shortAnswerSubmittedText.getText(), solution.getText()) && notUsedSolutions.contains(solution)) {
                        notUsedSolutions.remove(solution);
                        shortAnswerSubmittedText.setIsCorrect(true);
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
        return new int[] { correctSolutionsCount, incorrectSolutionsCount };
    }
}
