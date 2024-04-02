package de.tum.in.www1.artemis.entitylistener;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ResultListenerIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "resultlistenerintegrationtest";

    private Long idOfIndividualTextExercise;

    private Long idOfTeamTextExercise;

    private Long idOfTeam1;

    private Long idOfStudent1;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private StudentScoreRepository studentScoreRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResultService resultService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @AfterEach
    void cleanup() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    @BeforeEach
    void setupTestScenario() {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 100;
        participantScoreScheduleService.activate();
        ZonedDateTime pastReleaseDate = ZonedDateTime.now().minusDays(5);
        ZonedDateTime pastDueDate = ZonedDateTime.now().minusDays(3);
        ZonedDateTime pastAssessmentDueDate = ZonedDateTime.now().minusDays(2);

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        idOfStudent1 = student1.getId();
        // creating course
        Course course = courseUtilService.createCourse();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastReleaseDate, pastDueDate, pastAssessmentDueDate);
        idOfIndividualTextExercise = textExercise.getId();
        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastReleaseDate, pastDueDate, pastAssessmentDueDate);
        idOfTeamTextExercise = teamExercise.getId();
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, "team1").getId();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExercisePoints_ShouldUpdatePointsInParticipantScores(boolean isTeamTest) throws Exception {
        setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Exercise exercise;
        if (isTeamTest) {
            exercise = exerciseRepository.findById(idOfTeamTextExercise).orElseThrow();
        }
        else {
            exercise = exerciseRepository.findById(idOfIndividualTextExercise).orElseThrow();
        }
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(100.0);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/text-exercises", exercise, HttpStatus.OK);
        List<ParticipantScore> savedParticipantScores = participantScoreRepository.findAllByExercise(exercise);
        assertThat(savedParticipantScores).isNotEmpty().hasSize(1);
        ParticipantScore savedParticipantScore = savedParticipantScores.get(0);
        assertThat(savedParticipantScore.getLastPoints()).isEqualTo(200.0);
        assertThat(savedParticipantScore.getLastRatedPoints()).isEqualTo(200.0);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_ShouldCreateStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(true, isTeamTest);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_ShouldCreateStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(true, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalStudentScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalStudentScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_saveAnotherUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_saveAnotherRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_saveAnotherUnratedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
        resultService.deleteResult(newResult, true);
        assertThat(resultRepository.findById(originalResult.getId())).isPresent();
        assertThat(resultRepository.findById(newResult.getId())).isEmpty();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_saveAnotherRatedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
        resultService.deleteResult(newResult, true);
        assertThat(resultRepository.findById(newResult.getId())).isEmpty();
        assertThat(resultRepository.findById(originalResult.getId())).isPresent();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherUnratedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultService.deleteResult(newResult, true);
        assertThat(resultRepository.findById(originalResult.getId())).isPresent();
        assertThat(resultRepository.findById(newResult.getId())).isEmpty();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherRatedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
        resultService.deleteResult(newResult, true);
        assertThat(resultRepository.findById(newResult.getId())).isEmpty();
        assertThat(resultRepository.findById(originalResult.getId())).isPresent();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_changeScoreOfResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setScore(0D);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_makeResultRated_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setRated(true);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), updatedResult.getId(), updatedResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_changeScoreOfResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setScore(0D);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), updatedResult.getId(), updatedResult.getScore());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_makeResultUnrated_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setRated(null);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_removeSavedResult_ShouldRemoveAssociatedStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result persistedResult = originalParticipantScore.getLastResult();
        // removing the result should trigger the entity listener and remove the associated student score
        resultService.deleteResult(persistedResult, true);

        // Wait for the scheduler to execute its task
        await().until(() -> participantScoreScheduleService.isIdle());

        assertThat(studentScoreRepository.findById(originalParticipantScore.getId())).isEmpty();
        assertThat(resultRepository.findById(persistedResult.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveUnratedResult_then_removeSavedResult_ShouldRemoveAssociatedStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result persistedResult = originalParticipantScore.getLastResult();
        // removing the result should trigger the entity listener and remove the associated student score
        resultService.deleteResult(persistedResult, true);

        // Wait for the scheduler to execute its task
        await().until(() -> participantScoreScheduleService.isIdle());

        assertThat(studentScoreRepository.findById(originalParticipantScore.getId())).isEmpty();
        assertThat(resultRepository.findById(persistedResult.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherUnratedResult_then_removeRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultService.deleteResult(originalResult, true);
        assertThat(resultRepository.findById(originalResult.getId())).isEmpty();
        assertThat(resultRepository.findById(newResult.getId())).isPresent();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void saveRatedResult_then_saveAnotherUnratedResult_then_removeUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultService.deleteResult(newResult, true);
        assertThat(resultRepository.findById(newResult.getId())).isEmpty();
        assertThat(resultRepository.findById(originalResult.getId())).isPresent();
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    private void assertParticipantScoreStructure(ParticipantScore participantScore, Long expectedExerciseId, Long expectedParticipantId, Long expectedLastResultId,
            Double expectedLastScore, Long expectedLastRatedResultId, Double expectedLastRatedScore, Double expectedLastPoints, Double expectedLastRatedPoints) {
        assertThat(participantScore.getExercise().getId()).isEqualTo(expectedExerciseId);

        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            assertThat(studentScore.getUser().getId()).isEqualTo(expectedParticipantId);
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            assertThat(teamScore.getTeam().getId()).isEqualTo(expectedParticipantId);
        }

        if (expectedLastResultId == null) {
            assertThat(participantScore.getLastResult()).isNull();
        }
        else {
            assertThat(participantScore.getLastResult().getId()).isEqualTo(expectedLastResultId);
        }
        assertThat(participantScore.getLastScore()).isEqualTo(expectedLastScore);
        assertThat(participantScore.getLastPoints()).isEqualTo(expectedLastPoints);

        if (expectedLastRatedResultId == null) {
            assertThat(participantScore.getLastRatedResult()).isNull();
        }
        else {
            assertThat(participantScore.getLastRatedResult().getId()).isEqualTo(expectedLastRatedResultId);
        }
        assertThat(participantScore.getLastRatedScore()).isEqualTo(expectedLastRatedScore);
        assertThat(participantScore.getLastRatedPoints()).isEqualTo(expectedLastRatedPoints);
    }

    private Result createNewResult(boolean isTeamTest, boolean isRated) {
        StudentParticipation studentParticipation;
        SecurityUtils.setAuthorizationObject();
        if (isTeamTest) {
            studentParticipation = studentParticipationRepository.findAllWithTeamStudentsByExerciseIdAndTeamStudentId(idOfTeamTextExercise, idOfStudent1).get(0);
        }
        else {
            studentParticipation = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1).get(0);
        }
        return participationUtilService.createSubmissionAndResult(studentParticipation, 100, isRated);
    }

    private ParticipantScore setupTestScenarioWithOneResultSaved(boolean isRatedResult, boolean isTeam) {
        Long idOfExercise;
        Participant participant;
        if (isTeam) {
            participant = teamRepository.findById(idOfTeam1).orElseThrow();
            idOfExercise = idOfTeamTextExercise;
        }
        else {
            participant = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            idOfExercise = idOfIndividualTextExercise;
        }

        var exercise = exerciseRepository.findById(idOfExercise).orElseThrow();

        Result persistedResult = participationUtilService.createParticipationSubmissionAndResult(idOfExercise, participant, 10.0, 10.0, 200, isRatedResult);

        // Wait for the scheduler to execute its task
        participantScoreScheduleService.executeScheduledTasks();
        await().until(() -> participantScoreScheduleService.isIdle());

        var savedParticipantScores = participantScoreRepository.findAllByExercise(exercise);
        assertThat(savedParticipantScores).isNotEmpty();
        assertThat(savedParticipantScores).hasSize(1);
        ParticipantScore savedParticipantScore = savedParticipantScores.get(0);
        Double pointsAchieved = round(persistedResult.getScore() * 0.01 * 10.0);
        if (isRatedResult) {
            assertParticipantScoreStructure(savedParticipantScore, idOfExercise, participant.getId(), persistedResult.getId(), persistedResult.getScore(), persistedResult.getId(),
                    persistedResult.getScore(), pointsAchieved, pointsAchieved);
        }
        else {
            assertParticipantScoreStructure(savedParticipantScore, idOfExercise, participant.getId(), persistedResult.getId(), persistedResult.getScore(), null, null,
                    pointsAchieved, null);
        }
        return savedParticipantScore;
    }

    private void verifyStructureOfParticipantScoreInDatabase(boolean isTeamTest, Long expectedLastResultId, Double expectedLastScore, Long expectedLastRatedResultId,
            Double expectedLastRatedScore) {
        Long idOfExercise;
        Participant participant;
        if (isTeamTest) {
            participant = teamRepository.findById(idOfTeam1).orElseThrow();
            idOfExercise = idOfTeamTextExercise;
        }
        else {
            participant = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            idOfExercise = idOfIndividualTextExercise;
        }

        var exercise = exerciseRepository.findById(idOfExercise).orElseThrow();

        // Wait for the scheduler to execute its task
        participantScoreScheduleService.executeScheduledTasks();
        await().until(() -> participantScoreScheduleService.isIdle());

        List<ParticipantScore> savedParticipantScore = participantScoreRepository.findAllByExercise(exercise);
        assertThat(savedParticipantScore).isNotEmpty();
        assertThat(savedParticipantScore).hasSize(1);
        ParticipantScore updatedParticipantScore = savedParticipantScore.get(0);
        Double lastPoints = null;
        Double lastRatedPoints = null;
        if (expectedLastScore != null) {
            lastPoints = round(expectedLastScore * 0.01 * 10.0);
        }
        if (expectedLastRatedScore != null) {
            lastRatedPoints = round(expectedLastRatedScore * 0.01 * 10.0);
        }

        assertParticipantScoreStructure(updatedParticipantScore, idOfExercise, participant.getId(), expectedLastResultId, expectedLastScore, expectedLastRatedResultId,
                expectedLastRatedScore, lastPoints, lastRatedPoints);
    }

}
