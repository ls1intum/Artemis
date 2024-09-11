package de.tum.cit.aet.artemis.service.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.QuizMode;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.ShortAnswerMappingRepository;
import de.tum.cit.aet.artemis.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.util.PageUtil;

@Profile(PROFILE_CORE)
@Service
public class QuizExerciseService extends QuizService<QuizExercise> {

    public static final String ENTITY_NAME = "QuizExercise";

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizStatisticService quizStatisticService;

    private final QuizBatchService quizBatchService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final FileService fileService;

    public QuizExerciseService(QuizExerciseRepository quizExerciseRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository,
            InstanceMessageSendService instanceMessageSendService, QuizStatisticService quizStatisticService, QuizBatchService quizBatchService,
            ExerciseSpecificationService exerciseSpecificationService, FileService fileService, DragAndDropMappingRepository dragAndDropMappingRepository,
            ShortAnswerMappingRepository shortAnswerMappingRepository) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository);
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizStatisticService = quizStatisticService;
        this.quizBatchService = quizBatchService;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.fileService = fileService;
    }

    /**
     * adjust existing results if an answer or and question was deleted and recalculate the scores
     *
     * @param quizExercise the changed quizExercise.
     */
    private void updateResultsOnQuizChanges(QuizExercise quizExercise) {
        // change existing results if an answer or and question was deleted
        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        log.info("Found {} results to update for quiz re-evaluate", results.size());
        List<QuizSubmission> submissions = new ArrayList<>();
        for (Result result : results) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
            result.setSubmission(quizSubmission);

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                // Delete all references to question and question-elements if the question was changed
                submittedAnswer.checkAndDeleteReferences(quizExercise);
                if (!quizExercise.getQuizQuestions().contains(submittedAnswer.getQuizQuestion())) {
                    submittedAnswersToDelete.add(submittedAnswer);
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);

            // recalculate existing score
            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            // update Successful-Flag in Result
            StudentParticipation studentParticipation = (StudentParticipation) result.getParticipation();
            studentParticipation.setExercise(quizExercise);
            result.evaluateQuizSubmission(quizExercise);

            submissions.add(quizSubmission);
        }
        // save the updated submissions and results
        quizSubmissionRepository.saveAll(submissions);
        resultRepository.saveAll(results);
        log.info("{} results have been updated successfully for quiz re-evaluate", results.size());
    }

    /**
     * @param quizExercise         the changed quiz exercise from the client
     * @param originalQuizExercise the original quiz exercise (with statistics)
     * @param files                the files that were uploaded
     * @return the updated quiz exercise with the changed statistics
     */
    public QuizExercise reEvaluate(QuizExercise quizExercise, QuizExercise originalQuizExercise, @NotNull List<MultipartFile> files) throws IOException {
        quizExercise.undoUnallowedChanges(originalQuizExercise);
        validateQuizExerciseFiles(quizExercise, files, false);

        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        // update QuizExercise
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.reconnectJSONIgnoreAttributes();
        handleDndQuizFileUpdates(quizExercise, originalQuizExercise, files);

        // adjust existing results if an answer or a question was deleted and recalculate them
        updateResultsOnQuizChanges(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        if (updateOfResultsAndStatisticsNecessary) {
            // make sure we have all objects available before updating the statistics to avoid lazy / proxy issues
            savedQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
            quizStatisticService.recalculateStatistics(savedQuizExercise);
        }
        // fetch the quiz exercise again to make sure the latest changes are included
        return quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
    }

    /**
     * Reset a QuizExercise to its original state, delete statistics and cleanup the schedule service.
     *
     * @param exerciseId id of the exercise to reset
     */
    public void resetExercise(Long exerciseId) {
        // fetch exercise again to make sure we have an updated version
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(exerciseId);

        // for quizzes, we need to delete the statistics, and we need to reset the quiz to its original state
        quizExercise.setIsOpenForPractice(Boolean.FALSE);
        if (!quizExercise.isExamExercise()) {
            // do not set the release date of exam exercises
            quizExercise.setReleaseDate(ZonedDateTime.now());
        }
        quizExercise.setDueDate(null);
        quizExercise.setQuizBatches(Set.of());

        resetInvalidQuestions(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        // in case the quiz has not yet started or the quiz is currently running, we have to clean up
        instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());

        // clean up the statistics
        quizStatisticService.recalculateStatistics(savedQuizExercise);
    }

    public void cancelScheduledQuiz(Long quizExerciseId) {
        instanceMessageSendService.sendQuizExerciseStartCancel(quizExerciseId);
    }

    /**
     * Update a QuizExercise so that it ends at a specific date and moves the start date of the batches as required. Does not save the quiz.
     *
     * @param quizExercise The quiz to end
     */
    public void endQuiz(QuizExercise quizExercise) {
        quizExercise.setDueDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, batch.getStartTime())));
    }

    /**
     * Search for all quiz exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in courses for exercises
     * @param isExamFilter   Whether to search in exams for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<QuizExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<QuizExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<QuizExercise> exercisePage = quizExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the exercise accordingly.
     *
     * @param quizExercise the quiz exercise to create
     * @param files        the provided files
     */
    public void handleDndQuizFileCreation(QuizExercise quizExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(quizExercise, nullsafeFiles, true);
        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, null);
                }
                handleDndQuizDragItemsCreation(dragAndDropQuestion, fileMap);
            }
        }
    }

    private void handleDndQuizDragItemsCreation(DragAndDropQuestion dragAndDropQuestion, Map<String, MultipartFile> fileMap) throws IOException {
        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            if (dragItem.getPictureFilePath() != null) {
                saveDndDragItemPicture(dragItem, fileMap, null);
            }
        }
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the exercise accordingly.
     * Ignores unchanged paths and removes deleted background images.
     *
     * @param updatedExercise  the updated quiz exercise
     * @param originalExercise the original quiz exercise
     * @param files            the provided files
     */
    public void handleDndQuizFileUpdates(QuizExercise updatedExercise, QuizExercise originalExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(updatedExercise, nullsafeFiles, false);
        // Find old drag items paths
        Set<String> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalExercise);
        // Init files to remove with all old paths
        Set<String> filesToRemove = new HashSet<>(oldPaths);

        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : updatedExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap, dragAndDropQuestion);
            }
        }

        fileService.deleteFiles(filesToRemove.stream().map(Paths::get).toList());
    }

    private Set<String> getAllPathsFromDragAndDropQuestionsOfExercise(QuizExercise quizExercise) {
        Set<String> paths = new HashSet<>();
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    paths.add(dragAndDropQuestion.getBackgroundFilePath());
                }
                paths.addAll(dragAndDropQuestion.getDragItems().stream().map(DragItem::getPictureFilePath).filter(Objects::nonNull).collect(Collectors.toSet()));
            }
        }
        return paths;
    }

    private void handleDndQuestionUpdate(DragAndDropQuestion dragAndDropQuestion, Set<String> oldPaths, Set<String> filesToRemove, Map<String, MultipartFile> fileMap,
            DragAndDropQuestion questionUpdate) throws IOException {
        String newBackgroundPath = dragAndDropQuestion.getBackgroundFilePath();

        // Don't do anything if the path is null because it's getting removed
        if (newBackgroundPath != null) {
            if (oldPaths.contains(newBackgroundPath)) {
                // Path didn't change
                filesToRemove.remove(dragAndDropQuestion.getBackgroundFilePath());
            }
            else {
                // Path changed and file was provided
                saveDndQuestionBackground(dragAndDropQuestion, fileMap, questionUpdate.getId());
            }
        }

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            String newDragItemPath = dragItem.getPictureFilePath();
            if (dragItem.getPictureFilePath() != null && !oldPaths.contains(newDragItemPath)) {
                // Path changed and file was provided
                saveDndDragItemPicture(dragItem, fileMap, null);
            }
        }
    }

    /**
     * Verifies that the provided files match the provided filenames in the exercise entity.
     *
     * @param quizExercise  the quiz exercise to validate
     * @param providedFiles the provided files to validate
     * @param isCreate      On create all files get validated, on update only changed files get validated
     */
    public void validateQuizExerciseFiles(QuizExercise quizExercise, @NotNull List<MultipartFile> providedFiles, boolean isCreate) {
        long fileCount = providedFiles.size();
        Set<String> exerciseFileNames = getAllPathsFromDragAndDropQuestionsOfExercise(quizExercise);
        Set<String> newFileNames = isCreate ? exerciseFileNames : exerciseFileNames.stream().filter(fileNameOrUri -> {
            try {
                return !Files.exists(FilePathService.actualPathForPublicPathOrThrow(URI.create(fileNameOrUri)));
            }
            catch (FilePathParsingException e) {
                // File is not in the internal API format and hence expected to be a new file
                return true;
            }
        }).collect(Collectors.toSet());

        if (newFileNames.size() != fileCount) {
            throw new BadRequestAlertException("Number of files does not match number of new drag items and backgrounds", ENTITY_NAME, null);
        }
        Set<String> providedFileNames = providedFiles.stream().map(MultipartFile::getOriginalFilename).collect(Collectors.toSet());
        if (!newFileNames.equals(providedFileNames)) {
            throw new BadRequestAlertException("File names do not match new drag item and background file names", ENTITY_NAME, null);
        }
    }

    /**
     * Saves the background image of a drag and drop question without saving the question itself
     *
     * @param question   the drag and drop question
     * @param files      all provided files
     * @param questionId the id of the question, null on creation
     */
    public void saveDndQuestionBackground(DragAndDropQuestion question, Map<String, MultipartFile> files, @Nullable Long questionId) throws IOException {
        MultipartFile file = files.get(question.getBackgroundFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + question.getBackgroundFilePath() + " was not provided", ENTITY_NAME, null);
        }

        question.setBackgroundFilePath(saveDragAndDropImage(FilePathService.getDragAndDropBackgroundFilePath(), file, questionId).toString());
    }

    /**
     * Saves the picture of a drag item without saving the drag item itself
     *
     * @param dragItem the drag item
     * @param files    all provided files
     * @param entityId The entity id connected to this file, can be question id for background, or the drag item id for drag item images
     */
    public void saveDndDragItemPicture(DragItem dragItem, Map<String, MultipartFile> files, @Nullable Long entityId) throws IOException {
        MultipartFile file = files.get(dragItem.getPictureFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + dragItem.getPictureFilePath() + " was not provided", ENTITY_NAME, null);
        }

        dragItem.setPictureFilePath(saveDragAndDropImage(FilePathService.getDragItemFilePath(), file, entityId).toString());
    }

    /**
     * Saves an image for an DragAndDropQuestion. Either a background image or a drag item image.
     *
     * @return the public path of the saved image
     */
    private URI saveDragAndDropImage(Path basePath, MultipartFile file, @Nullable Long entityId) throws IOException {
        String sanitizedFilename = fileService.checkAndSanitizeFilename(file.getOriginalFilename());
        Path savePath = basePath.resolve(fileService.generateFilename("dnd_image_", sanitizedFilename, true));
        FileUtils.copyToFile(file.getInputStream(), savePath.toFile());
        return FilePathService.publicPathForActualPathOrThrow(savePath, entityId);
    }

    /**
     * Reset the invalid status of questions of given quizExercise to false
     *
     * @param quizExercise The quiz exercise which questions to be reset
     */
    private void resetInvalidQuestions(QuizExercise quizExercise) {
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            question.setInvalid(false);
        }
    }

    @Override
    public QuizExercise save(QuizExercise quizExercise) {
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());

        // create a quizPointStatistic if it does not yet exist
        if (quizExercise.getQuizPointStatistic() == null) {
            QuizPointStatistic quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
        }

        // make sure the pointers in the statistics are correct
        quizExercise.recalculatePointCounters();

        QuizExercise savedQuizExercise = super.save(quizExercise);

        if (savedQuizExercise.isCourseExercise()) {
            // only schedule quizzes for course exercises, not for exam exercises
            instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());
        }

        return savedQuizExercise;
    }

    @Override
    protected QuizExercise saveAndFlush(QuizExercise quizExercise) {
        if (quizExercise.getQuizBatches() != null) {
            for (QuizBatch quizBatch : quizExercise.getQuizBatches()) {
                quizBatch.setQuizExercise(quizExercise);
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    if (quizBatch.getStartTime() != null) {
                        quizExercise.setDueDate(quizBatch.getStartTime().plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
                    }
                }
                else {
                    quizBatch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, quizBatch.getStartTime()));
                }
            }
        }

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        // and delete the now orphaned entries from the database
        log.debug("Save quiz exercise to database: {}", quizExercise);
        return quizExerciseRepository.saveAndFlush(quizExercise);
    }

    /**
     *
     * @param newQuizExercise the newly created quiz exercise, after importing basis of imported exercise
     * @param files           the new files to be added to the newQuizExercise which do not have a previous path and need to be saved in the server
     * @return the new exercise with the updated file paths which have been created and saved
     * @throws IOException throws IO exception if corrupted files
     */
    public QuizExercise uploadNewFilesToNewImportedQuiz(QuizExercise newQuizExercise, List<MultipartFile> files) throws IOException {
        Map<String, MultipartFile> fileMap = files.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity()));
        for (var question : newQuizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                URI publicPathUri = URI.create(dragAndDropQuestion.getBackgroundFilePath());
                if (FilePathService.actualPathForPublicPath(publicPathUri) == null) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, dragAndDropQuestion.getId());
                }
                for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null && FilePathService.actualPathForPublicPath(URI.create(dragItem.getPictureFilePath())) == null) {
                        saveDndDragItemPicture(dragItem, fileMap, dragItem.getId());
                    }
                }
            }
        }
        return newQuizExercise;
    }
}
