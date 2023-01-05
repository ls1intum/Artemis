package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;

class ExerciseScoresChartIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "exercisescoreschart";

    private Long idOfCourse;

    private Long textExerciseId;

    private Long textExerciseNoParticipantsId;

    private Long teamExerciseId;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @BeforeEach
    void setupTestScenario() {
        participantScoreSchedulerService.activate();
        ParticipantScoreSchedulerService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        this.database.addUsers(TEST_PREFIX, 2, 2, 0, 0);
        Course course = this.database.createCourse();
        idOfCourse = course.getId();
        TextExercise textExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        textExerciseId = textExercise.getId();
        TextExercise exerciseWithoutParticipants = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        textExerciseNoParticipantsId = exerciseWithoutParticipants.getId();

        Exercise teamExercise = database.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        teamExerciseId = teamExercise.getId();
        User student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        User tutor1 = database.getUserByLogin(TEST_PREFIX + "tutor1");
        Team team1 = database.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1");
        User student2 = database.getUserByLogin(TEST_PREFIX + "student2");
        User tutor2 = database.getUserByLogin(TEST_PREFIX + "tutor2");
        Team team2 = database.createTeam(Set.of(student2), tutor2, teamExercise, TEST_PREFIX + "team2");

        // Creating result for student1
        database.createParticipationSubmissionAndResult(textExercise.getId(), student1, 10.0, 10.0, 50, true);
        // Creating result for student2
        database.createParticipationSubmissionAndResult(textExercise.getId(), student2, 10.0, 10.0, 40, true);

        // Creating result for team1
        database.createParticipationSubmissionAndResult(teamExercise.getId(), team1, 10.0, 10.0, 50, true);
        // Creating result for team2
        database.createParticipationSubmissionAndResult(teamExercise.getId(), team2, 10.0, 10.0, 90, true);

        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 2);
        await().until(() -> participantScoreRepository.findAllByExercise(teamExercise).size() == 2);
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreSchedulerService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCourseExerciseScores_asStudent_shouldReturnCorrectIndividualAverageAndMaxScores() throws Exception {
        var scores = request.getList(getEndpointUrl(idOfCourse), HttpStatus.OK, ExerciseScoresDTO.class);
        assertThat(scores).hasSize(3);
        var textExercise = scores.stream().filter(score -> score.exerciseId.equals(textExerciseId)).findFirst().get();
        var teamExercise = scores.stream().filter(score -> score.exerciseId.equals(teamExerciseId)).findFirst().get();
        var textExerciseNoParticipants = scores.stream().filter(score -> score.exerciseId.equals(textExerciseNoParticipantsId)).findFirst().get();

        assertThat(textExercise.scoreOfStudent).isEqualTo(50.0);
        assertThat(textExercise.averageScoreAchieved).isEqualTo(45.0);
        assertThat(textExercise.maxScoreAchieved).isEqualTo(50.0);

        assertThat(teamExercise.scoreOfStudent).isEqualTo(50.0);
        assertThat(teamExercise.averageScoreAchieved).isEqualTo(70.0);
        assertThat(teamExercise.maxScoreAchieved).isEqualTo(90.0);

        assertThat(textExerciseNoParticipants.scoreOfStudent).isEqualTo(0.0);
        assertThat(textExerciseNoParticipants.averageScoreAchieved).isEqualTo(0.0);
        assertThat(textExerciseNoParticipants.maxScoreAchieved).isEqualTo(0.0);
    }

    private String getEndpointUrl(long courseId) {
        return String.format("/api/courses/%d/exercise-scores", courseId);
    }
}
