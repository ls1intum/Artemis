package de.tum.in.www1.artemis.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
import de.tum.in.www1.artemis.service.quiz.QuizService;

@Service
public class QuizUpdaterService {

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
                invokePrivateMethod("fixReferenceMultipleChoice", multipleChoiceQuestion);

                QuizIdAssigner.assignIds(multipleChoiceQuestion.getAnswerOptions());
                QuizIdAssigner.assignIds(((MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic()).getAnswerCounters());
            }
            else if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                invokePrivateMethod("fixReferenceDragAndDrop", dragAndDropQuestion);
                invokePrivateMethod("restoreCorrectMappingsFromIndicesDragAndDrop", dragAndDropQuestion);

                QuizIdAssigner.assignIds(((DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic()).getDropLocationCounters());
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                invokePrivateMethod("fixReferenceShortAnswer", shortAnswerQuestion);
                invokePrivateMethod("restoreCorrectMappingsFromIndicesShortAnswer", shortAnswerQuestion);

                QuizIdAssigner.assignIds(((ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic()).getShortAnswerSpotCounters());
            }
        }
    }

    /**
     * Invokes a private method on a class instance using reflection.
     *
     * @param methodName The name of the method to be invoked.
     * @param parameter  The parameter to be passed to the method.
     * @throws RuntimeException If the method cannot be found, accessed, or invoked.
     */
    private void invokePrivateMethod(String methodName, Object parameter) {
        try {
            Method privateMethod = QuizService.class.getDeclaredMethod(methodName, parameter.getClass());
            privateMethod.setAccessible(true);
            privateMethod.invoke(quizExerciseService, parameter);
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
