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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
    @NonNull
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
    @NonNull
    private QuizExercise copyQuizExerciseBasis(QuizExercise importedExercise) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        QuizExercise newExercise = new QuizExercise();

        super.copyExerciseBasis(newExercise, importedExercise, new HashMap<>());
        newExercise.setRandomizeQuestionOrder(importedExercise.isRandomizeQuestionOrder());
        newExercise.setAllowedNumberOfAttempts(importedExercise.getAllowedNumberOfAttempts());
        newExercise.setRemainingNumberOfAttempts(importedExercise.getRemainingNumberOfAttempts());
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

        // Create deep copies of each question to avoid mutating managed entities
        // from the source exercise's L1 cache (important for exam import).
        List<QuizQuestion> newQuestions = new ArrayList<>();
        for (QuizQuestion originalQuestion : sourceExercise.getQuizQuestions()) {
            QuizQuestion newQuestion = switch (originalQuestion) {
                case MultipleChoiceQuestion mcq -> copyMultipleChoiceQuestion(mcq);
                case DragAndDropQuestion dnd -> copyDragAndDropQuestion(dnd);
                case ShortAnswerQuestion sa -> copyShortAnswerQuestion(sa);
                default -> throw new IllegalStateException("Unknown quiz question type: " + originalQuestion.getClass());
            };
            copyBaseQuizQuestionFields(originalQuestion, newQuestion);
            newQuestion.setExercise(newExercise);
            newQuestions.add(newQuestion);
        }
        newExercise.setQuizQuestions(newQuestions);
    }

    private void copyBaseQuizQuestionFields(QuizQuestion source, QuizQuestion target) {
        target.setTitle(source.getTitle());
        target.setText(source.getText());
        target.setHint(source.getHint());
        target.setExplanation(source.getExplanation());
        target.setPoints(source.getPoints());
        target.setScoringType(source.getScoringType());
        target.setRandomizeOrder(source.isRandomizeOrder());
        target.setInvalid(source.isInvalid());
        // ID and statistic are intentionally not copied — new copies start fresh
    }

    private MultipleChoiceQuestion copyMultipleChoiceQuestion(MultipleChoiceQuestion original) {
        MultipleChoiceQuestion copy = new MultipleChoiceQuestion();
        copy.setSingleChoice(original.isSingleChoice());

        List<AnswerOption> newAnswerOptions = new ArrayList<>();
        for (AnswerOption originalOption : original.getAnswerOptions()) {
            AnswerOption newOption = new AnswerOption();
            newOption.setText(originalOption.getText());
            newOption.setHint(originalOption.getHint());
            newOption.setExplanation(originalOption.getExplanation());
            newOption.setIsCorrect(originalOption.isIsCorrect());
            newOption.setInvalid(originalOption.isInvalid());
            newOption.setQuestion(copy);
            newAnswerOptions.add(newOption);
        }
        copy.setAnswerOptions(newAnswerOptions);
        return copy;
    }

    private DragAndDropQuestion copyDragAndDropQuestion(DragAndDropQuestion original) {
        DragAndDropQuestion copy = new DragAndDropQuestion();

        // Copy background file
        if (original.getBackgroundFilePath() != null) {
            URI backgroundFilePublicPath = URI.create(original.getBackgroundFilePath());
            URI backgroundFileIntendedPath = URI.create(FileUtil.BACKGROUND_FILE_SUBPATH);
            // Validate the path before any filesystem access to prevent path traversal
            FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(original.getBackgroundFilePath());
            FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(backgroundFilePublicPath, backgroundFileIntendedPath);
            Path oldPath = FilePathConverter.fileSystemPathForExternalUri(backgroundFilePublicPath, FilePathType.DRAG_AND_DROP_BACKGROUND).normalize();
            if (!oldPath.startsWith(FilePathConverter.getDragAndDropBackgroundFilePath().normalize())) {
                throw new IllegalArgumentException("Invalid background file path: resolved path is outside the expected directory");
            }
            if (Files.exists(oldPath)) {
                Path newPath = FileUtil.copyExistingFileToTarget(oldPath, FilePathConverter.getDragAndDropBackgroundFilePath(), FilePathType.DRAG_AND_DROP_BACKGROUND);
                copy.setBackgroundFilePath(FilePathConverter.externalUriForFileSystemPath(newPath, FilePathType.DRAG_AND_DROP_BACKGROUND, null).toString());
            }
            else {
                copy.setBackgroundFilePath(original.getBackgroundFilePath());
            }
        }
        else {
            log.warn("BackgroundFilePath of DragAndDropQuestion {} is null", original.getId());
        }

        // Copy drop locations
        List<DropLocation> newDropLocations = new ArrayList<>();
        for (DropLocation originalLoc : original.getDropLocations()) {
            DropLocation newLoc = new DropLocation();
            newLoc.setPosX(originalLoc.getPosX());
            newLoc.setPosY(originalLoc.getPosY());
            newLoc.setWidth(originalLoc.getWidth());
            newLoc.setHeight(originalLoc.getHeight());
            newLoc.setInvalid(originalLoc.isInvalid());
            newLoc.setQuestion(copy);
            newLoc.setMappings(new HashSet<>());
            newDropLocations.add(newLoc);
        }
        copy.setDropLocations(newDropLocations);

        // Copy drag items
        List<DragItem> newDragItems = new ArrayList<>();
        for (DragItem originalItem : original.getDragItems()) {
            DragItem newItem = new DragItem();
            newItem.setText(originalItem.getText());
            newItem.setInvalid(originalItem.isInvalid());
            newItem.setQuestion(copy);
            newItem.setMappings(new HashSet<>());
            copyDragItemFile(originalItem, newItem);
            newDragItems.add(newItem);
        }
        copy.setDragItems(newDragItems);

        // Copy correct mappings (must happen after drop locations and drag items are set)
        copyDragAndDropMappings(original, copy);
        return copy;
    }

    private void copyDragItemFile(DragItem source, DragItem target) {
        if (source.getPictureFilePath() == null) {
            return;
        }
        URI pictureFilePublicPath = URI.create(source.getPictureFilePath());
        URI pictureFileIntendedPath = URI.create(FileUtil.PICTURE_FILE_SUBPATH);
        // Validate the path before any filesystem access to prevent path traversal
        FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(source.getPictureFilePath());
        FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(pictureFilePublicPath, pictureFileIntendedPath);
        Path oldPath = FilePathConverter.fileSystemPathForExternalUri(pictureFilePublicPath, FilePathType.DRAG_ITEM).normalize();
        if (!oldPath.startsWith(FilePathConverter.getDragItemFilePath().normalize())) {
            throw new IllegalArgumentException("Invalid drag item file path: resolved path is outside the expected directory");
        }
        if (Files.exists(oldPath)) {
            Path newPath = FileUtil.copyExistingFileToTarget(oldPath, FilePathConverter.getDragItemFilePath(), FilePathType.DRAG_ITEM);
            target.setPictureFilePath(FilePathConverter.externalUriForFileSystemPath(newPath, FilePathType.DRAG_ITEM, null).toString());
        }
        else {
            target.setPictureFilePath(source.getPictureFilePath());
        }
    }

    private void copyDragAndDropMappings(DragAndDropQuestion source, DragAndDropQuestion target) {
        List<DragAndDropMapping> newMappings = new ArrayList<>();
        for (DragAndDropMapping originalMapping : source.getCorrectMappings()) {
            DragAndDropMapping newMapping = new DragAndDropMapping();
            newMapping.setInvalid(originalMapping.isInvalid());
            newMapping.setDragItemIndex(originalMapping.getDragItemIndex());
            newMapping.setDropLocationIndex(originalMapping.getDropLocationIndex());
            newMapping.setQuestion(target);
            if (originalMapping.getDragItemIndex() != null) {
                newMapping.setDragItem(target.getDragItems().get(originalMapping.getDragItemIndex()));
            }
            if (originalMapping.getDropLocationIndex() != null) {
                newMapping.setDropLocation(target.getDropLocations().get(originalMapping.getDropLocationIndex()));
            }
            newMappings.add(newMapping);
        }
        target.setCorrectMappings(newMappings);
    }

    private ShortAnswerQuestion copyShortAnswerQuestion(ShortAnswerQuestion original) {
        ShortAnswerQuestion copy = new ShortAnswerQuestion();
        copy.setSimilarityValue(original.getSimilarityValue());
        copy.setMatchLetterCase(original.getMatchLetterCase());

        // Copy spots
        Map<Long, ShortAnswerSpot> spotMap = new HashMap<>();
        for (ShortAnswerSpot oldSpot : original.getSpots()) {
            ShortAnswerSpot newSpot = createNewShortAnswerSpot(oldSpot, copy);
            Long key = oldSpot.getId();
            spotMap.put(key, newSpot);
        }
        copy.setSpots(new ArrayList<>(spotMap.values()));

        // Copy solutions
        Map<Long, ShortAnswerSolution> solutionMap = new HashMap<>();
        for (ShortAnswerSolution oldSolution : original.getSolutions()) {
            ShortAnswerSolution newSolution = createNewShortAnswerSolution(oldSolution, copy);
            Long key = oldSolution.getId();
            solutionMap.put(key, newSolution);
        }
        copy.setSolutions(new ArrayList<>(solutionMap.values()));

        // Copy correct mappings
        List<ShortAnswerMapping> newMappings = new ArrayList<>();
        for (ShortAnswerMapping oldMapping : original.getCorrectMappings()) {
            ShortAnswerMapping newMapping = createNewShortAnswerMapping(oldMapping, copy, spotMap, solutionMap);
            newMappings.add(newMapping);
        }
        copy.setCorrectMappings(newMappings);

        return copy;
    }

    /**
     * Creates a new ShortAnswerSpot instance based on the properties of the old spot.
     * Copies relevant fields and associates it with the given question, initializing an empty set of mappings.
     *
     * @param oldSpot    the original ShortAnswerSpot to copy from
     * @param saQuestion the short answer question to associate with the new spot
     * @return the newly created ShortAnswerSpot
     */
    private ShortAnswerSpot createNewShortAnswerSpot(ShortAnswerSpot oldSpot, ShortAnswerQuestion saQuestion) {
        ShortAnswerSpot newSpot = new ShortAnswerSpot();
        newSpot.setSpotNr(oldSpot.getSpotNr());
        newSpot.setWidth(oldSpot.getWidth());
        newSpot.setInvalid(oldSpot.isInvalid());
        newSpot.setQuestion(saQuestion);
        newSpot.setMappings(new HashSet<>());
        return newSpot;
    }

    /**
     * Creates a new ShortAnswerSolution instance based on the properties of the old solution.
     * Copies relevant fields and associates it with the given question, initializing an empty set of mappings.
     *
     * @param oldSolution the original ShortAnswerSolution to copy from
     * @param saQuestion  the short answer question to associate with the new solution
     * @return the newly created ShortAnswerSolution
     */
    private ShortAnswerSolution createNewShortAnswerSolution(ShortAnswerSolution oldSolution, ShortAnswerQuestion saQuestion) {
        ShortAnswerSolution newSolution = new ShortAnswerSolution();
        newSolution.setText(oldSolution.getText());
        newSolution.setInvalid(oldSolution.isInvalid());
        newSolution.setQuestion(saQuestion);
        newSolution.setMappings(new HashSet<>());
        return newSolution;
    }

    /**
     * Creates a new ShortAnswerMapping instance based on the properties of the old mapping.
     * Copies the invalid flag, associates it with the given question, and links it to the corresponding
     * new solution and spot using the provided maps if they exist.
     *
     * @param oldMapping  the original ShortAnswerMapping to copy from
     * @param saQuestion  the short answer question to associate with the new mapping
     * @param spotMap     the map of IDs/tempIDs to new ShortAnswerSpot instances
     * @param solutionMap the map of IDs/tempIDs to new ShortAnswerSolution instances
     * @return the newly created ShortAnswerMapping
     */
    private ShortAnswerMapping createNewShortAnswerMapping(ShortAnswerMapping oldMapping, ShortAnswerQuestion saQuestion, Map<Long, ShortAnswerSpot> spotMap,
            Map<Long, ShortAnswerSolution> solutionMap) {
        ShortAnswerMapping newMapping = new ShortAnswerMapping();
        newMapping.setInvalid(oldMapping.isInvalid());
        newMapping.setQuestion(saQuestion);

        if (oldMapping.getSolution() != null) {
            Long solutionKey = oldMapping.getSolution().getId();
            if (solutionKey != null) {
                newMapping.setSolution(solutionMap.computeIfPresent(solutionKey, (_, v) -> v));
            }
        }

        if (oldMapping.getSpot() != null) {
            Long spotKey = oldMapping.getSpot().getId();
            if (spotKey != null) {
                newMapping.setSpot(spotMap.computeIfPresent(spotKey, (_, v) -> v));
            }
        }

        return newMapping;
    }

    /**
     * This helper method copies all batches of the {@code sourceExercise} into a new exercise.
     *
     * @param sourceExercise The exercise from which to copy the batches
     * @param newExercise    The exercise to which the batches are copied
     */
    private void copyQuizBatches(QuizExercise sourceExercise, QuizExercise newExercise) {
        log.debug("Copying the QuizBatches to new QuizExercise: {}", newExercise);

        Set<QuizBatch> newBatches = new HashSet<>();
        for (QuizBatch originalBatch : sourceExercise.getQuizBatches()) {
            QuizBatch newBatch = new QuizBatch();
            newBatch.setStartTime(originalBatch.getStartTime());
            newBatch.setPassword(originalBatch.getPassword());
            newBatch.setQuizExercise(newExercise);
            newBatches.add(newBatch);
        }
        newExercise.setQuizBatches(newBatches);
    }

}
