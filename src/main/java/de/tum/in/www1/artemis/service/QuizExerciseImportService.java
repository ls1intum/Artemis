package de.tum.in.www1.artemis.service;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;

@Service
public class QuizExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseImportService.class);

    private final QuizExerciseService quizExerciseService;

    private final FileService fileService;

    private final FilePathService filePathService;

    private final ChannelService channelService;

    public QuizExerciseImportService(QuizExerciseService quizExerciseService, FileService fileService, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService, FeedbackService feedbackService,
            FilePathService filePathService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.quizExerciseService = quizExerciseService;
        this.fileService = fileService;
        this.channelService = channelService;
        this.filePathService = filePathService;
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
        copyQuizBatches(importedExercise, newExercise);

        QuizExercise newQuizExercise = quizExerciseService.save(newExercise);

        channelService.createExerciseChannel(newQuizExercise, Optional.ofNullable(importedExercise.getChannelName()));
        return newQuizExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into a new exercise.
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

    /**
     * This helper method copies all questions of the {@code importedExercise} into a new exercise.
     *
     * @param importedExercise The exercise from which to copy the questions
     * @param newExercise      The exercise to which the questions are copied
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
                if (dndQuestion.getBackgroundFilePath() != null) {
                    // Need to copy the file and get a new path, otherwise two different questions would share the same image and would cause problems in case one was deleted
                    Path oldPath = filePathService.actualPathForPublicPath(URI.create(dndQuestion.getBackgroundFilePath()));
                    Path newPath = fileService.copyExistingFileToTarget(oldPath, FilePathService.getDragAndDropBackgroundFilePath());
                    dndQuestion.setBackgroundFilePath(filePathService.publicPathForActualPath(newPath, null).toString());
                }
                else {
                    log.warn("BackgroundFilePath of DragAndDropQuestion {} is null", dndQuestion.getId());
                }

                for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
                    dropLocation.setId(null);
                    dropLocation.setQuestion(dndQuestion);
                }
                for (DragItem dragItem : dndQuestion.getDragItems()) {
                    dragItem.setId(null);
                    dragItem.setQuestion(dndQuestion);
                    if (dragItem.getPictureFilePath() != null) {
                        // Need to copy the file and get a new path, same as above
                        Path oldDragItemPath = filePathService.actualPathForPublicPath(URI.create(dragItem.getPictureFilePath()));
                        Path newDragItemPath = fileService.copyExistingFileToTarget(oldDragItemPath, FilePathService.getDragItemFilePath());
                        dragItem.setPictureFilePath(filePathService.publicPathForActualPath(newDragItemPath, null).toString());
                    }
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

    /**
     * This helper method copies all batches of the {@code importedExercise} into a new exercise.
     *
     * @param importedExercise The exercise from which to copy the batches
     * @param newExercise      The exercise to which the batches are copied
     */
    private void copyQuizBatches(QuizExercise importedExercise, QuizExercise newExercise) {
        log.debug("Copying the QuizBatches to new QuizExercise: {}", newExercise);

        Set<QuizBatch> quizBatchList = new HashSet<>();
        for (QuizBatch batch : importedExercise.getQuizBatches()) {
            batch.setId(null);
            batch.setQuizExercise(newExercise);
            quizBatchList.add(batch);
        }
        newExercise.setQuizBatches(quizBatchList);
    }
}
