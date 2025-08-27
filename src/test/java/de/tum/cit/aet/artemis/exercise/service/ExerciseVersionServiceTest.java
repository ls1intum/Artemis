package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseSnapshot;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVersionServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exerciseversiontest";

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionServiceTest.class);

    @Value("${artemis.version-control.url}")
    private String localVCBaseUrl;

    @LocalServerPort
    private int port;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionRepository exerciseVersionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    private static final long testWaitTime = 2L;

    private static final BiPredicate<ZonedDateTime, ZonedDateTime> zonedDateTimeBiPredicate = (a, b) -> {
        return a.toInstant().equals(b.toInstant());
    };

    @BeforeEach
    void init() {
        localVCLocalCITestService.setPort(port);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @AfterEach
    void tearDown() {
        exerciseVersionRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersion(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);

        // ExerciseVersionService.createExerciseVerion is marked with @Async,
        // so we need to wait for the async task to finish
        // "during" is needed because for createProgrammingExercise(), we have some "update" actions that
        await().during(testWaitTime, TimeUnit.SECONDS).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());

        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);

        assertThat(version).as("ExerciseVersion should be created for exercise " + exercise.getId()).isNotNull();
        log.info("found ExerciseVersion with id {}", version.getId());

        var snapshot = version.getExerciseSnapshot();
        assertThat(version.getAuthor()).isNotNull();
        assertThat(version.getCreatedDate()).isNotNull();
        assertThat(version.getExerciseSnapshot()).isNotNull();

        final var eagerFetchedExercise = fetchExerciseForComparison(exercise);
        var newSnapshot = ExerciseSnapshot.of(eagerFetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(newSnapshot);

    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        // ExerciseVersionService.createExerciseVerion is marked with @Async,
        // so we need to wait for the async task to finish
        // "during" is needed because for createProgrammingExercise(), we have some "update" actions that
        await().during(testWaitTime, TimeUnit.SECONDS).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());

        exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow();

        // Update various Exercise fields
        exercise.setTitle("Updated Title");
        exercise.setProblemStatement("Updated problem statement");
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(5.0);
        exercise.setAllowFeedbackRequests(true);
        exercise.setAllowComplaintsForAutomaticAssessments(true);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        exercise.setGradingInstructions("Updated grading instructions");
        exercise.setPresentationScoreEnabled(true);
        exercise.setSecondCorrectionEnabled(true);
        exercise.setDifficulty(DifficultyLevel.HARD);
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setAssessmentType(AssessmentType.MANUAL);

        ZonedDateTime now = ZonedDateTime.now();
        exercise.setReleaseDate(now.minusDays(1));
        exercise.setDueDate(now.plusDays(7));
        exercise.setAssessmentDueDate(now.plusDays(10));
        exercise.setExampleSolutionPublicationDate(now.plusDays(12));

        //
        final var updatedExercise = updateExerciseByType(exercise);
        // Save the updated exercise to trigger version creation
        saveExerciseByType(exercise);

        // Wait for new version to be created
        await().during(testWaitTime, TimeUnit.SECONDS).untilAsserted(() -> {
            var versions = exerciseVersionRepository.findAllByExerciseId(updatedExercise.getId());
            assertThat(versions.size() > 1);
        });

        // Get the new version
        var newVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(updatedExercise.getId());
        assertThat(newVersion).isPresent();

        // Verify that the new version contains the updated field values
        var snapshot = newVersion.get().getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        var expectedSnapshot = ExerciseSnapshot.of(updatedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnInvalidUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        // ExerciseVersionService.createExerciseVerion is marked with @Async,
        // so we need to wait for the async task to finish
        // "during" is needed because for createProgrammingExercise(), we have some "update" actions that
        await().during(testWaitTime, TimeUnit.SECONDS).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());

        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow();

        // Update various Exercise fields
        exercise.setTitle(exercise.getTitle());
        exercise.setProblemStatement(exercise.getProblemStatement());
        exercise.setMaxPoints(exercise.getMaxPoints());
        exercise.setBonusPoints(exercise.getBonusPoints());
        exercise.setAllowFeedbackRequests(exercise.getAllowFeedbackRequests());
        exercise.setAllowComplaintsForAutomaticAssessments(exercise.getAllowComplaintsForAutomaticAssessments());
        exercise.setIncludedInOverallScore(exercise.getIncludedInOverallScore());
        exercise.setReleaseDate(exercise.getReleaseDate());

        // Save the non-updated exercise to try to trigger version creation
        saveExerciseByType(exercise);

        // Wait for new version to be created
        await().during(testWaitTime, TimeUnit.SECONDS).untilAsserted(() -> {
            var versions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
            assertThat(!versions.isEmpty());
        });

        // Get the new version
        var newVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
        assertThat(newVersion).isPresent();
        assertThat(newVersion.get().getId()).isEqualTo(previousVersion.getId());

        // Verify that the new version contains the updated field values
        var snapshot = newVersion.get().getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        var expectedSnapshot = ExerciseSnapshot.of(exercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
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

        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerForVersioningById(programmingExercise.getId()).orElseThrow();

        String projectKey = programmingExercise.getProjectKey();
        try {
            programmingExercise = programmingExerciseRepository.findWithEagerForVersioningById(programmingExercise.getId()).orElseThrow();
            String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
            TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
            templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);

            programmingExercise = programmingExerciseRepository.findWithEagerForVersioningById(programmingExercise.getId()).orElseThrow();
            String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
            SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
            solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);

            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);
            programmingExercise.setTemplateParticipation(templateParticipation);
            programmingExercise.setSolutionParticipation(solutionParticipation);

            // Set the correct repository URIs for the template and the solution participation.
            String testSlug = projectKey.toLowerCase() + "tests";
            String testRepositoryUri = localVCBaseUrl + "/git/" + projectKey + "/" + testSlug + ".git";
            programmingExercise.setTestRepositoryUri(testRepositoryUri);
            programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, testSlug);
            programmingExerciseRepository.saveAndFlush(programmingExercise);
        }
        catch (GitAPIException | IOException | URISyntaxException e) {
            log.error("Failed to create programming exercise", e);
        }
        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExercise, localVCBasePath);

        programmingExercise = programmingExerciseRepository.findWithEagerForVersioningById(programmingExercise.getId()).orElseThrow();
        return programmingExercise;
    }

    private ModelingExercise createModelingExercise() {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        // Create a modeling exercise
        Exercise exercise = course.getExercises().iterator().next();
        return modelingExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElseThrow();
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
        return switch (exercise) {
            case ProgrammingExercise pExercise -> programmingExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElse(pExercise);
            case QuizExercise qExercise -> quizExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElse(qExercise);
            case TextExercise tExercise -> textExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElse(tExercise);
            case ModelingExercise mExercise -> modelingExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElse(mExercise);
            case FileUploadExercise fExercise -> fileUploadExerciseRepository.findWithEagerForVersioningById(exercise.getId()).orElse(fExercise);
            default -> exercise;
        };
    }

    private void saveExerciseByType(Exercise exercise) {
        switch (exercise) {
            case TextExercise textExercise -> textExerciseRepository.saveAndFlush(textExercise);
            case ProgrammingExercise programmingExercise -> programmingExerciseRepository.saveAndFlush(programmingExercise);
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
            case ProgrammingExercise programmingExercise:
                ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, exercise.getShortName(), "Updated Title", true, ProgrammingLanguage.SWIFT);
                yield programmingExercise;
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

}
