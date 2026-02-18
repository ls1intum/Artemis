package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService.zonedDateTimeBiPredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.RecordComponent;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncEventType;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseNewVersionAlertDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseVersionUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
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
    void testCreateExerciseVersionOnUpdate(ExerciseType exerciseType) {
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
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isNotEqualTo(previousVersion.getExerciseSnapshot());
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnInvalidUpdate(ExerciseType exerciseType) {
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
        ExerciseSnapshotDTO expectedSnapshot = ExerciseSnapshotDTO.of(fetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
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
