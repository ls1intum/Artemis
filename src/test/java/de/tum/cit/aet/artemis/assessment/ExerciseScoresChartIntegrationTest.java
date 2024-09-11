package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Team;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.team.TeamUtilService;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseScoresDTO;

class ExerciseScoresChartIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisescoreschart";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Long idOfCourse;

    private Long idOfIndividualTextExercise;

    private Long idOfIndividualTextExerciseWithoutParticipants;

    private Long idOfTeamTextExercise;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @BeforeEach
    void setupTestScenario() {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        participantScoreScheduleService.activate();
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 0);
        Course course = courseUtilService.createCourse();
        idOfCourse = course.getId();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExercise = textExercise.getId();
        TextExercise exerciseWithoutParticipants = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfIndividualTextExerciseWithoutParticipants = exerciseWithoutParticipants.getId();

        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Long idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();
        User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();
        User tutor2 = userRepository.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();
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
        return String.format("/api/courses/%d/charts/exercise-scores", courseId);
    }
}
