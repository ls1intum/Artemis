package de.tum.in.www1.artemis.domain.scoring;

import de.tum.in.www1.artemis.domain.*;

import java.util.HashSet;
import java.util.Set;

public class ScoringStrategyShortAnswerUtil {

    public static double[] getCorrectAndIncorrectSolutionsrShortAnswerQuestion(ShortAnswerQuestion shortAnswerQuestion, ShortAnswerSubmittedAnswer shortAnswerAnswer) {
        boolean foundCorrectSolution = false;
        double correctSolutions = 0;
        double incorrectSolutions = 0;
        Set<String> solutionsAlreadyUsed = new HashSet<>();

        // iterate through each spot and compare its correct solutions with the submitted texts
        for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
            Set<ShortAnswerSolution> solutionsForSpot = shortAnswerQuestion.getCorrectSolutionForSpot(spot);
            for(ShortAnswerSubmittedText text : shortAnswerAnswer.getSubmittedTexts()){
                foundCorrectSolution = false;
                if(text.getSpot().equals(spot)){
                    for(ShortAnswerSolution solution : solutionsForSpot){
                            /*submittedText answers can be the same (more than once)
                            when spots do not exist more than once*/
                        if(text.isSubmittedTextCorrect(text.getText(), solution.getText())
                            && (!shortAnswerAnswer.submittedTextMoreThanOnceInSubmittedAnswer(text) || !shortAnswerQuestion.spotMoreThanOnceInMapping(spot))
                            ){
                            text.setIsCorrect(true);
                            correctSolutions++;
                            foundCorrectSolution = true;
                            break;
                        }
                        /*submittedText answers cannot be the same (more than once) */
                        if(text.isSubmittedTextCorrect(text.getText(), solution.getText())
                            && shortAnswerAnswer.submittedTextMoreThanOnceInSubmittedAnswer(text)
                            && shortAnswerQuestion.spotMoreThanOnceInMapping(spot)
                            && shortAnswerQuestion.solutionMoreThanOnceInMapping(solution)
                            && !solutionsAlreadyUsed.contains(solution.getText().toLowerCase())
                            ){
                            solutionsAlreadyUsed.add(solution.getText().toLowerCase());
                            text.setIsCorrect(true);
                            correctSolutions++;
                            foundCorrectSolution = true;
                            break;
                        }
                    }
                    if(!foundCorrectSolution){
                        incorrectSolutions++;
                    }
                }
            }
        }
        double[] values = {correctSolutions,incorrectSolutions};
        return values;
    }
}
