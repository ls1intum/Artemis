package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.ExercisePeerComparisonDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseScoresChartIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "exercisescoreschart";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationTestRepository;

    private Long idOfCourse;

    private Long idOfIndividualTextExercise;

    private Long idOfIndividualTextExerciseWithoutParticipants;

    private Long idOfTeamTextExercise;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private TeamScoreRepository teamScoreRepository;

    @BeforeEach
    void setupTestScenario() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 0);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        idOfCourse = course.getId();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExercise = textExercise.getId();
        TextExercise exerciseWithoutParticipants = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExerciseWithoutParticipants = exerciseWithoutParticipants.getId();

        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        User tutor1 = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Long idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();
        User student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User student3 = userTestRepository.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();
        User tutor2 = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();
        Long idOfTeam2 = teamUtilService.createTeam(Set.of(student2, student3), tutor2, teamExercise, TEST_PREFIX + "team2").getId();

        // Creating result for student1
        participationUtilService.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team1 = teamRepository.findById(idOfTeam1).orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(idOfTeamTextExercise, team1, 10.0, 10.0, 50, true);
        // Creating result for student2
        participationUtilService.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student2, 10.0, 10.0, 40, true);
        // Creating result for student3
        participationUtilService.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student3, 10.0, 10.0, 30, true);
        // Creating result for team2
        Team team2 = teamRepository.findById(idOfTeam2).orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(idOfTeamTextExercise, team2, 10.0, 10.0, 90, true);

        participantScoreScheduleService.executeScheduledTasks();
        await().until(participantScoreScheduleService::isIdle);
        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 3);
        await().until(() -> participantScoreRepository.findAllByExercise(teamExercise).size() == 2);
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseExerciseScores_asStudent_shouldReturnCorrectIndividualAverageAndMaxScores() throws Exception {
        List<ExerciseScoresDTO> exerciseScores = request.getList(getEndpointUrl(idOfCourse), HttpStatus.OK, ExerciseScoresDTO.class);
        assertThat(exerciseScores).hasSize(3);
        ExerciseScoresDTO individualTextExercise = exerciseScores.stream().filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId() == idOfIndividualTextExercise).findFirst()
                .orElseThrow();
        ExerciseScoresDTO teamTextExercise = exerciseScores.stream().filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId() == idOfTeamTextExercise).findFirst().orElseThrow();
        ExerciseScoresDTO individualTextExerciseWithoutParticipants = exerciseScores.stream()
                .filter(exerciseScoresDTO -> exerciseScoresDTO.exerciseId() == idOfIndividualTextExerciseWithoutParticipants).findFirst().orElseThrow();

        assertThat(individualTextExercise.scoreOfStudent()).isEqualTo(50.0);
        assertThat(individualTextExercise.averageScoreAchieved()).isEqualTo(40.0);
        assertThat(individualTextExercise.maxScoreAchieved()).isEqualTo(50);

        assertThat(teamTextExercise.scoreOfStudent()).isEqualTo(50.0);
        assertThat(teamTextExercise.averageScoreAchieved()).isEqualTo(70.0);
        assertThat(teamTextExercise.maxScoreAchieved()).isEqualTo(90.0);

        assertThat(individualTextExerciseWithoutParticipants.scoreOfStudent()).isZero();
        assertThat(individualTextExerciseWithoutParticipants.averageScoreAchieved()).isZero();
        assertThat(individualTextExerciseWithoutParticipants.maxScoreAchieved()).isZero();
    }

    private String getEndpointUrl(long courseId) {
        return "/api/exercise/courses/%d/charts/exercise-scores".formatted(courseId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void similarComparisonDoesNotSubstituteTheCourseAverageForASmallCohort() throws Exception {
        var response = request.get("/api/exercise/courses/" + idOfCourse + "/charts/peer-comparison", HttpStatus.OK, ExercisePeerComparisonDTO.class);
        assertThat(response.peerCount()).isZero();
        assertThat(response.exercises()).hasSize(3).allSatisfy(score -> assertThat(score.peerAverage()).isNull());
        assertThat(response.exercises().stream().filter(score -> score.exerciseId() == idOfIndividualTextExerciseWithoutParticipants).findFirst().orElseThrow().ownScore())
                .isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void comparisonWaitsUntilAllIndividualDeadlinesHavePassed() throws Exception {
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        ZonedDateTime past = ZonedDateTime.now().minusDays(5);
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, past, past, null);
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        studentParticipationTestRepository.saveAndFlush(participation);
        String endpoint = "/api/exercise/courses/" + course.getId() + "/charts/peer-comparison";
        var beforeDeadline = request.get(endpoint, HttpStatus.OK, ExercisePeerComparisonDTO.class);
        assertThat(beforeDeadline.exercises()).isNullOrEmpty();

        participation.setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        studentParticipationTestRepository.saveAndFlush(participation);
        var afterDeadline = request.get(endpoint, HttpStatus.OK, ExercisePeerComparisonDTO.class);
        assertThat(afterDeadline.exercises()).singleElement().satisfies(score -> {
            assertThat(score.exerciseId()).isEqualTo(exercise.getId());
            assertThat(score.ownScore()).isNull();
            assertThat(score.peerAverage()).isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "cohortstudent1", roles = "USER")
    void similarComparisonReturnsOnlyTheMatchingCohortAggregate() throws Exception {
        String prefix = TEST_PREFIX + "cohort";
        userUtilService.addUsers(prefix, 44, 0, 0, 0);
        Course course = courseUtilService.createEnrolledCourse(prefix);
        ZonedDateTime past = ZonedDateTime.now().minusDays(5);
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, past, past, past);
        for (int i = 1; i <= 44; i++) {
            User student = userTestRepository.findOneByLogin(prefix + "student" + i).orElseThrow();
            participationUtilService.createParticipationSubmissionAndResult(exercise.getId(), student, 10D, 10D, i == 1 ? 0 : 49 + i, true);
        }
        participantScoreScheduleService.executeScheduledTasks();
        await().until(participantScoreScheduleService::isIdle);
        var response = request.get("/api/exercise/courses/" + course.getId() + "/charts/peer-comparison", HttpStatus.OK, ExercisePeerComparisonDTO.class);
        assertThat(response.groupCount()).isEqualTo(4);
        assertThat(response.peerCount()).isEqualTo(10);
        assertThat(response.exercises()).singleElement().satisfies(score -> {
            assertThat(score.ownScore()).isEqualTo(0D);
            assertThat(score.peerAverage()).isEqualTo(55.5D);
            assertThat(score.peerCount()).isEqualTo(10);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void similarComparisonRejectsUnenrolledStudents() throws Exception {
        Course otherCourse = courseUtilService.createCourse();
        request.get("/api/exercise/courses/" + otherCourse.getId() + "/charts/peer-comparison", HttpStatus.FORBIDDEN, ExercisePeerComparisonDTO.class);
    }

    @Test
    void peerTeamAggregatesExcludeTheRequestersOwnTeamAndCountTeamsIndependently() {
        User requester = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        User sharedTeammate = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User otherPeer = userTestRepository.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();
        var teams = teamRepository.findAllByExerciseIdWithEagerStudents(idOfTeamTextExercise);
        Team ownTeam = teams.stream().filter(team -> team.getStudents().contains(requester)).findFirst().orElseThrow();
        Team otherTeam = teams.stream().filter(team -> !team.getId().equals(ownTeam.getId())).findFirst().orElseThrow();
        otherTeam.getStudents().remove(sharedTeammate);
        teamRepository.save(otherTeam);
        ownTeam.getStudents().add(sharedTeammate);
        teamRepository.save(ownTeam);
        var averages = teamScoreRepository.findPeerExerciseAverages(Set.of(idOfTeamTextExercise), Set.of(sharedTeammate.getId(), otherPeer.getId()), requester.getId());
        assertThat(averages).singleElement().satisfies(average -> {
            assertThat(average.average()).isEqualTo(90D);
            assertThat(average.participantCount()).isEqualTo(1);
        });
    }
}
