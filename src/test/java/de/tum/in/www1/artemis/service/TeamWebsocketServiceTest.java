package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.websocket.dto.TeamAssignmentPayload;

class TeamWebsocketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private Set<User> students;

    private String teamResourceUrl() {
        return "/api/exercises/" + modelingExercise.getId() + "/teams";
    }

    private String importFromExerciseUrl(Exercise sourceExercise) {
        return "/api/exercises/" + modelingExercise.getId() + "/teams/import-from-exercise/" + sourceExercise.getId() + "?importStrategyType="
                + TeamImportStrategyType.PURGE_EXISTING;
    }

    private final String assignmentTopic = "/topic/team-assignments";

    @BeforeEach
    void init() {
        database.addUsers(3, 1, 0, 1);
        Course course = database.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                exercise.setMode(ExerciseMode.TEAM);
                modelingExercise = (ModelingExercise) exerciseRepo.save(exercise);
            }
            if (exercise instanceof TextExercise) {
                exercise.setMode(ExerciseMode.TEAM);
                textExercise = (TextExercise) exerciseRepo.save(exercise);
            }
        }
        assertThat(modelingExercise).isNotNull();
        assertThat(textExercise).isNotNull();
        students = new HashSet<>(userRepo.findAllInGroupWithAuthorities("tumuser"));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnTeamCreate() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, team);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnRemoveStudentFromTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        User studentToRemoveFromTeam = students.iterator().next();
        Team updatedTeam = new Team(team).id(team.getId()).removeStudents(studentToRemoveFromTeam);
        request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, null);
        verify(messagingTemplate).convertAndSendToUser(studentToRemoveFromTeam.getLogin(), assignmentTopic, expectedPayload);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnAddStudentToTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        Team updatedTeam = new Team(team).id(team.getId()).students(students);
        updatedTeam = request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, updatedTeam);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamDelete() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        request.delete(teamResourceUrl() + "/" + team.getId(), HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, null);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamImport() throws Exception {
        database.addTeamsForExercise(textExercise, 2, null); // create teams in source exercise
        List<Team> destinationTeams = request.putWithResponseBodyList(importFromExerciseUrl(textExercise), null, Team.class, HttpStatus.OK);

        destinationTeams.forEach(team -> {
            TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(modelingExercise, team);
            team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
        });
    }
}
