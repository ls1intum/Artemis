package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;
import java.util.HashSet;
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
            /*
            for(ShortAnswerSubmittedText submittedText : saAnswer.getSubmittedTexts()){
                submittedText.setSubmittedAnswer(saAnswer);
            } */
            ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) question;
            int correctSolutions = 0;
            Set<ShortAnswerSolution> solutionsAlreadyUsed = new HashSet<>();

            // iterate through each spot and compare its correct solutions with the submitted texts
            for (ShortAnswerSpot spot : saQuestion.getSpots()) {
                Set<ShortAnswerSolution> solutionsForSpot = saQuestion.getCorrectSolutionForSpot(spot);
                for(ShortAnswerSubmittedText text : saAnswer.getSubmittedTexts()){
                    if(text.getSpot().equals(spot)){
                        for(ShortAnswerSolution solution : solutionsForSpot){
                            /*submittedText answers can be the same (more than once)
                            when spots do not exist more than once */
                            if(text.isSubmittedTextCorrect(text.getText(), solution.getText())
                                && (!saAnswer.submittedTextMoreThanOnceInSubmittedAnswer(text) || !saQuestion.spotMoreThanOnceInMapping(spot))
                                ){
                                text.setIsCorrect(true);
                                correctSolutions++;
                                break;
                            }
                            /*submittedText answers cannot be the same (more than once) */
                            if(text.isSubmittedTextCorrect(text.getText(), solution.getText())
                                && saAnswer.submittedTextMoreThanOnceInSubmittedAnswer(text)
                                && saQuestion.spotMoreThanOnceInMapping(spot)
                                && saQuestion.solutionMoreThanOnceInMapping(solution)
                                && !solutionsAlreadyUsed.contains(solution)
                                ){
                                solutionsAlreadyUsed.add(solution);
                                text.setIsCorrect(true);
                                correctSolutions++;
                                break;
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
