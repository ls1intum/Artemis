package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;

import java.util.Set;

/**
 * All or nothing means the full score is given if the answer is 100% correct,
 * otherwise a score of 0 is given
 */
public class ScoringStrategyShortAnswerAllOrNothing implements ScoringStrategy {
    @Override
    public double calculateScore(Question question, SubmittedAnswer submittedAnswer) {
        // return maximal Score if the question is invalid
        if (question.isInvalid()) {
            return question.getScore();
        }
        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer && question instanceof ShortAnswerQuestion) {
            ShortAnswerSubmittedAnswer saAnswer = (ShortAnswerSubmittedAnswer) submittedAnswer;
            ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) question;
            int correctSolutions = 0;

            // iterate through each spot and compare its correct solutions with the submitted texts
            for (ShortAnswerSpot spot : saQuestion.getSpots()) {
                Set<ShortAnswerSolution> solutionsForSpot = saQuestion.getCorrectSolutionForSpot(spot);

                for(ShortAnswerSubmittedText text : saAnswer.getSubmittedTexts()){
                    if(text.getSpot().equals(spot)){
                        for(ShortAnswerSolution solution : solutionsForSpot){
                            //needs to be changed with better algorithm
                            if(solution.getText().equalsIgnoreCase(text.getText())){
                                correctSolutions++;
                            }
                        }
                    }
                }
            }

            if(correctSolutions == saQuestion.getSpots().size()){
                return saQuestion.getScore();
            } else {
                return 0.0;
            }
        } else {
            // the submitted answer's type doesn't fit the question's type => it cannot be correct
            return 0.0;
        }
    }
}
