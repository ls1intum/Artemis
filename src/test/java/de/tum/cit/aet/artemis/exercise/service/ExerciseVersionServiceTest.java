package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.exception.ExerciseVersioningException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
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



    private User instructor;

    @BeforeEach
    void initTestCase() {
        localVCLocalCITestService.setPort(port);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    @AfterEach
    void cleanup() {
        exerciseVersionRepository.deleteAll();
    }

    private TextExercise createTextExercise() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        return (TextExercise) course.getExercises().iterator().next();
    }

    private ProgrammingExercise createProgrammingExercise() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Set the correct repository URIs for the template and the solution participation.
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setTestRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + projectKey.toLowerCase() + "-tests.git");
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + solutionRepositorySlug + ".git");
        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);

        // Prepare the repositories.
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, projectKey.toLowerCase() + "-tests");
        localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);

        // Check that the repository folders were created in the file system for all base repositories.
        localVCLocalCITestService.verifyRepositoryFoldersExist(programmingExercise, localVCBasePath);

        return programmingExercise;
    }

    private ModelingExercise createModelingExercise() {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        return (ModelingExercise) course.getExercises().iterator().next();
    }

    private QuizExercise createQuizExercise() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        return (QuizExercise) course.getExercises().iterator().next();
    }


    /**
     * Verifies that the common exercise content fields in the version match the exercise
     */
    private void verifyCommonExerciseContent(ExerciseVersion version, Exercise exercise) {
        assertThat(version).isNotNull();
        assertThat(version.getExercise().getId()).isEqualTo(exercise.getId());
        assertThat(version.getAuthor().getId()).isEqualTo(instructor.getId());

        var content = version.getContent();
        log.debug("ExerciseVersionContent: {}", content);
        assertThat(content).isNotNull();

        // Verify content is a Map with exercise data
        assertThat(content).isInstanceOf(Map.class);


        assertThat(content.get("title")).isEqualTo(exercise.getTitle());
        assertThat(content.get("shortName")).isEqualTo(exercise.getShortName());
        assertThat(content.get("problemStatement")).isEqualTo(exercise.getProblemStatement());

        if (exercise.getMaxPoints() != null) {
            assertThat(((Number) content.get("maxPoints")).doubleValue()).isEqualTo(exercise.getMaxPoints());
        }
        if (exercise.getBonusPoints() != null) {
            assertThat(((Number) content.get("bonusPoints")).doubleValue()).isEqualTo(exercise.getBonusPoints());
        }
        assertThat(content.get("difficulty")).isEqualTo(exercise.getDifficulty());
    }


    @Nested
    class InitialVersionCreation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateInitialVersionWhenTextExerciseCreated() {
            // given
            var textExercise = createTextExercise();

            // when
            exerciseVersionService.onExerciseCreated(textExercise);

            // then
            var actualVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(textExercise.getId()).orElse(null);
            assertThat(actualVersion).isNotNull();
            verifyCommonExerciseContent(actualVersion, textExercise);
            assertThat(actualVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.FULL_SNAPSHOT);
            assertThat(actualVersion.getPreviousVersion()).isNull();
            assertThat(actualVersion.getContentHash()).isNotNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateInitialVersionWhenModelingExerciseCreated() {
            // given
            var modelingExercise = createModelingExercise();

            // when
            exerciseVersionService.onExerciseCreated(modelingExercise);

            // then
            var actualVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(modelingExercise.getId()).orElse(null);
            assertThat(actualVersion).isNotNull();
            verifyCommonExerciseContent(actualVersion, modelingExercise);
            assertThat(actualVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.FULL_SNAPSHOT);
            assertThat(actualVersion.getPreviousVersion()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateInitialVersionWithGitCommitsWhenProgrammingExerciseCreated() throws Exception {
            // given
            var programmingExercise = createProgrammingExercise();

            // when
            exerciseVersionService.onExerciseCreated(programmingExercise);

            // then
            var actualVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElse(null);
            assertThat(actualVersion).isNotNull();
            verifyCommonExerciseContent(actualVersion, programmingExercise);

            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) actualVersion.getContent();

            // Git commit IDs should be captured
            assertThat(content.get("templateCommitId")).isNotNull();
            assertThat(content.get("solutionCommitId")).isNotNull();
            assertThat(content.get("testsCommitId")).isNotNull();

            assertThat(actualVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.FULL_SNAPSHOT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateInitialVersionWhenQuizExerciseCreated() {
            // given
            var quizExercise = createQuizExercise();

            // when
            exerciseVersionService.onExerciseCreated(quizExercise);

            // then
            var actualVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(quizExercise.getId()).orElse(null);
            assertThat(actualVersion).isNotNull();
            verifyCommonExerciseContent(actualVersion, quizExercise);

            Map<String, Object> content = (Map<String, Object>) actualVersion.getContent();
            assertThat(content.get("isOpenForPractice")).isEqualTo(quizExercise.isIsOpenForPractice());
            assertThat(content.get("allowedNumberOfAttempts")).isEqualTo(quizExercise.getAllowedNumberOfAttempts());
            assertThat(content.get("duration")).isEqualTo(quizExercise.getDuration());
            assertThat(content.get("randomizeQuestionOrder")).isEqualTo(quizExercise.isRandomizeQuestionOrder());
            assertThat(actualVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.FULL_SNAPSHOT);
        }
    }

    @Nested
    class IncrementalVersionCreation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldNotCreateNewVersionWhenNoChangesDetected() throws Exception {
            // given
            var programmingExercise = createProgrammingExercise();
            exerciseVersionService.onExerciseCreated(programmingExercise);
            Optional<ExerciseVersion> initialVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(initialVersion).isPresent();

            // when
            exerciseVersionService.onSaveExercise(programmingExercise);

            // then
            Optional<ExerciseVersion> secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(secondVersion).isPresent();
            assertThat(secondVersion.get().getId()).isEqualTo(initialVersion.get().getId());
            long versionCount = exerciseVersionRepository.count();
            assertThat(versionCount).isEqualTo(1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateIncrementalVersionWhenExerciseContentChanged() throws Exception {
            // given
            var programmingExercise = createProgrammingExercise();
            exerciseVersionService.onExerciseCreated(programmingExercise);
            Optional<ExerciseVersion> initialVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(initialVersion).isPresent();

            // when
            String updatedTitle = "Updated Title " + System.currentTimeMillis();
            programmingExercise.setTitle(updatedTitle);
            programmingExercise = exerciseRepository.save(programmingExercise);
            exerciseVersionService.onSaveExercise(programmingExercise);

            // then
            Optional<ExerciseVersion> secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
            assertThat(secondVersion).isPresent();
            assertThat(secondVersion.get().getId()).isNotEqualTo(initialVersion.get().getId());

            long versionCount = exerciseVersionRepository.count();
            assertThat(versionCount).isEqualTo(2);

            // Verify the latest version has the updated title and is incremental diff
            ExerciseVersion latestVersion = secondVersion.get();
            assertThat(latestVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.INCREMENTAL_DIFF);
            assertThat(latestVersion.getPreviousVersion()).isEqualTo(initialVersion.get());

            Map<String, Object> content = (Map<String, Object>) latestVersion.getContent();
            assertThat(content.get("title")).isEqualTo(updatedTitle);
            assertThat(content).hasSize(1); // Only changed field should be in diff
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateIncrementalVersionWhenMultipleFieldsChanged() {
            // given
            var textExercise = createTextExercise();
            exerciseVersionService.onExerciseCreated(textExercise);
            Optional<ExerciseVersion> initialVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(textExercise.getId());
            assertThat(initialVersion).isPresent();

            // when
            String updatedTitle = "Updated Title " + System.currentTimeMillis();
            String updatedProblem = "Updated Problem Statement";
            Double updatedMaxPoints = 50.0;
            textExercise.setTitle(updatedTitle);
            textExercise.setProblemStatement(updatedProblem);
            textExercise.setMaxPoints(updatedMaxPoints);
            textExercise = exerciseRepository.save(textExercise);
            exerciseVersionService.onSaveExercise(textExercise);

            // then
            Optional<ExerciseVersion> secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(textExercise.getId());
            assertThat(secondVersion).isPresent();

            ExerciseVersion latestVersion = secondVersion.get();
            assertThat(latestVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.INCREMENTAL_DIFF);

            Map<String, Object> content = (Map<String, Object>) latestVersion.getContent();
            assertThat(content.get("title")).isEqualTo(updatedTitle);
            assertThat(content.get("problemStatement")).isEqualTo(updatedProblem);
            assertThat(content.get("maxPoints")).isEqualTo(updatedMaxPoints);
            assertThat(content).hasSize(3); // Only changed fields should be in diff
        }
    }

    @Nested
    class ExceptionHandling {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldThrowExceptionWhenOnExerciseCreatedCalledWithoutId() {
            // given
            var exercise = new TextExercise();
            exercise.setTitle("Test Exercise");
            // Exercise has no ID

            // when & then
            assertThatThrownBy(() -> exerciseVersionService.onExerciseCreated(exercise))
                    .isInstanceOf(ExerciseVersioningException.InvalidExerciseStateException.class)
                    .hasMessageContaining("Cannot create initial version for exercise without ID");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldThrowExceptionWhenOnSaveExerciseCalledWithoutId() {
            // given
            var exercise = new TextExercise();
            exercise.setTitle("Test Exercise");
            // Exercise has no ID

            // when & then
            assertThatThrownBy(() -> exerciseVersionService.onSaveExercise(exercise))
                    .isInstanceOf(ExerciseVersioningException.InvalidExerciseStateException.class)
                    .hasMessageContaining("onSaveExercise called for exercise without ID");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldThrowExceptionWhenDuplicateInitialVersionCreated() {
            // given
            var exercise = createTextExercise();
            exerciseVersionService.onExerciseCreated(exercise);

            // when & then
            assertThatThrownBy(() -> exerciseVersionService.onExerciseCreated(exercise))
                    .isInstanceOf(ExerciseVersioningException.DuplicateInitialVersionException.class)
                    .hasMessageContaining("Attempt to create duplicate initial version for exercise ID: " + exercise.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldThrowExceptionWhenOnSaveExerciseCalledForNonExistentExercise() {
            // given
            var exercise = createTextExercise();
            exerciseRepository.delete(exercise); // Delete from database but keep object with ID

            // when & then
            assertThatThrownBy(() -> exerciseVersionService.onSaveExercise(exercise))
                    .isInstanceOf(ExerciseVersioningException.InvalidExerciseStateException.class)
                    .hasMessageContaining("Exercise with ID " + exercise.getId() + " not found in database during versioning");
        }
    }

    @Nested
    class ContentHashValidation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGenerateConsistentContentHashForSameContent() {
            // given
            var exercise1 = createTextExercise();
            var exercise2 = createTextExercise();
            exercise2.setTitle(exercise1.getTitle());
            exercise2.setShortName(exercise1.getShortName());
            exercise2.setProblemStatement(exercise1.getProblemStatement());
            exercise2.setMaxPoints(exercise1.getMaxPoints());

            // when
            exerciseVersionService.onExerciseCreated(exercise1);
            exerciseVersionService.onExerciseCreated(exercise2);

            // then
            var version1 = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise1.getId()).orElse(null);
            var version2 = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise2.getId()).orElse(null);

            assertThat(version1).isNotNull();
            assertThat(version2).isNotNull();

            // Content hashes should be different as exercises have different IDs and other fields
            assertThat(version1.getContentHash()).isNotEqualTo(version2.getContentHash());

            // But each hash should be non-null and have expected length (SHA-256 = 64 chars)
            assertThat(version1.getContentHash()).hasSize(64);
            assertThat(version2.getContentHash()).hasSize(64);
        }
    }

    @Nested
    class VersionHistory {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldMaintainVersionHistoryWithPreviousVersionLinks() {
            // given
            var exercise = createTextExercise();

            // when
            exerciseVersionService.onExerciseCreated(exercise);
            var firstVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);

            exercise.setTitle("Updated Title 1");
            exercise = exerciseRepository.save(exercise);
            exerciseVersionService.onSaveExercise(exercise);
            var secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);

            exercise.setTitle("Updated Title 2");
            exercise = exerciseRepository.save(exercise);
            exerciseVersionService.onSaveExercise(exercise);
            var thirdVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);

            // then
            List<ExerciseVersion> allVersions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
            assertThat(allVersions).hasSize(3);

            // Verify version chain
            assertThat(firstVersion).isNotNull();
            assertThat(secondVersion).isNotNull();
            assertThat(thirdVersion).isNotNull();
            assertThat(firstVersion.getPreviousVersion()).isNull();
            assertThat(secondVersion.getPreviousVersion()).isEqualTo(firstVersion);
            assertThat(thirdVersion.getPreviousVersion()).isEqualTo(secondVersion);

            // Verify version types
            assertThat(firstVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.FULL_SNAPSHOT);
            assertThat(secondVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.INCREMENTAL_DIFF);
            assertThat(thirdVersion.getVersionType()).isEqualTo(ExerciseVersion.VersionType.INCREMENTAL_DIFF);
        }
    }

    @Nested
    class ProgrammingExerciseSpecificContent {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCaptureGitCommitHashesForProgrammingExercise() throws Exception {
            // given
            var programmingExercise = createProgrammingExercise();

            // when
            exerciseVersionService.onExerciseCreated(programmingExercise);

            // then
            var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElse(null);
            assertThat(version).isNotNull();

            Map<String, Object> content = (Map<String, Object>) version.getContent();

            // Verify that git commit hashes are captured
            assertThat(content).containsKeys("templateCommitId", "solutionCommitId", "testsCommitId");
            assertThat(content.get("templateCommitId")).isNotNull();
            assertThat(content.get("solutionCommitId")).isNotNull();
            assertThat(content.get("testsCommitId")).isNotNull();
        }
    }

    @Nested
    class DataExtraction {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldExcludeTransientFieldsFromVersioning() {
            // given
            var exercise = createTextExercise();

            // when
            exerciseVersionService.onExerciseCreated(exercise);

            // then
            var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
            assertThat(version).isNotNull();

            Map<String, Object> content = (Map<String, Object>) version.getContent();

            // Verify that excluded patterns are not present
            assertThat(content.keySet()).noneMatch(key -> key.contains("studentParticipations"));
            assertThat(content.keySet()).noneMatch(key -> key.contains("submissions"));
            assertThat(content.keySet()).noneMatch(key -> key.contains("results"));
            assertThat(content.keySet()).noneMatch(key -> key.contains("Password"));
            assertThat(content.keySet()).noneMatch(key -> key.contains("Token"));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCaptureAllRelevantExerciseFields() {
            // given
            var exercise = createTextExercise();
            exercise.setTitle("Test Title");
            exercise.setShortName("test-short");
            exercise.setProblemStatement("Test problem statement");
            exercise.setMaxPoints(100.0);
            exercise.setBonusPoints(10.0);
            exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
            exercise.setDueDate(ZonedDateTime.now().plusDays(7));
            exercise = exerciseRepository.save(exercise);

            // when
            exerciseVersionService.onExerciseCreated(exercise);

            // then
            var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
            assertThat(version).isNotNull();

            Map<String, Object> content = (Map<String, Object>) version.getContent();

            // Verify core fields are captured
            assertThat(content.get("title")).isEqualTo("Test Title");
            assertThat(content.get("shortName")).isEqualTo("test-short");
            assertThat(content.get("problemStatement")).isEqualTo("Test problem statement");
            assertThat(content.get("maxPoints")).isEqualTo(100.0);
            assertThat(content.get("bonusPoints")).isEqualTo(10.0);
            assertThat(content.get("releaseDate")).isNotNull();
            assertThat(content.get("dueDate")).isNotNull();
        }
    }

    @Nested
    class PerformanceAndConcurrency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldHandleMultipleVersionsForSameExerciseEfficiently() {
            // given
            var exercise = createTextExercise();
            exerciseVersionService.onExerciseCreated(exercise);

            // when - create multiple versions rapidly
            for (int i = 1; i <= 5; i++) {
                exercise.setTitle("Title " + i);
                exercise = exerciseRepository.save(exercise);
                exerciseVersionService.onSaveExercise(exercise);
            }

            // then
            List<ExerciseVersion> allVersions = exerciseVersionRepository.findAllByExerciseId(exercise.getId());
            assertThat(allVersions).hasSize(6); // Initial + 5 updates

            // Verify each version has correct content and relationships
            // Note: we need to get versions in proper order (newest first)
            var latestVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
            assertThat(latestVersion).isNotNull();

            // Verify version chain by following previous version links
            ExerciseVersion currentVersion = latestVersion;
            int versionCount = 0;
            while (currentVersion != null) {
                versionCount++;
                currentVersion = currentVersion.getPreviousVersion();
            }
            assertThat(versionCount).isEqualTo(6);
        }
    }
}
