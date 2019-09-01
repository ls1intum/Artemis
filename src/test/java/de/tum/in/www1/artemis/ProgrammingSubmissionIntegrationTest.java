package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.StudentParticipation;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.BambooService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.BuildTriggerDTO;

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

        when(gitServiceMock.getLastCommitHash(null)).thenReturn(new ObjectId(4, 5, 2, 5, 3));
        exercise = exerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
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
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-instructor-build", null, HttpStatus.OK, new HttpHeaders());

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
        request.postWithoutLocation("/api/programming-submissions/" + 1L + "/trigger-instructor-build", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildInstructor_studentForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-submissions/" + 1L + "/trigger-instructor-build", null, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void triggerBuildForExercise_Instructor() throws Exception {
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        database.addStudentParticipationForProgrammingExercise(exercise, login1);
        database.addStudentParticipationForProgrammingExercise(exercise, login2);
        database.addStudentParticipationForProgrammingExercise(exercise, login3);
        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build-all", null, HttpStatus.OK, new HttpHeaders());

        List<ProgrammingSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(3);

        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>();
        for (ProgrammingSubmission submission : submissions) {
            assertThat(submission.getResult()).isNull();
            assertThat(submission.isSubmitted()).isTrue();
            assertThat(submission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
            assertThat(submission.getParticipation()).isNotNull();
            // There should be no participation assigned to two submissions.
            assertThat(participations.stream().noneMatch(p -> p.equals(submission.getParticipation()))).isTrue();
            participations.add((ProgrammingExerciseStudentParticipation) submission.getParticipation());

            // Check that the CI build was triggered for the given submission.
            verify(continuousIntegrationServiceMock).triggerBuild((ProgrammingExerciseStudentParticipation) submission.getParticipation());
        }
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
        BuildTriggerDTO buildTriggerDTO = new BuildTriggerDTO();
        buildTriggerDTO.setParticipationIds(participationsToTrigger);

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/trigger-instructor-build", buildTriggerDTO, HttpStatus.OK, new HttpHeaders());

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
        BuildTriggerDTO buildTriggerDTO = new BuildTriggerDTO();
        buildTriggerDTO.setParticipationIds(new ArrayList<>());
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", buildTriggerDTO, HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildForParticipations_studentForbidden() throws Exception {
        BuildTriggerDTO buildTriggerDTO = new BuildTriggerDTO();
        buildTriggerDTO.setParticipationIds(new ArrayList<>());
        request.postWithoutLocation("/api/programming-exercises/" + 1L + "/trigger-instructor-build", buildTriggerDTO, HttpStatus.FORBIDDEN, new HttpHeaders());
    }
}
