package de.tum.in.www1.artemis.exercise.quizexercise;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;

/**
 * Factory for creating QuizExercises and related objects.
 */
public class QuizExerciseFactory {

    public static QuizBatch generateQuizBatch(QuizExercise quizExercise, ZonedDateTime startTime) {
        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizBatch.setStartTime(startTime);
        return quizBatch;
    }

    public static QuizExercise generateQuizExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode, Course course) {
        QuizExercise quizExercise = (QuizExercise) ExerciseFactory.populateExercise(new QuizExercise(), releaseDate, dueDate, null, course);
        quizExercise.setTitle("my cool quiz title");

        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setRandomizeQuestionOrder(true);
        quizExercise.setQuizMode(quizMode);
        if (quizMode == QuizMode.SYNCHRONIZED) {
            quizExercise.setQuizBatches(Set.of(generateQuizBatch(quizExercise, releaseDate)));
        }
        return quizExercise;
    }

    public static QuizExercise generateQuizExerciseForExam(ExerciseGroup exerciseGroup) {
        var quizExercise = (QuizExercise) ExerciseFactory.populateExerciseForExam(new QuizExercise(), exerciseGroup);

        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setQuizPointStatistic(new QuizPointStatistic());
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                question.setQuizQuestionStatistic(new DragAndDropQuestionStatistic());
            }
            else if (question instanceof MultipleChoiceQuestion) {
                question.setQuizQuestionStatistic(new MultipleChoiceQuestionStatistic());
            }
            else {
                question.setQuizQuestionStatistic(new ShortAnswerQuestionStatistic());
            }
        }
        quizExercise.setRandomizeQuestionOrder(true);

        return quizExercise;
    }
}
