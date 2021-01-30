package de.tum.in.www1.artemis.entitylistener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ScoreService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ResultListenerIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    Long idOfCourse;

    Long idOfIndividualTextExercise;

    Long idOfTeamTextExercise;

    Long idOfTeam1;

    Long idOfStudent1;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ParticipationService participationService;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    StudentScoreRepository studentScoreRepository;

    @Autowired
    ParticipantScoreRepository participantScoreRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    UserRepository userRepository;

    @SpyBean
    private ScoreService scoreService;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    public void setupTestScenario() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student5, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(5, 10, 10);
        User student1 = userRepository.findOneByLogin("student1").get();
        idOfStudent1 = student1.getId();
        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();
        createIndividualTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createTeamTextExerciseAndTeam(pastTimestamp, pastTimestamp, pastTimestamp);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_ShouldCreateStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(true, isTeamTest);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_ShouldCreateStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(true, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalStudentScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalStudentScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_saveAnotherUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_saveAnotherRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        setupTestScenarioWithOneResultSaved(false, isTeamTest);
        // creating a new rated result should trigger the entity listener and update the student score
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_saveAnotherUnratedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
        resultRepository.deleteById(newResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_saveAnotherRatedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
        resultRepository.deleteById(newResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherUnratedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultRepository.deleteById(newResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherRatedResult_thenRemoveSecondResult_ShouldUpdateStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, true);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), newResult.getId(), newResult.getScore());
        resultRepository.deleteById(newResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_changeScoreOfResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setScore(0L);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_makeResultRated_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setRated(true);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), updatedResult.getId(), updatedResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_changeScoreOfResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setScore(0L);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), updatedResult.getId(), updatedResult.getScore());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_makeResultUnrated_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // update the associated student score should trigger the entity listener and update the student score
        originalResult.setRated(null);
        Result updatedResult = resultRepository.saveAndFlush(originalResult);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, updatedResult.getId(), updatedResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_removeSavedResult_ShouldRemoveAssociatedStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result persistedResult = originalParticipantScore.getLastResult();
        // removing the result should trigger the entity listener and remove the associated student score
        resultRepository.deleteById(persistedResult.getId());
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        assertThat(savedResults).isEmpty();
        verify(this.scoreService, times(1)).removeOrUpdateAssociatedParticipantScore(any(Result.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveUnratedResult_then_removeSavedResult_ShouldRemoveAssociatedStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(false, isTeamTest);
        Result persistedResult = originalParticipantScore.getLastResult();
        // removing the result should trigger the entity listener and remove the associated student score
        resultRepository.deleteById(persistedResult.getId());
        List<StudentScore> savedStudentScores = studentScoreRepository.findAll();
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedStudentScores).isEmpty();
        assertThat(savedResults).isEmpty();
        verify(this.scoreService, times(1)).removeOrUpdateAssociatedParticipantScore(any(Result.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherUnratedResult_then_removeRatedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultRepository.deleteById(originalResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), null, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void saveRatedResult_then_saveAnotherUnratedResult_then_removeUnratedResult_ShouldUpdateOriginalStudentScore(boolean isTeamTest) {
        ParticipantScore originalParticipantScore = setupTestScenarioWithOneResultSaved(true, isTeamTest);
        Result originalResult = originalParticipantScore.getLastResult();
        // creating a new rated result should trigger the entity listener and update the student score BUT only the not rated part
        Result newResult = createNewResult(isTeamTest, false);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, newResult.getId(), newResult.getScore(), originalResult.getId(), originalResult.getScore());
        resultRepository.deleteById(newResult.getId());
        List<Result> savedResults = resultRepository.findAll();
        assertThat(savedResults).size().isEqualTo(1);
        verifyStructureOfParticipantScoreInDatabase(isTeamTest, originalResult.getId(), originalResult.getScore(), originalResult.getId(), originalResult.getScore());
    }

    private void assertParticipantScoreStructure(ParticipantScore participantScore, Long expectedExerciseId, Long expectedParticipantId, Long expectedLastResultId,
            Long expectedLastScore, Long expectedLastRatedResultId, Long expectedLastRatedScore) {
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

        if (expectedLastRatedResultId == null) {
            assertThat(participantScore.getLastRatedResult()).isNull();
        }
        else {
            assertThat(participantScore.getLastRatedResult().getId()).isEqualTo(expectedLastRatedResultId);
        }
        assertThat(participantScore.getLastRatedScore()).isEqualTo(expectedLastRatedScore);
    }

    public Result createNewResult(boolean isTeamTest, boolean isRated) {
        StudentParticipation studentParticipation;
        SecurityUtils.setAuthorizationObject();
        if (isTeamTest) {
            studentParticipation = studentParticipationRepository.findByExerciseIdAndTeamId(idOfTeamTextExercise, idOfTeam1).get(0);
        }
        else {
            studentParticipation = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1).get(0);
        }
        SecurityContextHolder.getContext().setAuthentication(null);
        return createSubmissionAndResult(studentParticipation, 100, isRated);
    }

    private void createIndividualTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxScore(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);

        idOfIndividualTextExercise = textExercise.getId();
    }

    private void createTeamTextExerciseAndTeam(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise teamTextExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        teamTextExercise.setMaxScore(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        teamTextExercise = exerciseRepository.save(teamTextExercise);

        User student1 = userRepository.findOneByLogin("student1").get();
        User tutor1 = userRepository.findOneByLogin("tutor1").get();
        Team team = new Team();
        team.addStudents(student1);
        team.setOwner(tutor1);
        team.setShortName("team1");
        team.setName("team1");
        team = teamRepository.saveAndFlush(team);

        idOfTeam1 = team.getId();
        idOfTeamTextExercise = teamTextExercise.getId();
    }

    private Result createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepository.findById(idOfExercise).get();

        if (!exercise.getMaxScore().equals(pointsOfExercise)) {
            exercise.setMaxScore(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.saveAndFlush(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    private Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        return resultRepository.saveAndFlush(result);
    }

    public ParticipantScore setupTestScenarioWithOneResultSaved(boolean isRatedResult, boolean isTeam) {
        List<ParticipantScore> savedParticipantScores = participantScoreRepository.findAll();
        assertThat(savedParticipantScores).isEmpty();

        Long idOfExercise;
        Participant participant;
        if (isTeam) {
            participant = teamRepository.findById(idOfTeam1).get();
            idOfExercise = idOfTeamTextExercise;
        }
        else {
            participant = userRepository.findOneByLogin("student1").get();
            idOfExercise = idOfIndividualTextExercise;
        }

        Result persistedResult = createParticipationSubmissionAndResult(idOfExercise, participant, 10.0, 10.0, 200, isRatedResult);
        savedParticipantScores = participantScoreRepository.findAll();
        assertThat(savedParticipantScores).isNotEmpty();
        assertThat(savedParticipantScores).size().isEqualTo(1);
        ParticipantScore savedParticipantScore = savedParticipantScores.get(0);
        if (isRatedResult) {
            assertParticipantScoreStructure(savedParticipantScore, idOfExercise, participant.getId(), persistedResult.getId(), persistedResult.getScore(), persistedResult.getId(),
                    persistedResult.getScore());
        }
        else {
            assertParticipantScoreStructure(savedParticipantScore, idOfExercise, participant.getId(), persistedResult.getId(), persistedResult.getScore(), null, null);

        }
        verify(this.scoreService, times(1)).updateOrCreateParticipantScore(any());
        return savedParticipantScore;
    }

    private void verifyStructureOfParticipantScoreInDatabase(boolean isTeamTest, Long expectedLastResultId, Long expectedLastScore, Long expectedLastRatedResultId,
            Long expectedLastRatedScore) {
        Long idOfExercise;
        Participant participant;
        if (isTeamTest) {
            participant = teamRepository.findById(idOfTeam1).get();
            idOfExercise = idOfTeamTextExercise;
        }
        else {
            participant = userRepository.findOneByLogin("student1").get();
            idOfExercise = idOfIndividualTextExercise;
        }

        List<ParticipantScore> savedParticipantScore = participantScoreRepository.findAll();
        assertThat(savedParticipantScore).isNotEmpty();
        assertThat(savedParticipantScore).size().isEqualTo(1);
        ParticipantScore updatedParticipantScore = savedParticipantScore.get(0);
        assertParticipantScoreStructure(updatedParticipantScore, idOfExercise, participant.getId(), expectedLastResultId, expectedLastScore, expectedLastRatedResultId,
                expectedLastRatedScore);
        verify(this.scoreService, times(2)).updateOrCreateParticipantScore(any(Result.class));
    }

}
