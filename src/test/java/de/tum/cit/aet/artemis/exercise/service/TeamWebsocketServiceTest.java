package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentPayload;
import de.tum.cit.aet.artemis.exercise.dto.TeamImportStrategyType;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class TeamWebsocketServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "teamwebsocketservice";

    @Autowired
    private UserTestRepository userRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private Set<User> students;

    private String teamResourceUrl() {
        return "/api/exercise/exercises/" + modelingExercise.getId() + "/teams";
    }

    private String importFromExerciseUrl(Exercise sourceExercise) {
        return "/api/exercise/exercises/" + modelingExercise.getId() + "/teams/import-from-exercise/" + sourceExercise.getId() + "?importStrategyType="
                + TeamImportStrategyType.PURGE_EXISTING;
    }

    private final String assignmentTopic = "/topic/team-assignments";

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                exercise.setMode(ExerciseMode.TEAM);
                modelingExercise = (ModelingExercise) exerciseRepository.save(exercise);
            }
            if (exercise instanceof TextExercise) {
                exercise.setMode(ExerciseMode.TEAM);
                textExercise = (TextExercise) exerciseRepository.save(exercise);
            }
        }
        assertThat(modelingExercise).isNotNull();
        assertThat(textExercise).isNotNull();
        students = new HashSet<>(userRepo.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains("tumuser"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnTeamCreate() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, team, List.of());
        team.getStudents().forEach(user -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnRemoveStudentFromTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);

        User studentToRemoveFromTeam = students.iterator().next();
        Team updatedTeam = new Team(team).id(team.getId()).removeStudents(studentToRemoveFromTeam);
        request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, null, List.of());
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(studentToRemoveFromTeam.getLogin(), assignmentTopic, expectedPayload);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnAddStudentToTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);

        Team updatedTeam = new Team(team).id(team.getId()).students(students);
        updatedTeam = request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, updatedTeam, List.of());
        team.getStudents().forEach(user -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamDelete() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        teamRepository.save(team);

        request.delete(teamResourceUrl() + "/" + team.getId(), HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, null, List.of());
        team.getStudents().forEach(user -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamImport() throws Exception {
        teamUtilService.addTeamsForExercise(textExercise, 2, null); // create teams in source exercise
        List<Team> destinationTeams = request.putWithResponseBodyList(importFromExerciseUrl(textExercise), null, Team.class, HttpStatus.OK);

        destinationTeams.forEach(team -> {
            TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, team, List.of());
            team.getStudents().forEach(user -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(user.getLogin(), assignmentTopic, expectedPayload));
        });
    }
}
