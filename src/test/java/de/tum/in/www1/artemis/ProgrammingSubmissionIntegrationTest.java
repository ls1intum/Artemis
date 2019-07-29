package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_PATH;
import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BAMBOO_REQUEST;
import static de.tum.in.www1.artemis.constants.ProgrammingSubmissionConstants.BITBUCKET_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.json.simple.parser.JSONParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.connectors.BitbucketService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.ResultResource;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo, bitbucket")
public class ProgrammingSubmissionIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ProgrammingSubmissionResource programmingSubmissionResource;

    @Autowired
    ResultResource resultResource;

    @Autowired
    ProgrammingSubmissionService programmingSubmissionService;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    BitbucketService versionControlService;

    ProgrammingExercise exercise;

    ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    ProgrammingSubmission submission;

    @Before
    public void reset() {
        database.resetDatabase();
        database.addUsers(2, 2, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        exercise = programmingExerciseRepository.findAll().get(0);
    }

    @Test
    @WithMockUser(username = "student1")
    public void shouldNotCreateSubmissionOnNotifyPushForInvalidParticipationId() throws Exception {
        long fakeParticipationId = 1L;
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);
        // Api should return not found.
        request.postWithoutLocation("/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH + fakeParticipationId, obj, HttpStatus.NOT_FOUND, new HttpHeaders());
        // No submission should be created for the fake participation.
        assertThat(submissionRepository.findAll()).hasSize(0);
    }

    @Test
    @WithMockUser(username = "student1")
    @Transactional(readOnly = true)
    public void shouldCreateSubmissionOnNotifyPushForStudentSubmission() throws Exception {
        postStudentSubmission();

        assertThat(submission.getParticipation().getId()).isEqualTo(programmingExerciseStudentParticipation.getId());
        ProgrammingExerciseStudentParticipation updatedParticipation = studentParticipationRepository.getOne(programmingExerciseStudentParticipation.getId());
        assertThat(updatedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(updatedParticipation.getSubmissions().stream().findFirst().get().getId()).isEqualTo(submission.getId());

        // Make sure the submission has the correct commit hash.
        assertThat(submission.getCommitHash()).isEqualTo("9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d");
        // The submission should be manual and submitted.
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();
    }

    @Test
    @Transactional(readOnly = true)
    public void shouldHandleNewBuildResultCreatedByStudentCommit() throws Exception {
        postStudentSubmission();
        postStudentCommit();

        // Check that the result was created successfully and is linked to the participation and submission.
        List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(programmingExerciseStudentParticipation.getId());
        assertThat(results).hasSize(1);
        Result createdResult = results.get(0);
        programmingExerciseStudentParticipation = studentParticipationRepository.getOne(programmingExerciseStudentParticipation.getId());
        submission = (ProgrammingSubmission) submissionRepository.getOne(submission.getId());
        assertThat(createdResult.getParticipation().getId()).isEqualTo(programmingExerciseStudentParticipation.getId());
        assertThat(submission.getResult().getId()).isEqualTo(createdResult.getId());
        assertThat(programmingExerciseStudentParticipation.getSubmissions()).hasSize(1);
        assertThat(programmingExerciseStudentParticipation.getSubmissions().stream().anyMatch(s -> s.getId().equals(submission.getId()))).isTrue();
    }

    @Test
    @Transactional(readOnly = true)
    public void whatWillHappen() throws Exception {
        postStudentSubmission();
        postStudentCommit();
        postStudentCommit();
    }

    private void postStudentSubmission() throws Exception {
        programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setBuildPlanId("TEST201904BPROGRAMMINGEXERCISE6-TESTUSER");
        programmingExerciseStudentParticipation.setInitializationState(InitializationState.INITIALIZED);
        programmingExerciseStudentParticipation.setExercise(exercise);
        programmingExerciseStudentParticipation = studentParticipationRepository.save(programmingExerciseStudentParticipation);

        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BITBUCKET_REQUEST);

        // Api should return ok.
        request.postWithoutLocation("/api" + PROGRAMMING_SUBMISSION_RESOURCE_PATH + programmingExerciseStudentParticipation.getId(), obj, HttpStatus.OK, new HttpHeaders());

        // Submission should have been created for the participation.
        assertThat(submissionRepository.findAll()).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        submission = (ProgrammingSubmission) submissionRepository.findAll().get(0);
    }

    private void postStudentCommit() throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(BAMBOO_REQUEST);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", "<secrettoken>");
        request.postWithoutLocation("/api" + NEW_RESULT_RESOURCE_PATH, obj, HttpStatus.OK, httpHeaders);
    }
}
