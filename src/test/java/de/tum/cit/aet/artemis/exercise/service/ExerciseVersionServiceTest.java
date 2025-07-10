package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersionContent;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseVersionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseversiontest";

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

    private User user;

    @BeforeEach
    void init() {
        // Create a user with instructor permissions
        userUtilService.addInstructor("testgroup", TEST_PREFIX + "user");
        user = userUtilService.getUserByLogin(TEST_PREFIX + "user");
    }

    @AfterEach
    void tearDown() {
        exerciseVersionRepository.deleteAll();
    }

    private TextExercise createTextExercise() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        // Create a text exercise
        return (TextExercise) course.getExercises().iterator().next();
    }

    private ProgrammingExercise createProgrammingExercise() {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Create a programming exercise
        return (ProgrammingExercise) course.getExercises().iterator().next();
    }

    private ModelingExercise createModelingExercise() {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        // Create a modeling exercise
        return (ModelingExercise) course.getExercises().iterator().next();
    }

    private QuizExercise createQuizExercise() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        // Create a quiz exercise
        return (QuizExercise) course.getExercises().iterator().next();
    }

    /**
     * Verifies that the common exercise content fields in the version match the exercise
     *
     * @param version  the exercise version to verify
     * @param exercise the exercise to compare against
     */
    private void verifyCommonExerciseContent(ExerciseVersion version, Exercise exercise) {

        assertThat(version).isNotNull();
        // Verify version was created
        assertThat(version.getExercise().getId()).isEqualTo(exercise.getId());
        assertThat(version.getAuthor().getId()).isEqualTo(user.getId());

        var content = version.getContent();
        assertThat(content).isNotNull();
        assertThat(content.title()).isEqualTo(exercise.getTitle());
        assertThat(content.shortName()).isEqualTo(exercise.getShortName());
        assertThat(content.problemStatement()).isEqualTo(exercise.getProblemStatement());

        assertThat(content.startDate()).isCloseTo(exercise.getStartDate(), within(1, ChronoUnit.SECONDS));
        assertThat(content.releaseDate()).isCloseTo(exercise.getReleaseDate(), within(1, ChronoUnit.SECONDS));
        assertThat(content.dueDate()).isCloseTo(exercise.getDueDate(), within(1, ChronoUnit.SECONDS));

        assertThat(content.maxPoints()).isCloseTo(exercise.getMaxPoints(), within(0.1));
        assertThat(content.bonusPoints()).isCloseTo(exercise.getBonusPoints(), within(0.1));
        assertThat(content.difficulty()).isEqualTo(exercise.getDifficulty());
    }

    private void verifyLastCommit(VcsRepositoryUri uri, String savedCommitId) {
        var commitHash = gitService.getLastCommitHash(uri);
        assertThat(commitHash).isNotNull();
        assertThat(savedCommitId).isEqualTo(commitHash.getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_textExercise() {
        var exercise = createTextExercise();
        // Create version
        exerciseVersionService.createExerciseVersion(exercise, user);

        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
        // Verify common exercise content
        verifyCommonExerciseContent(version, exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_modelingExercise() {
        var exercise = createModelingExercise();
        // Create version
        exerciseVersionService.createExerciseVersion(exercise, user);
        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId()).orElse(null);
        // Verify common exercise content
        verifyCommonExerciseContent(version, exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_programmingExercise() {
        var programmingExercise = createProgrammingExercise();
        // Create version
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId()).orElse(null);

        // Verify common exercise content
        verifyCommonExerciseContent(version, programmingExercise);

        // Verify programming exercise specific content
        ExerciseVersionContent content = version.getContent();

        verifyLastCommit(programmingExercise.getTemplateParticipation().getVcsRepositoryUri(), content.templateCommitId());
        verifyLastCommit(programmingExercise.getSolutionParticipation().getVcsRepositoryUri(), content.solutionCommitId());
        verifyLastCommit(programmingExercise.getVcsTestRepositoryUri(), content.testsCommitId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_quizExercise() {
        var quizExercise = createQuizExercise();
        // Create version
        exerciseVersionService.createExerciseVersion(quizExercise, user);
        var version = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(quizExercise.getId()).orElse(null);

        // Verify common exercise content
        verifyCommonExerciseContent(version, quizExercise);

        // Verify quiz exercise specific content
        ExerciseVersionContent content = version.getContent();
        assertThat(content.isOpenForPractice()).isEqualTo(quizExercise.isIsOpenForPractice());
        assertThat(content.allowedNumberOfAttempts()).isEqualTo(quizExercise.getAllowedNumberOfAttempts()); // Default number of questions added by factory
        assertThat(content.duration()).isEqualTo(quizExercise.getDuration());
        assertThat(content.randomizeQuestionOrder()).isEqualTo(quizExercise.isRandomizeQuestionOrder());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_noNewVersionNeeded() {
        var programmingExercise = createProgrammingExercise();
        // Create initial version
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        // find it from the repository
        Optional<ExerciseVersion> initialVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
        assertThat(initialVersion).isPresent();

        // Try to create another version without changes
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        Optional<ExerciseVersion> secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());

        // Verify no new version was created
        assertThat(secondVersion).isPresent();
        assertThat(secondVersion.get().getId()).isEqualTo(initialVersion.get().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "INSTRUCTOR")
    void testCreateExerciseVersion_newVersionNeeded() {
        var programmingExercise = createProgrammingExercise();

        // Create initial version
        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        Optional<ExerciseVersion> initialVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
        assertThat(initialVersion).isPresent();

        // Make a change to the exercise
        String updatedTitle = "Updated Title " + System.currentTimeMillis();
        programmingExercise.setTitle(updatedTitle);
        programmingExercise = exerciseRepository.save(programmingExercise);

        // Create another version

        exerciseVersionService.createExerciseVersion(programmingExercise, user);
        // find the version from the repo, but should be the same as the first one
        Optional<ExerciseVersion> secondVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
        // Second version should not be null since changes were made
        assertThat(secondVersion).isPresent();
        // Second version should not be the same as the first version
        assertThat(secondVersion.get().getId()).isNotEqualTo(initialVersion.get().getId());

        // Verify two versions exist
        long versionCount = exerciseVersionRepository.count();
        assertThat(versionCount).isEqualTo(2);

        // Verify the latest version has the updated title
        Optional<ExerciseVersion> latestVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(programmingExercise.getId());
        assertThat(latestVersion).isPresent();
        assertThat(latestVersion.get().getContent().title()).isEqualTo(updatedTitle);
    }

}
