package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo")
public class ProgrammingSubmissionIntegrationTest {

    @MockBean
    BambooService continuousIntegrationServiceMock;

    @MockBean
    GitService gitServiceMock;

    @MockBean
    GroupNotificationService groupNotificationService;

    @MockBean
    WebsocketMessagingService websocketMessagingService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    ProgrammingSubmissionRepository submissionRepository;

    ProgrammingExercise exercise;

    @BeforeEach
    public void init() throws Exception {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = exerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addParticipationWithResultForExercise(exercise, "student1");
        exercise.setTestCasesChanged(true);
        exerciseRepository.save(exercise);

        when(gitServiceMock.getLastCommitHash(null)).thenReturn(new ObjectId(4, 5, 2, 5, 3));
        when(gitServiceMock.getLastCommitHash(exercise.getTemplateParticipation().getRepositoryUrlAsUrl())).thenReturn(new ObjectId(4, 5, 2, 5, 3));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildStudent() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submission.getResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildInstructor() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);

        ProgrammingSubmission submission = submissions.get(0);
        assertThat(submission.getResult()).isNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildInstructor_tutorForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.FORBIDDEN,
                new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildInstructor_studentForbidden() throws Exception {
        String login = "student1";
        StudentParticipation participation = database.addStudentParticipationForProgrammingExercise(exercise, login);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build?submissionType=INSTRUCTOR", null, HttpStatus.FORBIDDEN,
                new HttpHeaders());
    }

    @Test
    @Timeout(5)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForExercise_Instructor() throws Exception {
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        database.addStudentParticipationForProgrammingExercise(exercise, login1);
        database.addStudentParticipationForProgrammingExercise(exercise, login2);
        database.addStudentParticipationForProgrammingExercise(exercise, login3);
        // Set test cases changed to true; after the build run it should be false);
        exercise.setTestCasesChanged(true);
        exerciseRepository.save(exercise);
        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all", null, HttpStatus.OK, new HttpHeaders());

        await().until(() -> submissionRepository.count() == 3);

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();

        List<ProgrammingExerciseParticipation> participations = new ArrayList<>();
        for (ProgrammingSubmission submission : submissions) {
            assertThat(submission.getResult()).isNull();
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
            assertThat(submission.getParticipation()).isNotNull();
            // There should be no participation assigned to two submissions.
            assertThat(participations.stream().noneMatch(p -> p.equals(submission.getParticipation()))).isTrue();
            participations.add((ProgrammingExerciseParticipation) submission.getParticipation());

            // Check that the CI build was triggered for the given submission.
            verify(continuousIntegrationServiceMock).triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation());
        }

        SecurityUtils.setAuthorizationObject();
        ProgrammingExercise updatedProgrammingExercise = exerciseRepository.findById(exercise.getId()).get();
        assertThat(updatedProgrammingExercise.getTestCasesChanged()).isFalse();
        verify(groupNotificationService, times(1)).notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, Constants.TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION);
        verify(websocketMessagingService, times(1)).sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases-changed", false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForExercise_tutorForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build-all", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForExercise_studentForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build-all", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForParticipations_instructor() throws Exception {
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        ProgrammingExerciseStudentParticipation participation1 = database.addStudentParticipationForProgrammingExercise(exercise, login1);
        ProgrammingExerciseStudentParticipation participation2 = database.addStudentParticipationForProgrammingExercise(exercise, login2);
        ProgrammingExerciseStudentParticipation participation3 = database.addStudentParticipationForProgrammingExercise(exercise, login3);

        // We only trigger two participations here: 1 and 3.
        List<Long> participationsToTrigger = new ArrayList<>(Arrays.asList(participation1.getId(), participation3.getId()));

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build", participationsToTrigger, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(2);

        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>();
        for (ProgrammingSubmission submission : submissions) {
            assertThat(submission.getResult()).isNull();
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
            assertThat(submission.getParticipation()).isNotNull();
            // There should be no submission for the participation that was not sent to the endpoint.
            assertThat(submission.getParticipation().getId()).isNotEqualTo(participation2.getId());
            // There should be no participation assigned to two submissions.
            assertThat(participations.stream().noneMatch(p -> p.equals(submission.getParticipation()))).isTrue();
            participations.add((ProgrammingExerciseStudentParticipation) submission.getParticipation());

            // Check that the CI build was triggered for the given submission.
            verify(continuousIntegrationServiceMock).triggerBuild((ProgrammingExerciseStudentParticipation) submission.getParticipation());
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void triggerBuildForParticipations_tutorForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForParticipations_studentForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", new ArrayList<>(), HttpStatus.FORBIDDEN, new HttpHeaders());
    }
}
