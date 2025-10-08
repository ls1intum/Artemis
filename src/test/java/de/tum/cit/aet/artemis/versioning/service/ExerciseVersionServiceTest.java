package de.tum.cit.aet.artemis.versioning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.versioning.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.versioning.dto.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.versioning.repository.FileUploadExerciseVersioningRepository;
import de.tum.cit.aet.artemis.versioning.repository.TextExerciseVersioningRepository;

@TestPropertySource(properties = { "artemis.exercise-versioning.enabled=true" })
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
    private TextExerciseVersioningRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseVersioningRepository fileUploadExerciseRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    private static final long TEST_WAIT_TIME = 100L;

    private static final TimeUnit TEST_TIME_UNIT = TimeUnit.MILLISECONDS;

    private static final BiPredicate<ZonedDateTime, ZonedDateTime> zonedDateTimeBiPredicate = (a,
            b) -> Math.abs(a.toInstant().getEpochSecond() - b.toInstant().getEpochSecond()) < 1;

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
    void testCreateExerciseVersion(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);

        // ExerciseVersionService.createExerciseVersion is executed only after current transaction is completed, and is marked as async,
        // programming exercise creation would take longer, due to git operations and setting up repositories takes time
        // using a small delay to avoid flaky tests.
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> {
            log.info("Exercise {} has been created", exercise.getId());
            return exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent();
        });

        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
        assertThat(version).as("ExerciseVersion should be created for exercise " + exercise.getId()).isNotNull();
        log.info("ExerciseVersion " + version.getId() + " has been created");

        var snapshot = version.getExerciseSnapshot();
        assertThat(version.getAuthor()).isNotNull();
        assertThat(version.getCreatedDate()).isNotNull();
        assertThat(version.getExerciseSnapshot()).isNotNull();

        final var eagerFetchedExercise = fetchExerciseForComparison(exercise);
        var newSnapshot = ExerciseSnapshotDTO.of(eagerFetchedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(newSnapshot);

    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());

        exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow();

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

        final var updatedExercise = updateExerciseByType(exercise);

        saveExerciseByType(updatedExercise);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).untilAsserted(() -> {
            var versions = exerciseVersionRepository.findAllByExerciseId(updatedExercise.getId());
            assertThat(versions).hasSizeGreaterThan(1);
        });

        var newVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(updatedExercise.getId());
        assertThat(newVersion).isPresent();

        var snapshot = newVersion.get().getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        var expectedSnapshot = ExerciseSnapshotDTO.of(updatedExercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExerciseVersionOnInvalidUpdate(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow();

        exercise.setTitle(exercise.getTitle());
        exercise.setProblemStatement(exercise.getProblemStatement());
        exercise.setMaxPoints(exercise.getMaxPoints());
        exercise.setBonusPoints(exercise.getBonusPoints());
        exercise.setAllowFeedbackRequests(exercise.getAllowFeedbackRequests());
        exercise.setAllowComplaintsForAutomaticAssessments(exercise.getAllowComplaintsForAutomaticAssessments());
        exercise.setIncludedInOverallScore(exercise.getIncludedInOverallScore());
        exercise.setReleaseDate(exercise.getReleaseDate());
        saveExerciseByType(exercise);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).untilAsserted(() -> {
            var versions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
            assertThat(versions).isNotEmpty();
        });

        var newVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
        assertThat(newVersion).isPresent();
        assertThat(newVersion.get().getId()).isEqualTo(previousVersion.getId());

        var snapshot = newVersion.get().getExerciseSnapshot();
        assertThat(snapshot).isNotNull();

        var expectedSnapshot = ExerciseSnapshotDTO.of(exercise, gitService);
        assertThat(snapshot).usingRecursiveComparison().withEqualsForType(zonedDateTimeBiPredicate, ZonedDateTime.class).isEqualTo(expectedSnapshot);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_templateRepository() throws Exception {
        var programmingExercise = createProgrammingExercise();
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isPresent());

        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();
        Long templateParticipationId = programmingExercise.getTemplateParticipation().getId();

        request.postWithoutLocation("/api/programming/repository/" + templateParticipationId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/repository/" + templateParticipationId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();

            assertThat(previousVersion.getId()).isNotEqualTo(version.get().getId());
            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().templateParticipation()).usingRecursiveComparison()
                    .isNotEqualTo(version.get().getExerciseSnapshot().programmingData().templateParticipation());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_solutionRepository() throws Exception {
        var programmingExercise = createProgrammingExercise();
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();

        Long solutionParticipationId = programmingExercise.getSolutionParticipation().getId();

        request.postWithoutLocation("/api/programming/repository/" + solutionParticipationId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/repository/" + solutionParticipationId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();

            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().solutionParticipation()).usingRecursiveComparison()
                    .isNotEqualTo(version.get().getExerciseSnapshot().programmingData().solutionParticipation());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_testsRepository() throws Exception {
        var programmingExercise = createProgrammingExercise();
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();

        request.postWithoutLocation("/api/programming/test-repository/" + programmingExercise.getId() + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/test-repository/" + programmingExercise.getId() + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();
            assertThat(version.get().getId()).isNotEqualTo(previousVersion.getId());
            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().testsCommitId()).isNotEqualTo(version.get().getExerciseSnapshot().programmingData().testsCommitId());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreationOnProcessNewPush_auxiliaryRepository() throws Exception {
        var programmingExercise = createProgrammingExercise();
        var previousCount = auxiliaryRepositoryRepository.count();

        AuxiliaryRepository auxRepo = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(programmingExercise);
        String auxRepoSlug = programmingExercise.generateRepositoryName(auxRepo.getRepositoryName());
        String auxRepoUri = localVCBaseUri + "/git/" + programmingExercise.getProjectKey() + "/" + auxRepoSlug + ".git";
        auxRepo.setRepositoryUri(auxRepoUri);
        localVCLocalCITestService.createAndConfigureLocalRepository(programmingExercise.getProjectKey(), auxRepoSlug);
        auxiliaryRepositoryRepository.save(auxRepo);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> auxiliaryRepositoryRepository.count() > previousCount);

        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();

        List<AuxiliaryRepository> auxiliaryRepositories = programmingExercise.getAuxiliaryRepositories();
        assertThat(auxiliaryRepositories).isNotEmpty();
        Long auxiliaryRepositoryId = auxiliaryRepositories.getFirst().getId();
        assertThat(auxiliaryRepositoryId).isNotNull();

        request.postWithoutLocation("/api/programming/auxiliary-repository/" + auxiliaryRepositoryId + "/file?file=Template.java", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/programming/auxiliary-repository/" + auxiliaryRepositoryId + "/commit", null, HttpStatus.OK, null);

        await().untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();
            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().auxiliaryRepositories()).usingRecursiveComparison()
                    .isNotEqualTo(version.get().getExerciseSnapshot().programmingData().auxiliaryRepositories());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreation_resetStaticCodeAnalysisCategories() throws Exception {
        var programmingExercise = createProgrammingExercise();
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();

        request.patch("/api/programming/programming-exercises/" + programmingExercise.getId() + "/static-code-analysis-categories/reset", null, HttpStatus.OK);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();

            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().staticCodeAnalysisCategories()).usingRecursiveComparison()
                    .isNotEqualTo(version.get().getExerciseSnapshot().programmingData().staticCodeAnalysisCategories());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreation_toggleSubmissionPolicy() throws Exception {
        var programmingExercise = createProgrammingExercise();
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElseThrow();

        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/submission-policy?activate=false", null, HttpStatus.OK);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(version).isPresent();

            assertThat(version.get().getExerciseSnapshot()).isNotNull();
            assertThat(version.get().getExerciseSnapshot().programmingData()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().programmingData().submissionPolicy()).usingRecursiveComparison()
                    .isNotEqualTo(version.get().getExerciseSnapshot().programmingData().submissionPolicy());
        });
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVersionCreation_competencyExerciseLink(ExerciseType exerciseType) {
        Exercise exercise = createExerciseByType(exerciseType);
        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).until(() -> exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).isPresent());
        var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElseThrow();
        competencyUtilService.createCompetencyWithExercise(exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);

        await().during(TEST_WAIT_TIME, TEST_TIME_UNIT).untilAsserted(() -> {
            Optional<ExerciseVersion> version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
            assertThat(version).isPresent();
            assertThat(version.get().getExerciseSnapshot()).isNotNull();

            assertThat(version.get().getExerciseSnapshot().competencyLinks()).isNotNull();
            assertThat(previousVersion.getExerciseSnapshot().competencyLinks()).isNotEqualTo(version.get().getExerciseSnapshot().competencyLinks());
        });
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

        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        programmingExercise = programmingExerciseRepository.findForVersioningById(programmingExercise.getId()).orElseThrow();
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);

        var penaltyPolicy = new SubmissionPenaltyPolicy();
        penaltyPolicy.setSubmissionLimit(7);
        penaltyPolicy.setExceedingPenalty(1.2);
        penaltyPolicy.setActive(true);
        penaltyPolicy.setProgrammingExercise(programmingExercise);
        penaltyPolicy = submissionPolicyRepository.saveAndFlush(penaltyPolicy);
        programmingExercise.setSubmissionPolicy(penaltyPolicy);
        programmingExerciseRepository.saveAndFlush(programmingExercise);

        String projectKey = programmingExercise.getProjectKey();
        try {
            programmingExercise = programmingExerciseRepository.findForVersioningById(programmingExercise.getId()).orElseThrow();

            programmingExercise.setAuxiliaryRepositories(new ArrayList<>());

            String templateRepositorySlug = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
            TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
            templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);
            programmingExercise.setTemplateParticipation(templateParticipation);

            String solutionRepositorySlug = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
            SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
            solutionParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
            programmingExercise.setSolutionParticipation(solutionParticipation);

            String testSlug = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
            String testRepositoryUri = localVCBaseUri + "/git/" + projectKey + "/" + testSlug + ".git";
            programmingExercise.setTestRepositoryUri(testRepositoryUri);
            programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, testSlug);

            programmingExerciseRepository.saveAndFlush(programmingExercise);

        }
        catch (GitAPIException | IOException | URISyntaxException e) {
            log.error("Failed to create programming exercise", e);
        }

        // Check that the repository folders were created in the file system for all
        // base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExercise, localVCBasePath);

        programmingExercise = programmingExerciseRepository.findForVersioningById(programmingExercise.getId()).orElseThrow();
        return programmingExercise;
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
        return switch (exercise) {
            case ProgrammingExercise pExercise -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(pExercise);
            case QuizExercise qExercise -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(qExercise);
            case TextExercise tExercise -> textExerciseRepository.findForVersioningById(exercise.getId()).orElse(tExercise);
            case ModelingExercise mExercise -> modelingExerciseRepository.findForVersioningById(exercise.getId()).orElse(mExercise);
            case FileUploadExercise fExercise -> fileUploadExerciseRepository.findForVersioningById(exercise.getId()).orElse(fExercise);
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
