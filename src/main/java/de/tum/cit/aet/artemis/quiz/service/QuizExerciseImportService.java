package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@Profile(PROFILE_CORE)
@Service
public class QuizExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseImportService.class);

    private final QuizExerciseService quizExerciseService;

    private final FileService fileService;

    private final ChannelService channelService;

    private final CompetencyProgressService competencyProgressService;

    public QuizExerciseImportService(QuizExerciseService quizExerciseService, FileService fileService, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService, FeedbackService feedbackService,
            CompetencyProgressService competencyProgressService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.quizExerciseService = quizExerciseService;
        this.fileService = fileService;
        this.channelService = channelService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Imports a quiz exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyQuizExerciseBasis(QuizExercise)} to set up the basis of the exercise and
     * {@link #copyQuizQuestions(QuizExercise, QuizExercise)} for a hard copy of the questions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @param files            The potential files to be added. Null if no change to files during import. ExamImportService sends null by default
     * @return The newly created exercise
     */
    @NotNull
    public QuizExercise importQuizExercise(final QuizExercise templateExercise, QuizExercise importedExercise, @Nullable List<MultipartFile> files) throws IOException {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        QuizExercise newExercise = copyQuizExerciseBasis(importedExercise);
        copyQuizQuestions(importedExercise, newExercise);
        copyQuizBatches(importedExercise, newExercise);

        QuizExercise newQuizExercise = quizExerciseService.save(newExercise);

        channelService.createExerciseChannel(newQuizExercise, Optional.ofNullable(importedExercise.getChannelName()));

        competencyProgressService.updateProgressByLearningObjectAsync(newQuizExercise);
        if (files != null) {
            newQuizExercise = quizExerciseService.save(quizExerciseService.uploadNewFilesToNewImportedQuiz(newQuizExercise, files));
        }

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
     * This helper method copies all questions of the {@code sourceExercise} into a new exercise.
     *
     * @param sourceExercise The exercise from which to copy the questions
     * @param newExercise    The exercise to which the questions are copied
     */
    private void copyQuizQuestions(QuizExercise sourceExercise, QuizExercise newExercise) {
        log.debug("Copying the QuizQuestions to new QuizExercise: {}", newExercise);

        List<QuizQuestion> newQuestions = new ArrayList<>();
        for (QuizQuestion quizQuestion : sourceExercise.getQuizQuestions()) {
            quizQuestion.setId(null);
            quizQuestion.setQuizQuestionStatistic(null);
            if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                setUpMultipleChoiceQuestionForImport(mcQuestion);
            }
            else if (quizQuestion instanceof DragAndDropQuestion dndQuestion) {
                setUpDragAndDropQuestionForImport(dndQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion saQuestion) {
                setUpShortAnswerQuestionForImport(saQuestion);
            }
            quizQuestion.setExercise(newExercise);

            newQuestions.add(quizQuestion);
        }
        newExercise.setQuizQuestions(newQuestions);
    }

    private void setUpMultipleChoiceQuestionForImport(MultipleChoiceQuestion mcQuestion) {
        List<AnswerOption> newAnswerOptions = new ArrayList<>();
        for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
            answerOption.setId(null);
            answerOption.setQuestion(mcQuestion);

            newAnswerOptions.add(answerOption);
        }
        mcQuestion.setAnswerOptions(newAnswerOptions);
    }

    private void setUpDragAndDropQuestionForImport(DragAndDropQuestion dndQuestion) {
        if (dndQuestion.getBackgroundFilePath() != null) {
            URI backgroundFilePublicPath = URI.create(dndQuestion.getBackgroundFilePath());
            URI backgroundFileIntendedPath = URI.create(FileService.BACKGROUND_FILE_SUBPATH);
            // Check whether pictureFilePublicPath is actually a picture file path
            // (which is the case when its path starts with the path backgroundFileIntendedPath)
            // If it is null it is a new image which doesn't exist yet and will be added later.
            if (FilePathService.actualPathForPublicPath(backgroundFilePublicPath) != null) {
                FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(backgroundFilePublicPath, backgroundFileIntendedPath);
                // Need to copy the file and get a new path, otherwise two different questions would share the same image and would cause problems in case one was deleted
                Path oldPath = FilePathService.actualPathForPublicPath(backgroundFilePublicPath);
                Path newPath = fileService.copyExistingFileToTarget(oldPath, FilePathService.getDragAndDropBackgroundFilePath());
                dndQuestion.setBackgroundFilePath(FilePathService.publicPathForActualPathOrThrow(newPath, null).toString());
            }
        }
        else {
            log.warn("BackgroundFilePath of DragAndDropQuestion {} is null", dndQuestion.getId());
        }

        List<DropLocation> newDropLocations = new ArrayList<>();
        for (DropLocation dropLocation : dndQuestion.getDropLocations()) {
            dropLocation.setId(null);
            dropLocation.setQuestion(dndQuestion);
            dropLocation.setMappings(new HashSet<>());

            newDropLocations.add(dropLocation);
        }
        dndQuestion.setDropLocations(newDropLocations);

        setUpDragItemsForImport(dndQuestion);
        setUpDragAndDropMappingsForImport(dndQuestion);
    }

    private void setUpDragItemsForImport(DragAndDropQuestion dndQuestion) {
        List<DragItem> newDragItems = new ArrayList<>();
        for (DragItem dragItem : dndQuestion.getDragItems()) {
            dragItem.setId(null);
            dragItem.setQuestion(dndQuestion);
            dragItem.setMappings(new HashSet<>());

            newDragItems.add(dragItem);

            if (dragItem.getPictureFilePath() == null) {
                continue;
            }

            URI pictureFilePublicPath = URI.create(dragItem.getPictureFilePath());
            URI pictureFileIntendedPath = URI.create(FileService.PICTURE_FILE_SUBPATH);
            // Check whether pictureFilePublicPath is actually a picture file path
            // (which is the case when its path starts with the path pictureFileIntendedPath)
            // If it is null it is a new image which doesn't exist yet and will be added later.
            if (FilePathService.actualPathForPublicPath(pictureFilePublicPath) != null) {
                FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(pictureFilePublicPath, pictureFileIntendedPath);
                // Need to copy the file and get a new path, same as above
                Path oldDragItemPath = FilePathService.actualPathForPublicPath(pictureFilePublicPath);
                Path newDragItemPath = fileService.copyExistingFileToTarget(oldDragItemPath, FilePathService.getDragItemFilePath());
                dragItem.setPictureFilePath(FilePathService.publicPathForActualPathOrThrow(newDragItemPath, null).toString());
            }
        }
        dndQuestion.setDragItems(newDragItems);
    }

    private void setUpDragAndDropMappingsForImport(DragAndDropQuestion dndQuestion) {
        List<DragAndDropMapping> newDragAndDropMappings = new ArrayList<>();
        for (DragAndDropMapping dragAndDropMapping : dndQuestion.getCorrectMappings()) {
            dragAndDropMapping.setId(null);
            dragAndDropMapping.setQuestion(dndQuestion);
            if (dragAndDropMapping.getDragItemIndex() != null) {
                dragAndDropMapping.setDragItem(dndQuestion.getDragItems().get(dragAndDropMapping.getDragItemIndex()));
            }
            if (dragAndDropMapping.getDropLocationIndex() != null) {
                dragAndDropMapping.setDropLocation(dndQuestion.getDropLocations().get(dragAndDropMapping.getDropLocationIndex()));
            }

            newDragAndDropMappings.add(dragAndDropMapping);
        }
        dndQuestion.setCorrectMappings(newDragAndDropMappings);
    }

    private void setUpShortAnswerQuestionForImport(ShortAnswerQuestion saQuestion) {
        List<ShortAnswerSpot> newShortAnswerSpots = new ArrayList<>();
        for (ShortAnswerSpot shortAnswerSpot : saQuestion.getSpots()) {
            shortAnswerSpot.setId(null);
            shortAnswerSpot.setQuestion(saQuestion);
            shortAnswerSpot.setMappings(new HashSet<>());

            newShortAnswerSpots.add(shortAnswerSpot);
        }
        saQuestion.setSpots(newShortAnswerSpots);

        List<ShortAnswerSolution> newShortAnswerSolutions = new ArrayList<>();
        for (ShortAnswerSolution shortAnswerSolution : saQuestion.getSolutions()) {
            shortAnswerSolution.setId(null);
            shortAnswerSolution.setQuestion(saQuestion);
            shortAnswerSolution.setMappings(new HashSet<>());

            newShortAnswerSolutions.add(shortAnswerSolution);
        }
        saQuestion.setSolutions(newShortAnswerSolutions);

        List<ShortAnswerMapping> newShortAnswerMappings = new ArrayList<>();
        for (ShortAnswerMapping shortAnswerMapping : saQuestion.getCorrectMappings()) {
            shortAnswerMapping.setId(null);
            shortAnswerMapping.setQuestion(saQuestion);
            if (shortAnswerMapping.getShortAnswerSolutionIndex() != null) {
                shortAnswerMapping.setSolution(saQuestion.getSolutions().get(shortAnswerMapping.getShortAnswerSolutionIndex()));
            }
            if (shortAnswerMapping.getShortAnswerSpotIndex() != null) {
                shortAnswerMapping.setSpot(saQuestion.getSpots().get(shortAnswerMapping.getShortAnswerSpotIndex()));
            }
            newShortAnswerMappings.add(shortAnswerMapping);
        }
        saQuestion.setCorrectMappings(newShortAnswerMappings);
    }

    /**
     * This helper method copies all batches of the {@code sourceExercise} into a new exercise.
     *
     * @param sourceExercise The exercise from which to copy the batches
     * @param newExercise    The exercise to which the batches are copied
     */
    private void copyQuizBatches(QuizExercise sourceExercise, QuizExercise newExercise) {
        log.debug("Copying the QuizBatches to new QuizExercise: {}", newExercise);

        Set<QuizBatch> quizBatchList = new HashSet<>();
        for (QuizBatch batch : sourceExercise.getQuizBatches()) {
            batch.setId(null);
            batch.setQuizExercise(newExercise);
            quizBatchList.add(batch);
        }
        newExercise.setQuizBatches(quizBatchList);
    }
}
