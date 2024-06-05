package de.tum.in.www1.artemis.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestionStatistic;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestionStatistic;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestionStatistic;
import de.tum.in.www1.artemis.service.quiz.QuizExerciseService;
import de.tum.in.www1.artemis.service.quiz.QuizIdAssigner;

@Service
public class QuizUpdater {

    @Autowired
    private QuizExerciseService quizExerciseService;

    /**
     * Updates the quiz questions in a given QuizExercise object by initializing statistics for each question if not already present,
     * and fixing references and assigning IDs for different types of quiz questions (Multiple Choice, Drag and Drop, Short Answer).
     *
     * For Multiple Choice questions, it fixes references, assigns IDs to answer options and answer counters.
     * For Drag and Drop questions, it fixes references, restores correct mappings from indices, and assigns IDs to drop location counters.
     * For Short Answer questions, it fixes references, restores correct mappings from indices, and assigns IDs to short answer spot counters.
     *
     * @param quizExercise the QuizExercise object containing the quiz questions to be updated
     */
    public void updateQuizQuestions(QuizExercise quizExercise) {
        for (var quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() == null) {
                quizQuestion.initializeStatistic();
            }

            if (quizQuestion instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                quizExerciseService.fixReferenceMultipleChoice(multipleChoiceQuestion);
                QuizIdAssigner.assignIds(multipleChoiceQuestion.getAnswerOptions());
                QuizIdAssigner.assignIds(((MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic()).getAnswerCounters());
            }
            else if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                quizExerciseService.fixReferenceDragAndDrop(dragAndDropQuestion);
                quizExerciseService.restoreCorrectMappingsFromIndicesDragAndDrop(dragAndDropQuestion);
                QuizIdAssigner.assignIds(((DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic()).getDropLocationCounters());
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                quizExerciseService.fixReferenceShortAnswer(shortAnswerQuestion);
                quizExerciseService.restoreCorrectMappingsFromIndicesShortAnswer(shortAnswerQuestion);
                QuizIdAssigner.assignIds(((ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic()).getShortAnswerSpotCounters());
            }
        }
    }
}
