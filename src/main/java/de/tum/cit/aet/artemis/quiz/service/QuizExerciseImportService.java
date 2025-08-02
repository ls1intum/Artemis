package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
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
@Lazy
@Service
public class QuizExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseImportService.class);

    private final QuizExerciseService quizExerciseService;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public QuizExerciseImportService(QuizExerciseService quizExerciseService, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ChannelService channelService, FeedbackService feedbackService, Optional<CompetencyProgressApi> competencyProgressApi) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.quizExerciseService = quizExerciseService;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
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

        QuizExercise finalNewQuizExercise = newQuizExercise;
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(finalNewQuizExercise));
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
        // The new exercise should not immediately be open for practice
        newExercise.setIsOpenForPractice(false);
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
            switch (quizQuestion) {
                case MultipleChoiceQuestion mcQuestion -> setUpMultipleChoiceQuestionForImport(mcQuestion);
                case DragAndDropQuestion dndQuestion -> setUpDragAndDropQuestionForImport(dndQuestion);
                case ShortAnswerQuestion saQuestion -> setUpShortAnswerQuestionForImport(saQuestion);
                default -> {
                }
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
            URI backgroundFileIntendedPath = URI.create(FileUtil.BACKGROUND_FILE_SUBPATH);
            FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(dndQuestion.getBackgroundFilePath());
            // If it doesn't exist yet, it is a new image and will be added later.
            if (Files.exists(FilePathConverter.fileSystemPathForExternalUri(backgroundFilePublicPath, FilePathType.DRAG_AND_DROP_BACKGROUND))) {
                // Check whether pictureFilePublicPath is actually a picture file path
                // (which is the case when its path starts with the path backgroundFileIntendedPath)
                FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(backgroundFilePublicPath, backgroundFileIntendedPath);
                // Need to copy the file and get a new path, otherwise two different questions would share the same image and would cause problems in case one was deleted
                Path oldPath = FilePathConverter.fileSystemPathForExternalUri(backgroundFilePublicPath, FilePathType.DRAG_AND_DROP_BACKGROUND);
                Path newPath = FileUtil.copyExistingFileToTarget(oldPath, FilePathConverter.getDragAndDropBackgroundFilePath(), FilePathType.DRAG_AND_DROP_BACKGROUND);
                dndQuestion.setBackgroundFilePath(FilePathConverter.externalUriForFileSystemPath(newPath, FilePathType.DRAG_AND_DROP_BACKGROUND, null).toString());
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
            URI pictureFileIntendedPath = URI.create(FileUtil.PICTURE_FILE_SUBPATH);
            FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(dragItem.getPictureFilePath());
            if (Files.exists(FilePathConverter.fileSystemPathForExternalUri(pictureFilePublicPath, FilePathType.DRAG_ITEM))) {
                // Check whether pictureFilePublicPath is actually a picture file path
                // (which is the case when its path starts with the path pictureFileIntendedPath)
                FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(pictureFilePublicPath, pictureFileIntendedPath);
                // Need to copy the file and get a new path, same as above
                Path oldDragItemPath = FilePathConverter.fileSystemPathForExternalUri(pictureFilePublicPath, FilePathType.DRAG_ITEM);
                Path newDragItemPath = FileUtil.copyExistingFileToTarget(oldDragItemPath, FilePathConverter.getDragItemFilePath(), FilePathType.DRAG_ITEM);
                dragItem.setPictureFilePath(FilePathConverter.externalUriForFileSystemPath(newDragItemPath, FilePathType.DRAG_ITEM, null).toString());
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

    /**
     * Prepares a short answer question for import by creating new instances of spots, solutions, and mappings.
     * This method creates copies of the spots and solutions, resetting their identifiers and mappings.
     * It then recreates the correct mappings by linking them to the new spots and solutions using either
     * persistent IDs or temporary IDs from the original objects.
     *
     * @param saQuestion the short answer question to set up for import
     */
    private void setUpShortAnswerQuestionForImport(ShortAnswerQuestion saQuestion) {
        HashMap<Long, ShortAnswerSpot> newSpotMap = new HashMap<>();
        List<ShortAnswerSpot> newSpots = new ArrayList<>();
        for (ShortAnswerSpot oldSpot : saQuestion.getSpots()) {
            ShortAnswerSpot newSpot = new ShortAnswerSpot();
            newSpot.setSpotNr(oldSpot.getSpotNr());
            newSpot.setWidth(oldSpot.getWidth());
            newSpot.setInvalid(oldSpot.isInvalid());
            newSpot.setQuestion(saQuestion);
            newSpot.setMappings(new HashSet<>());
            Long key = oldSpot.getId() != null ? oldSpot.getId() : oldSpot.getTempID();
            newSpotMap.put(key, newSpot);
            newSpots.add(newSpot);
        }
        saQuestion.setSpots(newSpots);

        HashMap<Long, ShortAnswerSolution> newSolutionMap = new HashMap<>();
        List<ShortAnswerSolution> newSolutions = new ArrayList<>();
        for (ShortAnswerSolution oldSolution : saQuestion.getSolutions()) {
            ShortAnswerSolution newSolution = new ShortAnswerSolution();
            newSolution.setText(oldSolution.getText());
            newSolution.setInvalid(oldSolution.isInvalid());
            newSolution.setQuestion(saQuestion);
            newSolution.setMappings(new HashSet<>());
            Long key = oldSolution.getId() != null ? oldSolution.getId() : oldSolution.getTempID();
            newSolutionMap.put(key, newSolution);
            newSolutions.add(newSolution);
        }
        saQuestion.setSolutions(newSolutions);

        List<ShortAnswerMapping> newShortAnswerMappings = new ArrayList<>();
        for (ShortAnswerMapping oldMapping : saQuestion.getCorrectMappings()) {
            ShortAnswerMapping newMapping = new ShortAnswerMapping();
            newMapping.setInvalid(oldMapping.isInvalid());
            newMapping.setQuestion(saQuestion);

            Long solutionKey = null;
            if (oldMapping.getSolution() != null) {
                solutionKey = oldMapping.getSolution().getId() != null ? oldMapping.getSolution().getId() : oldMapping.getSolution().getTempID();
            }
            if (solutionKey != null) {
                newMapping.setSolution(newSolutionMap.get(solutionKey));
            }

            Long spotKey = null;
            if (oldMapping.getSpot() != null) {
                spotKey = oldMapping.getSpot().getId() != null ? oldMapping.getSpot().getId() : oldMapping.getSpot().getTempID();
            }
            if (spotKey != null) {
                newMapping.setSpot(newSpotMap.get(spotKey));
            }

            newShortAnswerMappings.add(newMapping);
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
