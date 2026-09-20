package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentPayloadDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamImportStrategyType;
import de.tum.cit.aet.artemis.exercise.dto.TeamInputDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamResponseDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TeamWebsocketServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "teamwebsocketservice";

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private Set<User> students;

    private String teamResourceUrl() {
        return "/api/exercise/exercises/" + modelingExercise.getId() + "/teams";
    }

    private String importFromExerciseUrl(Exercise sourceExercise) {
        return "/api/exercise/exercises/" + modelingExercise.getId() + "/teams/import-from-exercise?sourceExerciseId=" + sourceExercise.getId() + "&importStrategyType="
                + TeamImportStrategyType.PURGE_EXISTING;
    }

    private final String assignmentTopic = "/topic/team-assignments";

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        Course course = courseUtilService.addEnrolledCourseWithModelingAndTextExercise(TEST_PREFIX);
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
        students = userCourseRoleTestRepository.findByCourse_IdAndRole(course.getId(), CourseRole.STUDENT).stream().map(UserCourseRole::getUser).collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnTeamCreate() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        TeamResponseDTO createdTeam = request.postWithResponseBody(teamResourceUrl(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.CREATED);

        TeamAssignmentPayloadDTO expectedPayload = new TeamAssignmentPayloadDTO(modelingExercise.getId(), createdTeam.id(), List.of());
        createdTeam.students().forEach(student -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(student.login(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnRemoveStudentFromTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);

        User studentToRemoveFromTeam = students.iterator().next();
        Team updatedTeam = new Team(team).id(team.getId()).removeStudents(studentToRemoveFromTeam);
        request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), TeamInputDTO.of(updatedTeam), TeamResponseDTO.class, HttpStatus.OK);

        TeamAssignmentPayloadDTO expectedPayload = new TeamAssignmentPayloadDTO(modelingExercise.getId(), null, List.of());
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(studentToRemoveFromTeam.getLogin(), assignmentTopic, expectedPayload);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTeamAssignmentUpdateOnAddStudentToTeam() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);

        Team updatedTeam = new Team(team).id(team.getId()).students(students);
        TeamResponseDTO savedTeam = request.putWithResponseBody(teamResourceUrl() + "/" + updatedTeam.getId(), TeamInputDTO.of(updatedTeam), TeamResponseDTO.class, HttpStatus.OK);

        TeamAssignmentPayloadDTO expectedPayload = new TeamAssignmentPayloadDTO(modelingExercise.getId(), savedTeam.id(), List.of());
        savedTeam.students().forEach(student -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(student.login(), assignmentTopic, expectedPayload));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCurrentTeamMembersReceiveExistingParticipationWithVisibleResults(boolean resultsReleased) throws Exception {
        textExercise.setAssessmentDueDate(ZonedDateTime.parse(resultsReleased ? "2000-01-01T00:00:00Z" : "2999-01-01T00:00:00Z"));
        exerciseRepository.save(textExercise);
        Team team = new Team().name("Team").shortName("team").exercise(textExercise);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);
        // Keep team and student IDs distinct so confusing the two cannot accidentally match a member.
        List<User> members = students.stream().filter(student -> !student.getId().equals(team.getId())).toList();
        assertThat(members).hasSizeGreaterThanOrEqualTo(2);
        team.setStudents(Set.of(members.getFirst()));
        teamRepository.save(team);
        var submission = ParticipationFactory.generateTextSubmission("Team answer", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, submission, team.getId(), TEST_PREFIX + "tutor1");
        var participation = studentParticipationRepository.findOneByExerciseIdAndTeamId(textExercise.getId(), team.getId()).orElseThrow();

        // An unrelated team's participation must not be included in the assignment.
        Team otherTeam = teamRepository.save(new Team().name("Other team").shortName("otherteam").exercise(textExercise));
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, ParticipationFactory.generateTextSubmission("Other answer", Language.ENGLISH, true),
                otherTeam.getId(), TEST_PREFIX + "tutor1");
        User newMember = members.get(1);
        team.setStudents(Set.of(members.getFirst(), newMember));
        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/teams/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.OK);

        var captor = ArgumentCaptor.forClass(TeamAssignmentPayloadDTO.class);
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(newMember.getLogin()), eq(assignmentTopic), captor.capture());
        var payload = captor.getValue();
        verify(websocketMessagingService, timeout(2000)).sendMessageToUser(members.getFirst().getLogin(), assignmentTopic, payload);
        assertThat(payload.exerciseId()).isEqualTo(textExercise.getId());
        assertThat(payload.teamId()).isEqualTo(team.getId());
        assertThat(payload.studentParticipations()).singleElement().satisfies(sentParticipation -> {
            assertThat(sentParticipation.id()).isEqualTo(participation.getId());
            assertThat(sentParticipation.team().id()).isEqualTo(team.getId());
            assertThat(sentParticipation.team().students()).extracting(student -> student.id()).containsExactlyInAnyOrder(members.getFirst().getId(), newMember.getId());
            assertThat(sentParticipation.submissions()).singleElement().satisfies(sentSubmission -> {
                assertThat(sentSubmission.id()).isEqualTo(submission.getId());
                assertThat(sentSubmission.submitted()).isTrue();
                if (resultsReleased) {
                    assertThat(sentSubmission.results()).singleElement().satisfies(result -> assertThat(result.score()).isEqualTo(100D));
                }
                else {
                    assertThat(sentSubmission.results()).isEmpty();
                }
            });
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRenamedTeamNotifiesExistingMembers() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise).students(students);
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        teamRepository.save(team);
        var participation = new StudentParticipation();
        participation.setExercise(modelingExercise);
        participation.setParticipant(team);
        studentParticipationRepository.save(participation);

        team.setName("Renamed team");
        request.putWithResponseBody(teamResourceUrl() + "/" + team.getId(), TeamInputDTO.of(team), TeamResponseDTO.class, HttpStatus.OK);

        for (User student : students) {
            var captor = ArgumentCaptor.forClass(TeamAssignmentPayloadDTO.class);
            verify(websocketMessagingService, timeout(2000)).sendMessageToUser(eq(student.getLogin()), eq(assignmentTopic), captor.capture());
            assertThat(captor.getValue().studentParticipations()).singleElement().satisfies(sentParticipation -> {
                assertThat(sentParticipation.id()).isEqualTo(participation.getId());
                assertThat(sentParticipation.team().name()).isEqualTo("Renamed team");
            });
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamDelete() throws Exception {
        Team team = new Team().name("Team").shortName("team").exercise(modelingExercise);
        teamRepository.save(team);

        request.delete(teamResourceUrl() + "/" + team.getId(), HttpStatus.OK);

        TeamAssignmentPayloadDTO expectedPayload = new TeamAssignmentPayloadDTO(modelingExercise.getId(), null, List.of());
        team.getStudents().forEach(user -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(user.getLogin(), assignmentTopic, expectedPayload));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSendTeamAssignmentUpdateOnTeamImport() throws Exception {
        teamUtilService.addTeamsForExercise(textExercise, 2, null); // create teams in source exercise
        List<TeamResponseDTO> destinationTeams = request.putWithResponseBodyList(importFromExerciseUrl(textExercise), null, TeamResponseDTO.class, HttpStatus.OK);

        destinationTeams.forEach(team -> {
            TeamAssignmentPayloadDTO expectedPayload = new TeamAssignmentPayloadDTO(modelingExercise.getId(), team.id(), List.of());
            team.students().forEach(student -> verify(websocketMessagingService, timeout(2000)).sendMessageToUser(student.login(), assignmentTopic, expectedPayload));
        });
    }
}
