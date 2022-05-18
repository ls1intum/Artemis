package de.tum.in.www1.artemis.service;

import java.util.HashMap;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.AnswerOption;
import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

@Service
public class QuizExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseImportService.class);

    private final QuizExerciseService quizExerciseService;

    public QuizExerciseImportService(QuizExerciseService quizExerciseService, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository);
        this.quizExerciseService = quizExerciseService;
    }

    /**
     * Imports a quiz exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyQuizExerciseBasis(QuizExercise)} to set up the basis of the exercise and
     * {@link #copyQuizQuestions(QuizExercise, QuizExercise)} for a hard copy of the questions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public QuizExercise importQuizExercise(final QuizExercise templateExercise, QuizExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        QuizExercise newExercise = copyQuizExerciseBasis(importedExercise);
        copyQuizQuestions(importedExercise, newExercise);
        return quizExerciseService.save(newExercise);
    }

    /** This helper method copies all attributes of the {@code importedExercise} into a new exercise.
     * Here we ignore all external entities as well as the start-, end-, and asseessment due date.
     *
     * @param importedExercise The exercise from which to copy the basis
     * @return the cloned QuizExercise basis
     */
    @NotNull
    private QuizExercise copyQuizExerciseBasis(QuizExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        QuizExercise newExercise = new QuizExercise();

        super.copyExerciseBasis(newExercise, importedExercise, new HashMap<>());
        newExercise.setRandomizeQuestionOrder(importedExercise.isRandomizeQuestionOrder());
        newExercise.setAllowedNumberOfAttempts(importedExercise.getAllowedNumberOfAttempts());
        newExercise.setRemainingNumberOfAttempts(importedExercise.getRemainingNumberOfAttempts());
        newExercise.setIsOpenForPractice(importedExercise.isIsOpenForPractice());
        newExercise.setQuizMode(importedExercise.getQuizMode());
        newExercise.setDuration(importedExercise.getDuration());
        return newExercise;
    }

    /** This helper method copies all questions of the {@code importedExercise} into a new exercise.
     *
     * @param importedExercise The exercise from which to copy the questions
     * @param newExercise The exercise to which the questions are copied
     */
    private void copyQuizQuestions(QuizExercise importedExercise, QuizExercise newExercise) {
        log.debug("Copying the QuizQuestions to new QuizExercise: {}", newExercise);

        for (QuizQuestion quizQuestion : importedExercise.getQuizQuestions()) {
            quizQuestion.setId(null);
            quizQuestion.setQuizQuestionStatistic(null);
            if (quizQuestion instanceof MultipleChoiceQuestion) {
                List<AnswerOption> answerOptions = ((MultipleChoiceQuestion) quizQuestion).getAnswerOptions();
                for (AnswerOption answerOption : answerOptions) {
                    answerOption.setId(null);
                    answerOption.setQuestion((MultipleChoiceQuestion) quizQuestion);
                }
                ((MultipleChoiceQuestion) quizQuestion).setAnswerOptions(((MultipleChoiceQuestion) quizQuestion).getAnswerOptions());
            }
            quizQuestion.setExercise(newExercise);
        }
        newExercise.setQuizQuestions(importedExercise.getQuizQuestions());
    }
}
