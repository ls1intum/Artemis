package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TestConstants;

public class ProgrammingSubmissionIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    ProgrammingExercise exercise;

    @BeforeEach
    public void init() throws Exception {
        database.addUsers(3, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndSubmissions().get(0);
        database.addSolutionParticipationForProgrammingExercise(exercise);
        database.addTemplateParticipationForProgrammingExercise(exercise);
        database.addProgrammingParticipationWithResultForExercise(exercise, "student1");
        exercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(exercise);

        doNothing().when(continuousIntegrationService).triggerBuild(any());

        var newObjectId = new ObjectId(4, 5, 2, 5, 3);
        doReturn(newObjectId).when(gitService).getLastCommitHash(null);
        doReturn(newObjectId).when(gitService).getLastCommitHash(exercise.getTemplateParticipation().getRepositoryUrlAsUrl());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void triggerBuildStudent() throws Exception {
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
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
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
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
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        String login1 = "student1";
        String login2 = "student2";
        String login3 = "student3";
        database.addStudentParticipationForProgrammingExercise(exercise, login1);
        database.addStudentParticipationForProgrammingExercise(exercise, login2);
        database.addStudentParticipationForProgrammingExercise(exercise, login3);
        // Set test cases changed to true; after the build run it should be false);
        exercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(exercise);
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
            verify(continuousIntegrationService).triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation());
        }

        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.findWithTemplateParticipationAndSolutionParticipationById(exercise.getId()).get();
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

        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
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
            verify(continuousIntegrationService).triggerBuild((ProgrammingExerciseStudentParticipation) submission.getParticipation());
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

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void triggerFailedBuild_resultPresentInCI_ok() throws Exception {
        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setLanguage(Language.ENGLISH);
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        final var participation = programmingExerciseStudentParticipationRepository.findById(submission.getParticipation().getId()).get();
        bambooRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockQueryLatestBuildResultFromBambooServer(participation.getBuildPlanId());

        request.postWithoutLocation("/api" + Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + participation.getId() + "/trigger-failed-build", null, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAlllProgrammingSubmissions_asUser_forbidden() throws Exception {
        request.get("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllProgramminSubmissions_asInstructor_allSubmissionsReturned() throws Exception {
        final var submissions = new LinkedList<ProgrammingSubmission>();
        for (int i = 1; i < 4; i++) {
            final var submission = ModelFactory.generateProgrammingSubmission(true);
            submissions.add(submission);
            database.addProgrammingSubmission(exercise, submission, "student" + i);
        }

        final var responseSubmissions = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmissions).containsExactly(submissions.toArray(new ProgrammingSubmission[0]));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAllProgrammingSubmissions_assessedByTutor_allSubmissionsReturned() throws Exception {
        database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        var assessedSubmission = ModelFactory.generateProgrammingSubmission(true);
        assessedSubmission = database.addProgrammingSubmission(exercise, assessedSubmission, "student2");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(assessedSubmission, AssessmentType.MANUAL, tutor);

        final var paramMap = new LinkedMultiValueMap<String, String>();
        paramMap.add("assessedByTutor", "true");
        final var responseSubmissions = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class, paramMap);

        assertThat(responseSubmissions).containsExactly(assessedSubmission);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_asTutorWithOneAvailable_returnsSubmission() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        final var responseSubmission = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK, ProgrammingSubmission.class);

        assertThat(responseSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_dueDateNotPassedYet_notFound() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        final var submission = database.addProgrammingSubmission(exercise, ModelFactory.generateProgrammingSubmission(true), "student1");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null);

        request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getProgrammingSubmissionWithoutAssessment_alreadyAssessed_noFound() throws Exception {
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.saveAndFlush(exercise);
        var submission = ModelFactory.generateProgrammingSubmission(true);
        submission = database.addProgrammingSubmission(exercise, submission, "student1");
        final var tutor = database.getUserByLogin("tutor1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL, tutor);

        request.get("/api/exercises" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.NOT_FOUND, String.class);
    }
}
