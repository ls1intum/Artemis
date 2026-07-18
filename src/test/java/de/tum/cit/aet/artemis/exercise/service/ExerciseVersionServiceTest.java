package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.domain.review.ReviewThreadSyncAction;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncEventType;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewCommitAlertDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewVersionAlertDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseReviewThreadUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.review.CommentThreadRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVersionServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "exerciseversiontest";

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionServiceTest.class);

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommentThreadRepository commentThreadRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionUtilService exerciseVersionUtilService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void tearDown() {
        exerciseVersionRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnCreate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOrThrow_returnsTheCreatedVersionId(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        User author = userTestRepository.findOneByLogin(TEST_PREFIX + "instructor1").orElseThrow();

        Long returnedVersionId = exerciseVersionService.createExerciseVersionOrThrow(exercise, author);

        ExerciseVersion persistedVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
        assertThat(returnedVersionId).isNotNull().isEqualTo(persistedVersion.getId());
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOrThrow_returnsNullWhenSnapshotIsUnchanged(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        User author = userTestRepository.findOneByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        Long firstVersionId = exerciseVersionService.createExerciseVersionOrThrow(exercise, author);
        assertThat(firstVersionId).isNotNull();

        Long secondVersionId = exerciseVersionService.createExerciseVersionOrThrow(exercise, author);

        assertThat(secondVersionId).isNull();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnUpdate(ExerciseType exerciseType) throws Exception {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);

        ExerciseVersionUtilService.updateExercise(exercise);
        Exercise updatedExercise = updateExerciseByType(exercise);
        saveExerciseByType(updatedExercise);
        exerciseVersionService.createExerciseVersion(updatedExercise);

        var versions = exerciseVersionRepository.findAllByExerciseId(updatedExercise.getId());
        assertThat(versions).hasSizeGreaterThan(1);

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
        assertThat(newVersion.getId()).isNotEqualTo(previousVersion.getId());

        ExerciseSnapshotDTO snapshot = newVersion.getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        Exercise fetchedExercise = fetchExerciseForComparison(exercise);
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, ExerciseVersionCommitHashResolver.resolveForExercise(fetchedExercise, gitService));
        // Compare via JSON strings to avoid null vs empty list mismatches from @JsonInclude(NON_EMPTY) round-trip
        assertThat(objectMapper.writeValueAsString(snapshot)).isEqualTo(objectMapper.writeValueAsString(expectedSnapshot));
        assertThat(objectMapper.writeValueAsString(snapshot)).isNotEqualTo(objectMapper.writeValueAsString(previousVersion.getExerciseSnapshot()));
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = "QUIZ", mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnInvalidUpdate(ExerciseType exerciseType) throws Exception {
        Exercise exercise = createExerciseByType(exerciseType);
        exerciseVersionService.createExerciseVersion(exercise);
        ExerciseVersion previousVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);

        // save again to db without changing versionable data
        Course newCourse = courseUtilService.addEmptyCourse();
        exercise.setCourse(newCourse);
        saveExerciseByType(exercise);
        exerciseVersionService.createExerciseVersion(exercise);

        var versions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
        assertThat(versions).isNotEmpty();

        ExerciseVersion newVersion = exerciseVersionUtilService.verifyExerciseVersionCreated(exercise.getId(), TEST_PREFIX + "instructor1", exerciseType);
        assertThat(newVersion.getId()).isEqualTo(previousVersion.getId());

        ExerciseSnapshotDTO snapshot = newVersion.getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        Exercise fetchedExercise = fetchExerciseForComparison(exercise);
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, ExerciseVersionCommitHashResolver.resolveForExercise(fetchedExercise, gitService));
        // Compare via JSON strings to avoid null vs empty list mismatches from @JsonInclude(NON_EMPTY) round-trip
        assertThat(objectMapper.writeValueAsString(snapshot)).isEqualTo(objectMapper.writeValueAsString(expectedSnapshot));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnNullExercise() {
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(null);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnNullExerciseId() {
        Exercise exercise = createExerciseByType(ExerciseType.TEXT);
        exercise.setId(null);
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(exercise);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnNullUser() {
        Exercise exercise = createExerciseByType(ExerciseType.TEXT);
        var previousCount = exerciseVersionRepository.count();
        exerciseVersionService.createExerciseVersion(exercise, null);
        var afterCount = exerciseVersionRepository.count();
        assertThat(afterCount).isEqualTo(previousCount);
    }

    /**
     * {@code findForVersioningById} was changed from a single {@code @EntityGraph} (which produced a Cartesian product of
     * the independent {@code @OneToMany} collections) to a base query plus one lean query per large collection, merged in
     * Java. This test guards that refactor: it asserts every collection that versioning relies on is fully loaded, using
     * ground-truth counts from the setup helpers (not another {@code findForVersioningById} call), so a collection that the
     * multi-query fetch failed to load or merge would fail here rather than silently drop data from the version snapshot.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindForVersioningByIdLoadsAllIndependentCollections() {
        // Set up a programming exercise with the independent collections that previously formed the Cartesian product:
        // test cases, tasks (each linked to its test cases) and static code analysis categories.
        ProgrammingExercise exercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        var createdTestCases = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);
        // Reload so the in-memory exercise carries its test cases; addTasksToProgrammingExercise builds one task per test case.
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        programmingExerciseUtilService.addTasksToProgrammingExercise(exercise);

        ProgrammingExercise fetched = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();

        // Each independent collection is fully loaded; the counts are ground truth from the setup, so a short/dropped
        // collection fails here.
        assertThat(fetched.getTestCases()).hasSize(createdTestCases.size());
        assertThat(fetched.getTasks()).hasSize(createdTestCases.size());
        // tasks.testCases must be loaded too (the deepest part of the former Cartesian product): exactly one test case per task.
        assertThat(fetched.getTasks()).allSatisfy(task -> assertThat(task.getTestCases()).hasSize(1));
        assertThat(fetched.getTasks().stream().mapToLong(task -> task.getTestCases().size()).sum()).isEqualTo(createdTestCases.size());
        // The static code analysis categories created by the helper are loaded as well.
        assertThat(fetched.getStaticCodeAnalysisCategories()).isNotEmpty();
        // A to-one association from the base query is present, confirming the base query still loads its own attribute paths.
        assertThat(fetched.getBuildConfig()).isNotNull();
    }

    private Exercise createExerciseByType(ExerciseType exerciseType) {
        return switch (exerciseType) {
            case TEXT -> createTextExercise();
            case PROGRAMMING -> createProgrammingExercise();
            case QUIZ -> createQuizExercise();
            case MODELING -> createModelingExercise();
            case FILE_UPLOAD -> createFileUploadExercise();
        };
    }

    private TextExercise createTextExercise() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        return (TextExercise) course.getExercises().iterator().next();
    }

    private ProgrammingExercise createProgrammingExercise() {

        ProgrammingExercise newProgrammingExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(newProgrammingExercise);

        var penaltyPolicy = new SubmissionPenaltyPolicy();
        penaltyPolicy.setSubmissionLimit(7);
        penaltyPolicy.setExceedingPenalty(1.2);
        penaltyPolicy.setActive(true);
        penaltyPolicy.setProgrammingExercise(newProgrammingExercise);
        penaltyPolicy = submissionPolicyRepository.saveAndFlush(penaltyPolicy);
        newProgrammingExercise.setSubmissionPolicy(penaltyPolicy);
        programmingExerciseRepository.saveAndFlush(newProgrammingExercise);

        try {
            newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();

            newProgrammingExercise.setAuxiliaryRepositories(new ArrayList<>());

            RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, newProgrammingExercise);
            templateProgrammingExerciseParticipationRepository.save(newProgrammingExercise.getTemplateParticipation());
            solutionProgrammingExerciseParticipationRepository.save(newProgrammingExercise.getSolutionParticipation());

            newProgrammingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            programmingExerciseRepository.saveAndFlush(newProgrammingExercise);

        }
        catch (Exception e) {
            log.error("Failed to create programming exercise", e);
        }

        // Check that the repository folders were created in the file system for all
        // base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(newProgrammingExercise, localVCBasePath);

        newProgrammingExercise = programmingExerciseRepository.findForVersioningById(newProgrammingExercise.getId()).orElseThrow();
        return newProgrammingExercise;
    }

    private ModelingExercise createModelingExercise() {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        // Create a modeling exercise
        Exercise exercise = course.getExercises().iterator().next();
        return modelingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
    }

    private QuizExercise createQuizExercise() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        quizExerciseRepository.flush();
        return (QuizExercise) course.getExercises().iterator().next();
    }

    private FileUploadExercise createFileUploadExercise() {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        fileUploadExerciseRepository.flush();
        return (FileUploadExercise) course.getExercises().iterator().next();
    }

    private Exercise fetchExerciseForComparison(Exercise exercise) {
        Exercise fetched = switch (exercise) {
            case ProgrammingExercise pExercise -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(pExercise);
            case QuizExercise qExercise -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(qExercise);
            case TextExercise tExercise -> textExerciseRepository.findForVersioningById(exercise.getId()).orElse(tExercise);
            case ModelingExercise mExercise -> modelingExerciseRepository.findForVersioningById(exercise.getId()).orElse(mExercise);
            case FileUploadExercise fExercise -> fileUploadExerciseRepository.findForVersioningById(exercise.getId()).orElse(fExercise);
            default -> exercise;
        };
        var channel = channelRepository.findChannelByExerciseId(fetched.getId());
        if (channel != null) {
            fetched.setChannelName(channel.getName());
        }
        return fetched;
    }

    private void saveExerciseByType(Exercise exercise) {
        switch (exercise) {
            case TextExercise textExercise -> textExerciseRepository.saveAndFlush(textExercise);
            case ProgrammingExercise newProgrammingExercise -> programmingExerciseRepository.saveAndFlush(newProgrammingExercise);
            case QuizExercise quizExercise -> quizExerciseRepository.saveAndFlush(quizExercise);
            case ModelingExercise modelingExercise -> modelingExerciseRepository.saveAndFlush(modelingExercise);
            case FileUploadExercise fileUploadExercise -> fileUploadExerciseRepository.saveAndFlush(fileUploadExercise);
            default -> throw new IllegalArgumentException("Unsupported exercise type");
        }
    }

    private Exercise updateExerciseByType(Exercise exercise) {
        return switch (exercise) {
            case TextExercise textExercise:
                textExercise.setExampleSolution("Updated example solution");
                yield textExercise;
            case ProgrammingExercise newProgrammingExercise:
                ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(newProgrammingExercise, exercise.getShortName(), "Updated Title", true, ProgrammingLanguage.SWIFT);
                yield newProgrammingExercise;
            case QuizExercise quizExercise:
                quizExerciseUtilService.emptyOutQuizExercise(quizExercise);
                yield quizExercise;
            case ModelingExercise modelingExercise:
                modelingExercise.setExampleSolutionModel("Updated example solution");
                modelingExercise.setExampleSolutionExplanation("Updated example explanation");
                modelingExercise.setDiagramType(DiagramType.CommunicationDiagram);
                yield modelingExercise;
            case FileUploadExercise fileUploadExercise:
                fileUploadExercise.setExampleSolution("Updated example solution");
                fileUploadExercise.setFilePattern("Updated file pattern");
                yield fileUploadExercise;
            default:
                throw new IllegalArgumentException("Unsupported exercise type");
        };
    }

    /**
     * Ensures no synchronization messages are sent for the initial version.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoSynchronizationBroadcastWhenNoPreviousVersion() {
        ProgrammingExercise exercise = createProgrammingExercise();
        reset(websocketMessagingService);

        exerciseVersionService.createExerciseVersion(exercise);

        // No synchronization should be broadcast for the initial version
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSynchronizationBroadcastForEveryChangedAuxiliaryRepository() throws Exception {
        ProgrammingExercise exercise = createProgrammingExercise();
        String repositoryBaseUrl = exercise.getTestRepositoryUri().substring(0, exercise.getTestRepositoryUri().lastIndexOf('/') + 1);
        AuxiliaryRepository firstRepository = createAuxiliaryRepository(exercise, "auxiliary-one",
                repositoryBaseUrl + exercise.getProjectKey().toLowerCase() + "-auxiliary-one.git");
        AuxiliaryRepository secondRepository = createAuxiliaryRepository(exercise, "auxiliary-two",
                repositoryBaseUrl + exercise.getProjectKey().toLowerCase() + "-auxiliary-two.git");
        exercise.setAuxiliaryRepositories(new ArrayList<>(List.of(firstRepository, secondRepository)));
        programmingExerciseRepository.saveAndFlush(exercise);
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        Long firstRepositoryId = exercise.getAuxiliaryRepositories().get(0).getId();
        Long secondRepositoryId = exercise.getAuxiliaryRepositories().get(1).getId();

        doReturn("first-old").when(gitServiceSpy).getLastCommitHash(argThat(uri -> uri.toString().endsWith("-auxiliary-one.git")));
        doReturn("second-old").when(gitServiceSpy).getLastCommitHash(argThat(uri -> uri.toString().endsWith("-auxiliary-two.git")));
        exerciseVersionService.createExerciseVersion(exercise);
        reset(websocketMessagingService);

        doReturn("first-new").when(gitServiceSpy).getLastCommitHash(argThat(uri -> uri.toString().endsWith("-auxiliary-one.git")));
        doReturn("second-new").when(gitServiceSpy).getLastCommitHash(argThat(uri -> uri.toString().endsWith("-auxiliary-two.git")));
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        exerciseVersionService.createExerciseVersion(exercise);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService, atLeast(2)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), captor.capture());
        assertThat(captor.getAllValues()).filteredOn(ExerciseNewCommitAlertDTO.class::isInstance).map(ExerciseNewCommitAlertDTO.class::cast)
                .filteredOn(payload -> payload.target() == ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY).extracting(ExerciseNewCommitAlertDTO::auxiliaryRepositoryId)
                .containsExactlyInAnyOrder(firstRepositoryId, secondRepositoryId);
    }

    private AuxiliaryRepository createAuxiliaryRepository(ProgrammingExercise exercise, String name, String repositoryUri) {
        AuxiliaryRepository repository = new AuxiliaryRepository();
        repository.setExercise(exercise);
        repository.setName(name);
        repository.setDescription(name);
        repository.setCheckoutDirectory(name);
        repository.setRepositoryUri(repositoryUri);
        return repository;
    }

    /**
     * Ensures metadata alerts are broadcast when metadata changes without commit changes.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMetadataSynchronizationBroadcastWhenNoCommitChanges() {
        ProgrammingExercise exercise = createProgrammingExercise();
        exerciseVersionService.createExerciseVersion(exercise);
        reset(websocketMessagingService);

        // Update without changing any repository commits
        exercise.setTitle("New Title");
        programmingExerciseRepository.saveAndFlush(exercise);
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();

        exerciseVersionService.createExerciseVersion(exercise);

        // Metadata synchronization should be broadcast when no commits have changed
        var captor = ArgumentCaptor.forClass(ExerciseNewVersionAlertDTO.class);
        verify(websocketMessagingService, times(1)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), captor.capture());
        var payload = captor.getValue();
        assertThat(payload.exerciseVersionId()).isNotNull();
        assertThat(payload.eventType()).isEqualTo(ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT);
        assertThat(payload.target()).isEqualTo(ExerciseEditorSyncTarget.EXERCISE_METADATA);
        assertThat(payload.author()).isNotNull();
        assertThat(payload.changedFields()).contains("title");
    }

    /**
     * Ensures channel name changes are reported via metadata alerts.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMetadataSynchronizationBroadcastWhenChannelNameChanges() {
        ProgrammingExercise exercise = createProgrammingExercise();
        exerciseVersionService.createExerciseVersion(exercise);
        reset(websocketMessagingService);

        var channel = channelRepository.findChannelByExerciseId(exercise.getId());
        if (channel == null) {
            channel = conversationUtilService.addChannelToExercise(exercise);
        }
        assertThat(channel).isNotNull();
        channel.setName("exercise-updated-channel");
        channelRepository.saveAndFlush(channel);

        exerciseVersionService.createExerciseVersion(exercise);

        var captor = ArgumentCaptor.forClass(ExerciseNewVersionAlertDTO.class);
        verify(websocketMessagingService, times(1)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), captor.capture());
        var payload = captor.getValue();
        assertThat(payload.changedFields()).contains("channelName");
    }

    /**
     * Ensures auxiliary repository metadata changes are reported via metadata alerts.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMetadataSynchronizationBroadcastWhenAuxiliaryRepositoryMetadataChanges() {
        ProgrammingExercise exercise = createProgrammingExercise();
        exerciseVersionService.createExerciseVersion(exercise);
        reset(websocketMessagingService);

        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(exercise);
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        programmingExerciseRepository.saveAndFlush(exercise);

        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();
        exerciseVersionService.createExerciseVersion(exercise);

        var captor = ArgumentCaptor.forClass(ExerciseNewVersionAlertDTO.class);
        verify(websocketMessagingService, times(1)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), captor.capture());
        var payload = captor.getValue();
        assertThat(payload.changedFields()).contains("programmingData.auxiliaryRepositories");
    }

    /**
     * Ensures review thread updates are synchronized when line references change after a new exercise version.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReviewThreadSynchronizationBroadcastWhenThreadLocationChanges() {
        ProgrammingExercise exercise = createProgrammingExercise();
        exercise.setProblemStatement("line-1\nline-2\nline-3\n");
        programmingExerciseRepository.saveAndFlush(exercise);

        exerciseVersionService.createExerciseVersion(exercise);

        CommentThread thread = new CommentThread();
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialLineNumber(2);
        thread.setLineNumber(2);
        thread.setOutdated(false);
        thread.setResolved(false);
        commentThreadRepository.saveAndFlush(thread);

        reset(websocketMessagingService);

        exercise.setProblemStatement("line-1\ninserted\nline-2\nline-3\n");
        programmingExerciseRepository.saveAndFlush(exercise);
        exercise = programmingExerciseRepository.findForVersioningById(exercise.getId()).orElseThrow();

        exerciseVersionService.createExerciseVersion(exercise);

        var captor = ArgumentCaptor.forClass(ExerciseReviewThreadUpdateDTO.class);
        verify(websocketMessagingService, times(1)).sendMessage(eq("/topic/exercises/" + exercise.getId() + "/synchronization"), captor.capture());
        var payload = captor.getValue();

        assertThat(payload.eventType()).isEqualTo(ExerciseEditorSyncEventType.REVIEW_THREAD_UPDATE);
        assertThat(payload.target()).isEqualTo(ExerciseEditorSyncTarget.REVIEW_COMMENTS);
        assertThat(payload.action()).isEqualTo(ReviewThreadSyncAction.THREAD_UPDATED);
        assertThat(payload.thread()).isNotNull();
        assertThat(payload.thread().id()).isEqualTo(thread.getId());
        assertThat(payload.thread().lineNumber()).isEqualTo(3);
        assertThat(payload.thread().outdated()).isFalse();
    }

    /**
     * Ensures that every field in {@link ExerciseSnapshotDTO} is either tracked by
     * {@code collectChangedFields} or explicitly excluded. If this test fails, a new
     * field was added to the snapshot DTO without updating the change detection logic.
     */
    @Test
    void testCollectChangedFieldsCoversAllExerciseSnapshotFields() {
        Set<String> allFields = Arrays.stream(ExerciseSnapshotDTO.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());

        // Fields covered by addIfChanged calls in ExerciseVersionService.collectChangedFields
        Set<String> coveredFields = Set.of("title", "shortName", "channelName", "competencyLinks", "maxPoints", "bonusPoints", "assessmentType", "releaseDate", "startDate",
                "dueDate", "assessmentDueDate", "exampleSolutionPublicationDate", "difficulty", "mode", "allowComplaintsForAutomaticAssessments", "allowFeedbackRequests",
                "includedInOverallScore", "gradingInstructions", "categories", "teamAssignmentConfig", "presentationScoreEnabled", "secondCorrectionEnabled",
                "feedbackSuggestionModule", "gradingCriteria", "plagiarismDetectionConfig");

        // Fields intentionally excluded from metadata sync change detection
        Set<String> excludedFields = Set.of("id", // structural identifier, not editable metadata
                "problemStatement", // synchronized via Yjs client-to-client, not metadata sync
                "programmingData", // delegated to collectProgrammingChanges
                "textData", // exercise-type-specific sync not yet implemented
                "modelingData", // exercise-type-specific sync not yet implemented
                "quizData", // exercise-type-specific sync not yet implemented
                "fileUploadData" // exercise-type-specific sync not yet implemented
        );

        Set<String> accountedFor = new java.util.HashSet<>(coveredFields);
        accountedFor.addAll(excludedFields);
        assertThat(accountedFor).as("Every ExerciseSnapshotDTO field must be either covered or explicitly excluded in collectChangedFields").isEqualTo(allFields);
    }

    /**
     * Ensures that every field in {@link ProgrammingExerciseSnapshotDTO} is either tracked by
     * {@code collectProgrammingChanges} or explicitly excluded.
     */
    @Test
    void testCollectProgrammingChangesCoversAllProgrammingSnapshotFields() {
        Set<String> allFields = Arrays.stream(ProgrammingExerciseSnapshotDTO.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());

        // Fields covered by addIfChanged calls in ExerciseVersionService.collectProgrammingChanges
        Set<String> coveredFields = Set.of("allowOnlineEditor", "allowOfflineIde", "allowOnlineIde", "maxStaticCodeAnalysisPenalty", "showTestNamesToStudents",
                "auxiliaryRepositories", "buildAndTestStudentSubmissionsAfterDueDate", "releaseTestsWithExampleSolution", "buildConfig");

        // Fields intentionally excluded: not editable on the exercise edit page or handled separately
        Set<String> excludedFields = Set.of("testRepositoryUri", // not editable
                "staticCodeAnalysisEnabled", // not editable after creation
                "programmingLanguage", // not editable after creation
                "packageName", // not editable after creation
                "projectKey", // not editable
                "projectType", // not editable after creation
                "templateParticipation", // repository commit, handled by determineSynchronizationForActiveEditors
                "solutionParticipation", // repository commit, handled by determineSynchronizationForActiveEditors
                "testsCommitId", // repository commit, handled by determineSynchronizationForActiveEditors
                "testCases", // not editable via metadata sync
                "tasks", // not editable via metadata sync
                "staticCodeAnalysisCategories", // not editable via metadata sync
                "submissionPolicy" // not editable on the exercise edit page
        );

        Set<String> accountedFor = new java.util.HashSet<>(coveredFields);
        accountedFor.addAll(excludedFields);
        assertThat(accountedFor).as("Every ProgrammingExerciseSnapshotDTO field must be either covered or explicitly excluded in collectProgrammingChanges").isEqualTo(allFields);
    }

}
