package de.tum.cit.aet.artemis.exam.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamImportResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupImportResultDTO;
import de.tum.cit.aet.artemis.exam.exception.ExamConfigurationException;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exercise.domain.BaseExercise;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadImportApi;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.modeling.api.ModelingExerciseImportApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.text.api.TextExerciseImportApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamImportService {

    private static final Logger log = LoggerFactory.getLogger(ExamImportService.class);

    private final Optional<TextExerciseImportApi> textExerciseImportApi;

    private final Optional<ModelingExerciseImportApi> modelingExerciseImportApi;

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final Optional<FileUploadImportApi> fileUploadImportApi;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ChannelService channelService;

    private final Optional<SearchableEntityWeaviateService> searchableItemWeaviateService;

    private final WebsocketMessagingService websocketMessagingService;

    public ExamImportService(Optional<TextExerciseImportApi> textExerciseImportApi, Optional<ModelingExerciseImportApi> modelingExerciseImportApi, ExamRepository examRepository,
            ExerciseGroupRepository exerciseGroupRepository, QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService importQuizExercise,
            CourseRepository courseRepository, ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseImportService programmingExerciseImportService,
            Optional<FileUploadImportApi> fileUploadImportApi, GradingCriterionRepository gradingCriterionRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ChannelService channelService,
            Optional<SearchableEntityWeaviateService> searchableItemWeaviateService, WebsocketMessagingService websocketMessagingService) {
        this.textExerciseImportApi = textExerciseImportApi;
        this.modelingExerciseImportApi = modelingExerciseImportApi;
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = importQuizExercise;
        this.courseRepository = courseRepository;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.fileUploadImportApi = fileUploadImportApi;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.channelService = channelService;
        this.searchableItemWeaviateService = searchableItemWeaviateService;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Counts the total number of exercises across the given exercise groups (used to report import progress).
     *
     * @param exerciseGroups the exercise groups to count exercises in
     * @return the total number of exercises
     */
    private static int countExercises(List<ExerciseGroup> exerciseGroups) {
        if (exerciseGroups == null) {
            return 0;
        }
        return exerciseGroups.stream().mapToInt(group -> group.getExercises() == null ? 0 : group.getExercises().size()).sum();
    }

    /**
     * Imports the given Exam with ExerciseGroups and Exercises to the given target Course
     *
     * @param examToCopy     the exam which should be copied together with exercise groups and exercises
     * @param targetCourseId the course to which the exam should be imported
     * @return the result of the import, containing the copied exam and the titles of any exercises that could not be imported
     */
    public ExamImportResultDTO importExamWithExercises(Exam examToCopy, long targetCourseId) throws IOException {
        return importExamWithExercises(examToCopy, targetCourseId, null, null);
    }

    /**
     * Imports the given Exam with ExerciseGroups and Exercises to the given target Course, reporting live progress to the importing user over a websocket.
     *
     * @param examToCopy     the exam which should be copied together with exercise groups and exercises
     * @param targetCourseId the course to which the exam should be imported
     * @param importId       a client-supplied id identifying this import (used as the websocket progress channel), or {@code null} to disable progress reporting
     * @param userLogin      the login of the importing user (progress is sent only to them), or {@code null} to disable progress reporting
     * @return the result of the import, containing the copied exam and the titles of any exercises that could not be imported
     */
    public ExamImportResultDTO importExamWithExercises(Exam examToCopy, long targetCourseId, String importId, String userLogin) throws IOException {
        ExamImportProgressNotifier progressNotifier = new ExamImportProgressNotifier(websocketMessagingService, userLogin, importId);

        Course targetCourse = courseRepository.findByIdElseThrow(targetCourseId);

        preCheckProgrammingExercisesForTitleAndShortNameUniqueness(examToCopy.getExerciseGroups(), targetCourse.getShortName());

        // 1st: Save the exam without exercises to the database and create a new channel for the exam
        List<ExerciseGroup> exerciseGroupsToCopy = examToCopy.getExerciseGroups();
        progressNotifier.start(countExercises(exerciseGroupsToCopy));
        examToCopy.setExerciseGroups(new ArrayList<>());
        Exam examCopied = createCopyOfExamWithoutConductionSpecificAttributes(examToCopy, targetCourse);

        // 2nd: Copy the exercise groups to the exam. The import is intentionally resilient: if a single exercise fails to
        // import (e.g. a programming-exercise repository copy timing out), that exercise is skipped and its title collected,
        // and the import continues with the remaining exercises. This prevents one problematic exercise from failing the
        // whole import (the previous behaviour surfaced as a 5xx error that left most imported exercises broken).
        // IMPORTANT: this catch-and-continue is only safe because no transaction spans the import loop (no @Transactional
        // on this method or its callers, and OSIV is disabled). Adding an outer @Transactional would mark the shared
        // persistence context rollback-only after the first failed exercise, breaking all subsequent persistence calls.
        List<String> skippedExerciseTitles = new ArrayList<>();
        List<String> incompleteExerciseTitles = new ArrayList<>();
        copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, examCopied, skippedExerciseTitles, incompleteExerciseTitles, progressNotifier);
        channelService.createExamChannel(examCopied, Optional.ofNullable(examToCopy.getChannelName()));

        if (!skippedExerciseTitles.isEmpty() || !incompleteExerciseTitles.isEmpty()) {
            log.warn("Imported exam {} into course {}; skipped exercises (not imported): {}; incomplete exercises (may need review): {}", examCopied.getId(), targetCourseId,
                    skippedExerciseTitles, incompleteExerciseTitles);
        }
        progressNotifier.finished(skippedExerciseTitles, incompleteExerciseTitles);

        Exam examWithExercises = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examCopied.getId());

        // 3rd: Index all imported exercises and the exam itself in Weaviate
        searchableItemWeaviateService.ifPresent(service -> {
            service.upsertExamAsync(ExamSearchableEntityDTO.fromExam(examWithExercises));
            service.updateExercisesAsync(examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                    .map(exercise -> ExerciseSearchableEntityDTO.fromExerciseWithExam(exercise, examWithExercises)).toList(), examWithExercises.getId());
        });

        return new ExamImportResultDTO(examWithExercises, skippedExerciseTitles, incompleteExerciseTitles);
    }

    /**
     * Imports the given ExerciseGroups with exercises to the given exam
     *
     * @param exerciseGroupsToCopy the Exercise Groups to be imported
     * @param targetExamId         the target exam id
     * @param courseId             the associated course of the exam
     * @return the result of the import, containing all Exercise Groups of the target exam and the titles of any exercises that could not be imported
     */
    public ExerciseGroupImportResultDTO importExerciseGroupsWithExercisesToExistingExam(List<ExerciseGroup> exerciseGroupsToCopy, long targetExamId, long courseId)
            throws IOException {
        return importExerciseGroupsWithExercisesToExistingExam(exerciseGroupsToCopy, targetExamId, courseId, null, null);
    }

    /**
     * Imports the given ExerciseGroups with exercises to the given exam, reporting live progress to the importing user over a websocket.
     *
     * @param exerciseGroupsToCopy the Exercise Groups to be imported
     * @param targetExamId         the target exam id
     * @param courseId             the associated course of the exam
     * @param importId             a client-supplied id identifying this import (used as the websocket progress channel), or {@code null} to disable progress reporting
     * @param userLogin            the login of the importing user (progress is sent only to them), or {@code null} to disable progress reporting
     * @return the result of the import, containing all Exercise Groups of the target exam and the titles of any exercises that could not be imported
     */
    public ExerciseGroupImportResultDTO importExerciseGroupsWithExercisesToExistingExam(List<ExerciseGroup> exerciseGroupsToCopy, long targetExamId, long courseId, String importId,
            String userLogin) throws IOException {
        ExamImportProgressNotifier progressNotifier = new ExamImportProgressNotifier(websocketMessagingService, userLogin, importId);

        Course targetCourse = courseRepository.findByIdElseThrow(courseId);

        preCheckProgrammingExercisesForTitleAndShortNameUniqueness(exerciseGroupsToCopy, targetCourse.getShortName());

        // Start reporting progress only after the pre-check passed, so a request that fails validation up front does not emit
        // a (RUNNING) progress event (and briefly flash the progress dialog). This matches the full-exam import path.
        progressNotifier.start(countExercises(exerciseGroupsToCopy));

        Exam targetExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(targetExamId);

        // The Exam is used to ensure the connection ExerciseGroups <-> Exam.
        // As with the full exam import, exercises that fail to import do not abort the whole operation; they are reported
        // as either skipped (nothing persisted) or incomplete (may have left a partial exercise that needs review).
        List<String> skippedExerciseTitles = new ArrayList<>();
        List<String> incompleteExerciseTitles = new ArrayList<>();
        copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, targetExam, skippedExerciseTitles, incompleteExerciseTitles, progressNotifier);
        if (!skippedExerciseTitles.isEmpty() || !incompleteExerciseTitles.isEmpty()) {
            log.warn("Imported exercise groups into exam {}; skipped exercises (not imported): {}; incomplete exercises (may need review): {}", targetExamId, skippedExerciseTitles,
                    incompleteExerciseTitles);
        }
        progressNotifier.finished(skippedExerciseTitles, incompleteExerciseTitles);

        Exam examWithExercises = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(targetExamId);

        // Index the imported exercises and update the exam in Weaviate
        searchableItemWeaviateService.ifPresent(service -> {
            service.upsertExamAsync(ExamSearchableEntityDTO.fromExam(examWithExercises));
            service.updateExercisesAsync(examWithExercises.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                    .map(exercise -> ExerciseSearchableEntityDTO.fromExerciseWithExam(exercise, examWithExercises)).toList(), examWithExercises.getId());
        });

        return new ExerciseGroupImportResultDTO(examWithExercises.getExerciseGroups(), skippedExerciseTitles, incompleteExerciseTitles);
    }

    /**
     * Checks if programming exercises passed to the method have duplicated titles or short names. When a duplication is found,
     * the title / short name is removed from the corresponding exercises. After this method has been called, no programming exercise in
     * exerciseGroups has a duplicated title / short name.
     *
     * @param programmingExercises programming exercises we have to check for duplications
     * @param checkTitle           if the title should be checked for duplications. In case it is set to false, the short names are checked
     * @return true if any duplications were found and taken care of
     */
    private boolean checkForAndRemoveDuplicatedTitlesAndShortNames(List<Exercise> programmingExercises, boolean checkTitle) {
        List<String> titlesOrShortNames = programmingExercises.stream().map(checkTitle ? BaseExercise::getTitle : BaseExercise::getShortName).toList();
        Set<String> uniqueTitlesOrShortNames = new HashSet<>(titlesOrShortNames);

        // check if there are duplications
        if (titlesOrShortNames.size() != uniqueTitlesOrShortNames.size()) {
            // go through all exercises and use the uniqueTitlesOrShortNames set to see which ones are duplicated. When an
            // exercise is found, the title / shortName is removed and the corresponding entry is removed from the set
            programmingExercises.forEach(exercise -> {
                String searchFor = checkTitle ? exercise.getTitle() : exercise.getShortName();
                if (!uniqueTitlesOrShortNames.contains(searchFor)) {
                    if (checkTitle) {
                        exercise.setTitle("");
                    }
                    else {
                        exercise.setShortName("");
                    }
                }
                else {
                    uniqueTitlesOrShortNames.remove(searchFor);
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Checks if a project with the same key and name already exists on VCS / CI. The number of such occurrences is counted
     *
     * @param programmingExercises that are checked for an existing project
     * @param courseShortName      the short name of the course the exercises will be imported into
     * @return the number of exercises that need to be renamed in the client
     */
    private int checkForExistingProjectAndRemoveTitleShortName(List<Exercise> programmingExercises, String courseShortName) {
        // Count how many programming exercises have conflicts with VCS / CI due to the project with the same key / name already existing
        // Iterate over all programming exercises
        return programmingExercises.stream().mapToInt(exercise -> {
            // Method to check, if the project already exists.
            boolean projectExists = programmingExerciseValidationService.preCheckProjectExistsOnVCSOrCI((ProgrammingExercise) exercise, courseShortName);
            if (projectExists) {
                // If the project already exists the short name and title are removed. It has to be set in the client again
                exercise.setShortName("");
                exercise.setTitle("");
            }
            return projectExists ? 1 : 0;
        }).sum();
    }

    /**
     * Checks that all programming exercises of the given exercise group have a unique title and short name.
     * Additionally, checks if an exercise with the same project key or name already exists on the VCS / CI.
     * In case of an invalid configuration, the exam is sent back to the client with the title / short name removed, wherever a new one must be chosen
     *
     * @param exerciseGroups  the list of all exercises (not only programming) to be checked
     * @param courseShortName the short name of the course the exercises will be imported into
     * @throws ExamConfigurationException in case of duplicated titles / short names or if one or more programming exercise project keys are not unique
     */
    private void preCheckProgrammingExercisesForTitleAndShortNameUniqueness(List<ExerciseGroup> exerciseGroups, String courseShortName) {
        List<Exercise> programmingExercises = exerciseGroups.stream().flatMap(group -> group.getExercises().stream())
                .filter(exercise -> exercise.getExerciseType() == ExerciseType.PROGRAMMING).toList();

        // check for duplicated titles
        boolean duplicatedTitles = checkForAndRemoveDuplicatedTitlesAndShortNames(programmingExercises, true);
        if (duplicatedTitles) {
            throw new ExamConfigurationException(exerciseGroups, 0, "duplicatedProgrammingExerciseTitle");
        }
        // check for duplicated short names
        boolean duplicatedShortNames = checkForAndRemoveDuplicatedTitlesAndShortNames(programmingExercises, false);
        if (duplicatedShortNames) {
            throw new ExamConfigurationException(exerciseGroups, 0, "duplicatedProgrammingExerciseShortName");
        }
        // check for existing project on VCS / CI
        int numberOfInvalidProgrammingExercises = checkForExistingProjectAndRemoveTitleShortName(programmingExercises, courseShortName);
        if (numberOfInvalidProgrammingExercises > 0) {
            throw new ExamConfigurationException(exerciseGroups, numberOfInvalidProgrammingExercises, "invalidKey");
        }
    }

    /**
     * Method to create a copy of the given exerciseGroups and their exercises
     *
     * @param exerciseGroupsToCopy     the exerciseGroups to be copied
     * @param targetExam               the new exam to which the new exerciseGroups should be linked
     * @param skippedExerciseTitles    collects titles of exercises that were cleanly skipped (nothing persisted)
     * @param incompleteExerciseTitles collects titles of exercises that failed partway and may be incomplete (need review)
     * @param progressNotifier         reports live import progress per exercise to the importing user (no-op when nobody is listening)
     */
    private void copyExerciseGroupsWithExercisesToExam(List<ExerciseGroup> exerciseGroupsToCopy, Exam targetExam, List<String> skippedExerciseTitles,
            List<String> incompleteExerciseTitles, ExamImportProgressNotifier progressNotifier) throws IOException {
        // Only exercise groups with at least one exercise should be imported.
        List<ExerciseGroup> filteredExerciseGroupsToCopy = exerciseGroupsToCopy.stream().filter(exerciseGroup -> !exerciseGroup.getExercises().isEmpty()).toList();
        // If no exercise group is existent, we can aboard the process
        if (filteredExerciseGroupsToCopy.isEmpty()) {
            return;
        }

        // Create a copy of each exercise group and add them to the exam
        filteredExerciseGroupsToCopy.forEach(exerciseGroupToCopy -> {
            ExerciseGroup exerciseGroupCopied = new ExerciseGroup();
            exerciseGroupCopied.setTitle(exerciseGroupToCopy.getTitle());
            exerciseGroupCopied.setIsMandatory(exerciseGroupToCopy.getIsMandatory());
            targetExam.addExerciseGroup(exerciseGroupCopied);
        });

        examRepository.save(targetExam);

        // We need to take the exercise groups from the exam to ensure the correct connection exam <-> exercise group
        // subList(from,to) needs the arguments in the following way: [from, to)
        int indexTo = targetExam.getExerciseGroups().size();
        int indexFrom = indexTo - filteredExerciseGroupsToCopy.size();
        List<ExerciseGroup> exerciseGroupsCopied = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(targetExam.getId()).getExerciseGroups().subList(indexFrom,
                indexTo);

        for (int index = 0; index < exerciseGroupsCopied.size(); index++) {
            addExercisesToExerciseGroup(filteredExerciseGroupsToCopy.get(index), exerciseGroupsCopied.get(index), skippedExerciseTitles, incompleteExerciseTitles,
                    progressNotifier);
        }

        // A group whose exercises all failed to import is intentionally left in place (empty), not deleted: the failed
        // exercises are reported to the instructor via the skipped/incomplete lists, and an empty exercise group is rejected
        // later with a clear message by ExamService.validateForStudentExamGeneration ("all exercise groups must have at
        // least one exercise") when the instructor generates student exams. Deleting the group here is both unnecessary and
        // unsafe: Exam.exerciseGroups is an @OrderColumn list, so removing a non-last element mid-import can leave a gap in
        // the order column that Hibernate reloads as a null element, corrupting the exam.
    }

    /**
     * Helper method to create a copy of the given Exercises within one given exercise group and attaching them to the
     * given new exercise groups
     *
     * @param exerciseGroupToCopy      the exercise group to copy
     * @param exerciseGroupCopied      the copied exercise group, i.e. the ones attached to the new exam
     * @param skippedExerciseTitles    collects titles of exercises that were cleanly skipped (nothing persisted)
     * @param incompleteExerciseTitles collects titles of exercises that failed partway and may be incomplete (need review)
     * @param progressNotifier         reports live import progress per exercise to the importing user (no-op when nobody is listening)
     */
    private void addExercisesToExerciseGroup(ExerciseGroup exerciseGroupToCopy, ExerciseGroup exerciseGroupCopied, List<String> skippedExerciseTitles,
            List<String> incompleteExerciseTitles, ExamImportProgressNotifier progressNotifier) {
        // Copy each exercise within the existing Exercise Group
        for (Exercise exerciseToCopy : exerciseGroupToCopy.getExercises()) {
            final String exerciseTitle = exerciseToCopy.getTitle();
            progressNotifier.importing(exerciseTitle);
            try {
                // We need to set the new Exercise Group to the old exercise, so the new exercise group is correctly set for the new exercise
                exerciseToCopy.setExerciseGroup(exerciseGroupCopied);
                // Extract the source exercise ID and clear it from the skeleton exercise to avoid
                // Hibernate conflicts with managed entities that have the same ID in the persistence context
                Long sourceExerciseId = exerciseToCopy.getId();
                exerciseToCopy.setId(null);
                Optional<? extends Exercise> exerciseCopied = switch (exerciseToCopy.getExerciseType()) {
                    case MODELING -> {
                        if (modelingExerciseImportApi.isEmpty()) {
                            yield Optional.empty();
                        }
                        yield modelingExerciseImportApi.get().importModelingExercise(sourceExerciseId, (ModelingExercise) exerciseToCopy);
                    }

                    case TEXT -> {
                        if (textExerciseImportApi.isEmpty()) {
                            yield Optional.empty();
                        }
                        yield textExerciseImportApi.get().importTextExercise(sourceExerciseId, (TextExercise) exerciseToCopy);
                    }

                    case PROGRAMMING -> {
                        final Optional<ProgrammingExercise> optionalOriginalProgrammingExercise = programmingExerciseRepository
                                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigCategories(sourceExerciseId);
                        if (optionalOriginalProgrammingExercise.isEmpty()) {
                            yield Optional.empty();
                        }
                        var originalProgrammingExercise = optionalOriginalProgrammingExercise.get();
                        // Fetching the tasks separately, as putting it in the query above leads to Hibernate duplicating the tasks.
                        var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(originalProgrammingExercise.getId());
                        originalProgrammingExercise.setTasks(new ArrayList<>(templateTasks));
                        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(originalProgrammingExercise.getId());
                        originalProgrammingExercise.setGradingCriteria(gradingCriteria);

                        ProgrammingExercise newProgrammingExercise = (ProgrammingExercise) exerciseToCopy;
                        copyProgrammingExerciseInformationForExamImport(originalProgrammingExercise, newProgrammingExercise);
                        prepareProgrammingExerciseForExamImport(newProgrammingExercise);

                        yield Optional.of(programmingExerciseImportService.importProgrammingExercise(originalProgrammingExercise, newProgrammingExercise, false, false, false));
                    }

                    case FILE_UPLOAD -> {
                        if (fileUploadImportApi.isEmpty()) {
                            yield Optional.empty();
                        }
                        yield fileUploadImportApi.get().importFileUploadExercise(sourceExerciseId, (FileUploadExercise) exerciseToCopy);
                    }

                    case QUIZ -> {
                        // Use a query that eagerly loads quiz questions, grading criteria, and other needed associations
                        final Optional<QuizExercise> optionalOriginalQuizExercise = quizExerciseRepository
                                .findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(sourceExerciseId);
                        if (optionalOriginalQuizExercise.isEmpty()) {
                            yield Optional.empty();
                        }
                        var originalQuizExercise = optionalOriginalQuizExercise.get();
                        // The import service mutates the second parameter (importedExercise) in-place
                        // (e.g., nulling question IDs and clearing statistics). We must NOT pass the
                        // same managed entity for both parameters, as that would corrupt the original
                        // quiz in the L1 cache. The exerciseToCopy skeleton already has the correct
                        // exercise group, title, shortName, etc. from the DTO conversion.
                        // However, the skeleton does not contain quiz questions or batches (these are
                        // not part of ExerciseImportDTO), so we must copy them from the original.
                        QuizExercise quizSkeleton = (QuizExercise) exerciseToCopy;
                        quizSkeleton.setQuizQuestions(originalQuizExercise.getQuizQuestions());
                        quizSkeleton.setQuizBatches(originalQuizExercise.getQuizBatches());
                        // We don't allow a modification of the exercise at this point, so we can just pass an empty list of files.
                        yield Optional.of(quizExerciseImportService.importQuizExercise(originalQuizExercise, quizSkeleton, null));
                    }
                };
                // Attach the newly created Exercise to the new Exercise Group only if the importing was successful.
                // An empty result means the exercise could not be imported (e.g. the responsible import module is
                // unavailable or the source exercise no longer exists). Record it as a failure instead of silently
                // dropping it, otherwise the caller would report a full success while exercises went missing.
                if (exerciseCopied.isPresent()) {
                    exerciseGroupCopied.addExercise(exerciseCopied.get());
                    progressNotifier.imported(exerciseTitle);
                }
                else {
                    // The import returned no exercise without persisting anything (module unavailable or source exercise deleted): a clean skip.
                    log.warn("Could not import exercise '{}' during exam import (source exercise unavailable). Skipping it and continuing with the remaining exercises.",
                            exerciseTitle);
                    skippedExerciseTitles.add(failedExerciseLabel(exerciseTitle));
                    progressNotifier.skipped(exerciseTitle);
                }
            }
            catch (Exception exception) {
                // The import threw partway through: an entity may already have been persisted (e.g. a programming exercise whose
                // repository copy failed after its basis was committed). Report it as incomplete (may need review/removal) rather
                // than as a clean skip, and continue so that a single problematic exercise does not abort the whole exam import.
                log.error("Failed to import exercise '{}' during exam import. It may be incompletely imported; continuing with the remaining exercises.", exerciseTitle, exception);
                incompleteExerciseTitles.add(failedExerciseLabel(exerciseTitle));
                progressNotifier.incomplete(exerciseTitle);
            }
        }
        exerciseGroupRepository.save(exerciseGroupCopied);
    }

    /**
     * Returns a non-null, human-readable label for a failed exercise. The title can legitimately be {@code null}
     * (e.g. a programming exercise whose title was cleared during the uniqueness pre-check), and it is later reported
     * to the client, so we never add a raw {@code null} to the skipped/incomplete lists.
     *
     * @param title the (possibly {@code null}) exercise title
     * @return the title, or a placeholder when no title is available
     */
    private static String failedExerciseLabel(String title) {
        return (title == null || title.isBlank()) ? "(unnamed exercise)" : title;
    }

    /**
     * Prepares a Programming Exercise for the import by setting irrelevant data to null.
     * Additionally, the grading criteria is loaded and attached to the exercise, as this needs to be released before the import
     *
     * @param newExercise The new exercise which should be prepared for the import
     */
    private void prepareProgrammingExerciseForExamImport(final ProgrammingExercise newExercise) {

        // we do not support the following values as part of exam exercises
        newExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        newExercise.setSubmissionPolicy(null);
        newExercise.setStartDate(null);
        newExercise.setReleaseDate(null);
        newExercise.setDueDate(null);
        newExercise.setAssessmentDueDate(null);
        newExercise.setExampleSolutionPublicationDate(null);

        newExercise.forceNewProjectKey();
    }

    /**
     * Copies programming-specific fields that are not part of {@link de.tum.cit.aet.artemis.exam.dto.ExerciseImportDTO}.
     * The DTO intentionally only carries generic exercise fields and possible overrides such as title, short name, and points.
     *
     * @param originalExercise the source programming exercise with complete programming settings
     * @param newExercise      the exam-import skeleton created from {@link de.tum.cit.aet.artemis.exam.dto.ExerciseImportDTO}
     */
    static void copyProgrammingExerciseInformationForExamImport(final ProgrammingExercise originalExercise, final ProgrammingExercise newExercise) {
        newExercise.setProgrammingLanguage(originalExercise.getProgrammingLanguage());
        newExercise.setProjectType(originalExercise.getProjectType());
        newExercise.setPackageName(originalExercise.getPackageName());
        newExercise.setAllowOnlineEditor(originalExercise.isAllowOnlineEditor());
        newExercise.setAllowOfflineIde(originalExercise.isAllowOfflineIde());
        newExercise.setAllowOnlineIde(originalExercise.isAllowOnlineIde());
        newExercise.setStaticCodeAnalysisEnabled(originalExercise.isStaticCodeAnalysisEnabled());
        newExercise.setMaxStaticCodeAnalysisPenalty(originalExercise.getMaxStaticCodeAnalysisPenalty());
        newExercise.setShowTestNamesToStudents(originalExercise.getShowTestNamesToStudents());
        newExercise.setReleaseTestsWithExampleSolution(originalExercise.isReleaseTestsWithExampleSolution());
        newExercise.setAssessmentType(originalExercise.getAssessmentType());
        newExercise.setDifficulty(originalExercise.getDifficulty());
        newExercise.setMode(originalExercise.getMode());
        newExercise.setIncludedInOverallScore(originalExercise.getIncludedInOverallScore());
        newExercise.setAllowComplaintsForAutomaticAssessments(originalExercise.getAllowComplaintsForAutomaticAssessments());
        newExercise.setProblemStatement(originalExercise.getProblemStatement());
        newExercise.setGradingInstructions(originalExercise.getGradingInstructions());
        newExercise.setCategories(new HashSet<>(originalExercise.getCategories()));
    }

    /**
     * Helper method to create a copy of the given Exam without conduction specific attributes
     *
     * @param examToCopy the exam to be copied
     * @return a copy of the given exam without conduction specific attributes
     */
    private Exam createCopyOfExamWithoutConductionSpecificAttributes(Exam examToCopy, Course targetCourse) {
        examToCopy.setExerciseGroups(new ArrayList<>());
        examToCopy.setExamUsers(new HashSet<>());
        examToCopy.setStudentExams(new HashSet<>());
        examToCopy.setId(null);
        examToCopy.setCourse(targetCourse);
        return examRepository.save(examToCopy);
    }
}
