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
     * Imports a quiz exercise: builds a new entity from {@code newExercise} (the destination and any caller overrides),
     * backfills its basis and quiz settings from {@code sourceExercise} (the original), copies a hard copy of the
     * questions, and saves it. Student-/tutor participations are not copied.
     * This method calls {@link #copyQuizExerciseBasis(QuizExercise, QuizExercise)} to set up the basis of the exercise
     * and {@link #copyQuizQuestions(QuizExercise, QuizExercise)} for a hard copy of the questions.
     *
     * @param newExercise    the exercise to build; already carries the destination (course / exercise group) and any overrides
     * @param sourceExercise the source exercise whose content and settings are copied
     * @param files          The potential files to be added. Null if no change to files during import. ExamImportService sends null by default
     * @return The newly created exercise
     */
    @NonNull
    public QuizExercise importQuizExercise(final QuizExercise newExercise, final QuizExercise sourceExercise, @Nullable List<MultipartFile> files) throws IOException {
        log.debug("Creating a new quiz exercise based on exercise {}", sourceExercise);
        copyQuizExerciseBasis(newExercise, sourceExercise);
        copyQuizQuestions(sourceExercise, newExercise);
        // Don't copy batches for exam exercises — exam timing controls quiz scheduling
        if (!newExercise.isExamExercise()) {
            copyQuizBatches(sourceExercise, newExercise);
        }

        // The first save is identity-preserving (the id was cleared, so Spring Data persists newExercise itself), so we
        // keep operating on the single newExercise reference instead of juggling the returned instances.
        quizExerciseService.save(newExercise);

        channelService.createExerciseChannel(newExercise, Optional.ofNullable(newExercise.getChannelName()));

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(newExercise));
        if (files != null) {
            // This save operates on a detached entity and therefore merges into a new instance, which carries the file
            // paths and the ids generated for the uploaded files, so it has to be returned instead of newExercise. The
            // transient channel name does not survive the merge, so restore it on the returned exercise.
            QuizExercise persistedExercise = quizExerciseService.save(quizExerciseService.uploadNewFilesToNewImportedQuiz(newExercise, files));
            persistedExercise.setChannelName(newExercise.getChannelName());
            return persistedExercise;
        }

        return newExercise;
    }

    /**
     * Backfills the quiz exercise basis and quiz-specific settings onto {@code newExercise} from {@code sourceExercise}.
     * The generic basis follows the "keep the caller's value, else take the source's" rule (see
     * {@link ExerciseImportService#copyExerciseBasis}). The quiz-specific settings are always taken from the source:
     * quiz exercises have no standalone (user-editable) import path, and several settings are primitive or have non-null
     * defaults, so a skeleton's default cannot be distinguished from an intentional override. The start-, end-, and
     * assessment due dates are intentionally not copied here.
     *
     * @param newExercise    the exercise being built; mutated in place
     * @param sourceExercise the source exercise providing the quiz content and settings
     */
    private void copyQuizExerciseBasis(QuizExercise newExercise, QuizExercise sourceExercise) {
        log.debug("Copying the quiz exercise basis from {}", sourceExercise);
        prepareNewExerciseForImport(newExercise);
        // A caller may pass a full quiz (the import-exercise-group path binds request entities), whose managed/detached
        // quiz statistic and batches cannot be persisted under the new exercise. Reset them: the statistic is recreated
        // fresh on save, questions and (for non-exam) batches are re-copied from the source below.
        newExercise.setQuizPointStatistic(null);
        newExercise.setQuizBatches(new HashSet<>());
        super.copyExerciseBasis(newExercise, sourceExercise, new HashMap<>());
        newExercise.setRandomizeQuestionOrder(sourceExercise.isRandomizeQuestionOrder());
        newExercise.setAllowedNumberOfAttempts(sourceExercise.getAllowedNumberOfAttempts());
        newExercise.setQuizMode(sourceExercise.getQuizMode());
        newExercise.setDuration(sourceExercise.getDuration());
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
            newAnswerOptions.add(newOption);
        }
        // setAnswerOptions mints a fresh, question-scoped id for each option
        copy.setAnswerOptions(newAnswerOptions);
        return copy;
    }

    private DragAndDropQuestion copyDragAndDropQuestion(DragAndDropQuestion original) {
        DragAndDropQuestion copy = new DragAndDropQuestion();

        // Copy background file
        if (original.getBackgroundFilePath() != null) {
            URI backgroundFilePublicPath = URI.create(original.getBackgroundFilePath());
            // Validate the path before any filesystem access to prevent path traversal. Both the canonical, question-scoped spelling and the legacy one are accepted, because the
            // source question may have been created before the re-spelling.
            FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(original.getBackgroundFilePath());
            FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(backgroundFilePublicPath, URI.create(FilePathConverter.DRAG_AND_DROP_QUESTION_SUBPATH),
                    URI.create(FileUtil.BACKGROUND_FILE_SUBPATH));
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

        // Copy drop locations (each gets a fresh, question-scoped id); order is preserved so index-based mapping copy below stays valid
        for (DropLocation originalLoc : original.getDropLocations()) {
            DropLocation newLoc = new DropLocation();
            newLoc.setPosX(originalLoc.getPosX());
            newLoc.setPosY(originalLoc.getPosY());
            newLoc.setWidth(originalLoc.getWidth());
            newLoc.setHeight(originalLoc.getHeight());
            newLoc.setInvalid(originalLoc.isInvalid());
            copy.addDropLocation(newLoc);
        }

        // Copy drag items (each gets a fresh, question-scoped id); order is preserved. The id must be minted (via addDragItem) before copying the picture file, since the new
        // picture
        // path embeds the drag item id.
        for (DragItem originalItem : original.getDragItems()) {
            DragItem newItem = new DragItem();
            newItem.setText(originalItem.getText());
            newItem.setInvalid(originalItem.isInvalid());
            copy.addDragItem(newItem);
            copyDragItemFile(originalItem, newItem, copy);
        }

        // Copy correct mappings (must happen after drop locations and drag items are set)
        copyDragAndDropMappings(original, copy);
        return copy;
    }

    private void copyDragItemFile(DragItem source, DragItem target, DragAndDropQuestion targetQuestion) {
        if (source.getPictureFilePath() == null) {
            return;
        }
        URI pictureFilePublicPath = URI.create(source.getPictureFilePath());
        // Validate the path before any filesystem access to prevent path traversal. Both the canonical, question-scoped spelling and the legacy one are accepted, because the
        // source drag item may have been created before the re-spelling.
        FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(source.getPictureFilePath());
        FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(pictureFilePublicPath, URI.create(FilePathConverter.DRAG_AND_DROP_QUESTION_SUBPATH),
                URI.create(FileUtil.PICTURE_FILE_SUBPATH));
        Path oldPath = FilePathConverter.fileSystemPathForExternalUri(pictureFilePublicPath, FilePathType.DRAG_ITEM).normalize();
        if (!oldPath.startsWith(FilePathConverter.getDragItemFilePath().normalize())) {
            throw new IllegalArgumentException("Invalid drag item file path: resolved path is outside the expected directory");
        }
        if (Files.exists(oldPath)) {
            Path newPath = FileUtil.copyExistingFileToTarget(oldPath, FilePathConverter.getDragItemFilePath(), FilePathType.DRAG_ITEM);
            // The target question has no id yet; a placeholder is written and DragAndDropQuestion.afterCreate() replaces it once the question has been persisted.
            target.setPictureFilePath(FilePathConverter.externalUriForDragItemFileSystemPath(newPath, targetQuestion.getId(), target.getId()).toString());
        }
        else {
            target.setPictureFilePath(source.getPictureFilePath());
        }
    }

    private void copyDragAndDropMappings(DragAndDropQuestion source, DragAndDropQuestion target) {
        // The source mappings carry positional indices (derived from the source's ordered lists). Because the target's drop locations and drag items were copied in the same order,
        // the index maps 1:1 to the target's freshly-created (id-bearing) components.
        for (DragAndDropMapping originalMapping : source.getCorrectMappings()) {
            Integer dragItemIndex = originalMapping.getDragItemIndex();
            Integer dropLocationIndex = originalMapping.getDropLocationIndex();
            if (dragItemIndex == null || dropLocationIndex == null) {
                continue;
            }
            DragAndDropMapping newMapping = new DragAndDropMapping();
            newMapping.setInvalid(originalMapping.isInvalid());
            newMapping.setDragItem(target.getDragItems().get(dragItemIndex));
            newMapping.setDropLocation(target.getDropLocations().get(dropLocationIndex));
            target.addCorrectMapping(newMapping);
        }
    }

    private ShortAnswerQuestion copyShortAnswerQuestion(ShortAnswerQuestion original) {
        ShortAnswerQuestion copy = new ShortAnswerQuestion();
        copy.setSimilarityValue(original.getSimilarityValue());
        copy.setMatchLetterCase(original.getMatchLetterCase());

        // Copy spots (a fresh, question-scoped id is minted for each by setSpots). Keep an ordered list so the original spot order is preserved, and a map only for resolving the
        // correct mappings below by old id (HashMap iteration order is unspecified, so it must not drive the stored/serialized order).
        Map<Long, ShortAnswerSpot> spotMap = new HashMap<>();
        List<ShortAnswerSpot> newSpots = new ArrayList<>();
        for (ShortAnswerSpot oldSpot : original.getSpots()) {
            ShortAnswerSpot newSpot = createNewShortAnswerSpot(oldSpot);
            spotMap.put(oldSpot.getId(), newSpot);
            newSpots.add(newSpot);
        }
        copy.setSpots(newSpots);

        // Copy solutions (a fresh, question-scoped id is minted for each by setSolutions); same ordered-list + lookup-map approach as the spots above.
        Map<Long, ShortAnswerSolution> solutionMap = new HashMap<>();
        List<ShortAnswerSolution> newSolutions = new ArrayList<>();
        for (ShortAnswerSolution oldSolution : original.getSolutions()) {
            ShortAnswerSolution newSolution = createNewShortAnswerSolution(oldSolution);
            solutionMap.put(oldSolution.getId(), newSolution);
            newSolutions.add(newSolution);
        }
        copy.setSolutions(newSolutions);

        // Copy correct mappings: the new spots/solutions already have minted ids, so setCorrectMappings can store them id-based (resolved against spotMap/solutionMap by old id)
        Set<ShortAnswerMapping> newMappings = new HashSet<>();
        for (ShortAnswerMapping oldMapping : original.getCorrectMappings()) {
            ShortAnswerMapping newMapping = createNewShortAnswerMapping(oldMapping, spotMap, solutionMap);
            newMappings.add(newMapping);
        }
        copy.setCorrectMappings(newMappings);

        return copy;
    }

    /**
     * Creates a new ShortAnswerSpot instance based on the properties of the old spot.
     *
     * @param oldSpot the original ShortAnswerSpot to copy from
     * @return the newly created ShortAnswerSpot
     */
    private ShortAnswerSpot createNewShortAnswerSpot(ShortAnswerSpot oldSpot) {
        ShortAnswerSpot newSpot = new ShortAnswerSpot();
        newSpot.setSpotNr(oldSpot.getSpotNr());
        newSpot.setWidth(oldSpot.getWidth());
        newSpot.setInvalid(oldSpot.isInvalid());
        return newSpot;
    }

    /**
     * Creates a new ShortAnswerSolution instance based on the properties of the old solution.
     *
     * @param oldSolution the original ShortAnswerSolution to copy from
     * @return the newly created ShortAnswerSolution
     */
    private ShortAnswerSolution createNewShortAnswerSolution(ShortAnswerSolution oldSolution) {
        ShortAnswerSolution newSolution = new ShortAnswerSolution();
        newSolution.setText(oldSolution.getText());
        newSolution.setInvalid(oldSolution.isInvalid());
        return newSolution;
    }

    /**
     * Creates a new ShortAnswerMapping instance based on the properties of the old mapping.
     * Copies the invalid flag and links it to the corresponding new solution and spot using the provided maps if they exist.
     *
     * @param oldMapping  the original ShortAnswerMapping to copy from
     * @param spotMap     the map of IDs/tempIDs to new ShortAnswerSpot instances
     * @param solutionMap the map of IDs/tempIDs to new ShortAnswerSolution instances
     * @return the newly created ShortAnswerMapping
     */
    private ShortAnswerMapping createNewShortAnswerMapping(ShortAnswerMapping oldMapping, Map<Long, ShortAnswerSpot> spotMap, Map<Long, ShortAnswerSolution> solutionMap) {
        ShortAnswerMapping newMapping = new ShortAnswerMapping();
        newMapping.setInvalid(oldMapping.isInvalid());

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
