package de.tum.in.www1.artemis.service;

import static org.mockito.Mockito.verify;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.TeamAssignmentPayload;
import de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService;

class TeamWebsocketServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    StudentParticipationRepository participationRepo;

    @Autowired
    ParticipationTeamWebsocketService participationTeamWebsocketService;

    private ModelingExercise exercise;

    private TextExercise otherExercise;

    private Set<User> students;

    private String teamResourceUrl() {
        return "/api/exercises/" + exercise.getId() + "/teams";
    }

    private String importFromExerciseUrl(Exercise sourceExercise) {
        return "/api/exercises/" + exercise.getId() + "/teams/import-from-exercise/" + sourceExercise.getId() + "?importStrategyType=" + TeamImportStrategyType.PURGE_EXISTING;
    }

    private final String assignmentTopic = "/topic/team-assignments";

    @BeforeEach
    void init() {
        database.addUsers(3, 1, 1);
        database.addCourseWithModelingAndTextExercise();
        exercise = (ModelingExercise) exerciseRepo.save(exerciseRepo.findAll().get(0).mode(ExerciseMode.TEAM));
        otherExercise = (TextExercise) exerciseRepo.save(exerciseRepo.findAll().get(1).mode(ExerciseMode.TEAM));
        students = new HashSet<>(userRepo.findAllInGroup("tumuser"));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnTeamCreate() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(exercise).students(students);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(exercise, team);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnRemoveStudentFromTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(exercise).students(students);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        User studentToRemoveFromTeam = students.iterator().next();
        Team updatedTeam = new Team(team).id(team.getId()).removeStudents(studentToRemoveFromTeam);
        request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(exercise, null);
        verify(messagingTemplate).convertAndSendToUser(studentToRemoveFromTeam.getLogin(), assignmentTopic, expectedPayload);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnAddStudentToTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(exercise);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        Team updatedTeam = new Team(team).id(team.getId()).students(students);
        updatedTeam = request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), updatedTeam, Team.class, HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(exercise, updatedTeam);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamDelete() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(exercise);
        team = request.postWithResponseBody(teamResourceUrl(), team, Team.class, HttpStatus.CREATED);

        request.delete(teamResourceUrl() + "/" + team.getId(), HttpStatus.OK);

        TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(exercise, null);
        team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamImport() throws Exception {
        database.addTeamsForExercise(otherExercise, 2, null); // create teams in source exercise
        List<Team> destinationTeams = request.putWithResponseBodyList(importFromExerciseUrl(otherExercise), null, Team.class, HttpStatus.OK);

        destinationTeams.forEach(team -> {
            TeamAssignmentPayload expectedPayload = new TeamAssignmentPayload(exercise, team);
            team.getStudents().forEach(user -> verify(messagingTemplate).convertAndSendToUser(user.getLogin(), assignmentTopic, expectedPayload));
        });
    }
}
