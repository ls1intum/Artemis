package de.tum.in.www1.artemis.service;

import java.util.HashMap;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.*;
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
            if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                    answerOption.setId(null);
                    answerOption.setQuestion(mcQuestion);
                }
            }
            else if (quizQuestion instanceof DragAndDropQuestion dndQuestion) {
                for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
                    dropLocation.setId(null);
                    dropLocation.setQuestion(dndQuestion);
                }
                for (DragItem dragItem : dndQuestion.getDragItems()) {
                    dragItem.setId(null);
                    dragItem.setQuestion(dndQuestion);
                }
                for (DragAndDropMapping dragAndDropMapping : dndQuestion.getCorrectMappings()) {
                    dragAndDropMapping.setId(null);
                    dragAndDropMapping.setQuestion(dndQuestion);
                    if (dragAndDropMapping.getDragItemIndex() != null) {
                        dragAndDropMapping.setDragItem(dndQuestion.getDragItems().get(dragAndDropMapping.getDragItemIndex()));
                    }
                    if (dragAndDropMapping.getDropLocationIndex() != null) {
                        dragAndDropMapping.setDropLocation(dndQuestion.getDropLocations().get(dragAndDropMapping.getDropLocationIndex()));
                    }
                }
            }
            else if (quizQuestion instanceof ShortAnswerQuestion saQuestion) {
                for (ShortAnswerSpot shortAnswerSpot : saQuestion.getSpots()) {
                    shortAnswerSpot.setId(null);
                    shortAnswerSpot.setQuestion(saQuestion);
                }
                for (ShortAnswerSolution shortAnswerSolution : saQuestion.getSolutions()) {
                    shortAnswerSolution.setId(null);
                    shortAnswerSolution.setQuestion(saQuestion);
                }
                for (ShortAnswerMapping shortAnswerMapping : saQuestion.getCorrectMappings()) {
                    shortAnswerMapping.setId(null);
                    shortAnswerMapping.setQuestion(saQuestion);
                    if (shortAnswerMapping.getShortAnswerSolutionIndex() != null) {
                        shortAnswerMapping.setSolution(saQuestion.getSolutions().get(shortAnswerMapping.getShortAnswerSolutionIndex()));
                    }
                    if (shortAnswerMapping.getShortAnswerSpotIndex() != null) {
                        shortAnswerMapping.setSpot(saQuestion.getSpots().get(shortAnswerMapping.getShortAnswerSpotIndex()));
                    }
                }
            }
            quizQuestion.setExercise(newExercise);
        }
        newExercise.setQuizQuestions(importedExercise.getQuizQuestions());
    }
}
